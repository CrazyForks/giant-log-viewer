package com.sunnychung.application.multiplatform.giantlogviewer

import com.sunnychung.application.multiplatform.giantlogviewer.io.CoroutineGiantFileTextPager
import com.sunnychung.application.multiplatform.giantlogviewer.io.GiantFileReader
import com.sunnychung.application.multiplatform.giantlogviewer.io.TextEncoding
import com.sunnychung.application.multiplatform.giantlogviewer.io.Viewport
import com.sunnychung.application.multiplatform.giantlogviewer.layout.MonospaceBidirectionalTextLayouter
import com.sunnychung.application.multiplatform.giantlogviewer.util.FixedWidthCharMeasurer
import java.nio.charset.StandardCharsets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GiantFileTextPagerEncodingTest {

    @Test
    fun utf8BomInitialViewportSkipsBom() {
        val content = "Alpha\nBeta"
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
            content.toByteArray(StandardCharsets.UTF_8)

        createTestFile(bytes) { file ->
            GiantFileReader(file.absolutePath, 16).use { fileReader ->
                val pager = CoroutineGiantFileTextPager(
                    fileReader,
                    MonospaceBidirectionalTextLayouter(FixedWidthCharMeasurer(16f)),
                )
                pager.viewport = Viewport(width = 16 * 20, height = 12 * 4, density = 1f)

                assertEquals(3L, pager.viewportStartBytePosition)
                assertEquals(listOf("Alpha", "Beta"), pager.textInViewport.take(2).map { it.toString() })
            }
        }
    }

    @Test
    fun utf16LittleEndianBomNavigationSkipsBomAndKeepsPhysicalBytePositions() {
        val content = "A😄B\nC😇D\n"
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) +
            content.toByteArray(StandardCharsets.UTF_16LE)

        createTestFile(bytes) { file ->
            GiantFileReader(file.absolutePath, 16).use { fileReader ->
                val pager = CoroutineGiantFileTextPager(
                    fileReader,
                    MonospaceBidirectionalTextLayouter(FixedWidthCharMeasurer(16f)),
                )
                pager.viewport = Viewport(width = 16 * 20, height = 12 * 4, density = 1f)

                assertEquals(2L, pager.viewportStartBytePosition)
                assertEquals(listOf("A😄B", "C😇D"), pager.textInViewport.take(2).map { it.toString() })

                pager.moveToNextRow()
                assertEquals(2L + "A😄B\n".toByteArray(StandardCharsets.UTF_16LE).size, pager.viewportStartBytePosition)
                assertEquals("C😇D", pager.textInViewport.first().toString())
            }
        }
    }

    @Test
    fun nextPageThenBeginningWorksForEverySupportedEncodingScenario() {
        val lines = listOf(
            "L00😄",
            "L01你好",
            "L02plain",
            "L03😇",
            "L04wrap",
            "L05끝",
            "L06🌭",
            "L07done",
        )
        val content = lines.joinToString("\n")

        supportedEncodingScenarios(content).forEach { scenario ->
            createTestFile(scenario.bytes) { file ->
                GiantFileReader(file.absolutePath, 32, textEncoding = scenario.textEncoding).use { fileReader ->
                    val pager = CoroutineGiantFileTextPager(
                        fileReader,
                        MonospaceBidirectionalTextLayouter(FixedWidthCharMeasurer(16f)),
                    )
                    pager.viewport = Viewport(width = 16 * 30, height = 12 * 3 + 1, density = 1f)

                    assertEquals(scenario.contentStartBytePosition, pager.viewportStartBytePosition, scenario.name)
                    assertEquals(lines.take(4), pager.textInViewport.take(4).map { it.toString() }, scenario.name)

                    pager.moveToNextPage()
                    assertTrue(
                        pager.viewportStartBytePosition > scenario.contentStartBytePosition,
                        "${scenario.name}: next page should move past the first displayable byte",
                    )
                    assertTrue(
                        pager.textInViewport.first().toString() in lines.drop(1),
                        "${scenario.name}: next page should start on a later row",
                    )

                    pager.moveToTheFirstRow()
                    assertEquals(scenario.contentStartBytePosition, pager.viewportStartBytePosition, scenario.name)
                    assertEquals(lines.take(4), pager.textInViewport.take(4).map { it.toString() }, scenario.name)
                }
            }
        }
    }

    @Test
    fun utf16LittleEndianWithoutBomUsesExplicitEncodingFromByteZero() {
        val content = "A😄B\nC😇D\n"
        val bytes = content.toByteArray(StandardCharsets.UTF_16LE)

        createTestFile(bytes) { file ->
            GiantFileReader(file.absolutePath, 16, textEncoding = TextEncoding.Utf16LE).use { fileReader ->
                val pager = CoroutineGiantFileTextPager(
                    fileReader,
                    MonospaceBidirectionalTextLayouter(FixedWidthCharMeasurer(16f)),
                )
                pager.viewport = Viewport(width = 16 * 20, height = 12 * 4, density = 1f)

                assertEquals(0L, pager.viewportStartBytePosition)
                assertEquals(listOf("A😄B", "C😇D"), pager.textInViewport.take(2).map { it.toString() })

                pager.moveToNextRow()
                assertEquals("A😄B\n".toByteArray(StandardCharsets.UTF_16LE).size.toLong(), pager.viewportStartBytePosition)
                assertEquals("C😇D", pager.textInViewport.first().toString())
            }
        }
    }

    @Test
    fun utf16BigEndianWithoutBomSearchUsesExplicitEncodingFromByteZero() {
        val prefix = "x".repeat(29)
        val content = prefix + "😇D" + "y".repeat(40)
        val bytes = content.toByteArray(StandardCharsets.UTF_16BE)

        createTestFile(bytes) { file ->
            GiantFileReader(file.absolutePath, 64, textEncoding = TextEncoding.Utf16BE).use { fileReader ->
                val pager = CoroutineGiantFileTextPager(
                    fileReader,
                    MonospaceBidirectionalTextLayouter(FixedWidthCharMeasurer(16f)),
                )
                pager.viewport = Viewport(width = 16 * 20, height = 12 * 4, density = 1f)

                val match = pager.searchAtAndForward(0L, Regex("😇D"))
                val expectedStart = prefix.toByteArray(StandardCharsets.UTF_16BE).size.toLong()
                val expectedEnd = expectedStart + "😇D".toByteArray(StandardCharsets.UTF_16BE).size
                assertEquals(expectedStart..<expectedEnd, match)
            }
        }
    }

    @Test
    fun utf16BigEndianSearchReturnsSurrogatePairByteRange() {
        val prefix = "x".repeat(29)
        val content = prefix + "😇D" + "y".repeat(40)
        val bytes = byteArrayOf(0xFE.toByte(), 0xFF.toByte()) +
            content.toByteArray(StandardCharsets.UTF_16BE)

        createTestFile(bytes) { file ->
            GiantFileReader(file.absolutePath, 64).use { fileReader ->
                val pager = CoroutineGiantFileTextPager(
                    fileReader,
                    MonospaceBidirectionalTextLayouter(FixedWidthCharMeasurer(16f)),
                )
                pager.viewport = Viewport(width = 16 * 20, height = 12 * 4, density = 1f)

                val match = pager.searchAtAndForward(0L, Regex("😇D"))
                val expectedStart = 2L + prefix.toByteArray(StandardCharsets.UTF_16BE).size
                val expectedEnd = expectedStart + "😇D".toByteArray(StandardCharsets.UTF_16BE).size
                assertEquals(expectedStart..<expectedEnd, match)
            }
        }
    }

    @Test
    fun millionLineBreaksInitialViewportReadsOnlyVisibleRows() {
        val numOfLineBreaks = 1_000_000
        val content = "\n".repeat(numOfLineBreaks) + "tail"

        createTestFile(content) { file ->
            GiantFileReader(file.absolutePath, 32).use { fileReader ->
                val pager = CoroutineGiantFileTextPager(
                    fileReader,
                    MonospaceBidirectionalTextLayouter(FixedWidthCharMeasurer(16f)),
                )
                pager.viewport = Viewport(width = 16 * 20, height = 12 * 6, density = 1f)

                assertEquals(0L, pager.viewportStartBytePosition)
                assertEquals(List(6) { "" }, pager.textInViewport.take(6).map { it.toString() })

                repeat(20) {
                    pager.moveToNextRow()
                }
                assertEquals(20L, pager.viewportStartBytePosition)
                assertEquals(List(6) { "" }, pager.textInViewport.take(6).map { it.toString() })
            }
        }
    }

    @Test
    fun utf16LittleEndianEmojiAtSoftWrapBoundaryKeepsRowByteStarts() {
        val firstRow = "x".repeat(19) + "😄"
        val content = firstRow + "Z"
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) +
            content.toByteArray(StandardCharsets.UTF_16LE)

        createTestFile(bytes) { file ->
            GiantFileReader(file.absolutePath, 48).use { fileReader ->
                val pager = CoroutineGiantFileTextPager(
                    fileReader,
                    MonospaceBidirectionalTextLayouter(FixedWidthCharMeasurer(16f)),
                )
                pager.viewport = Viewport(width = 16 * 20, height = 12 * 4, density = 1f)

                assertEquals(firstRow, pager.textInViewport[0].toString())
                assertEquals("Z", pager.textInViewport[1].toString())
                assertEquals(
                    2L + firstRow.toByteArray(StandardCharsets.UTF_16LE).size,
                    pager.startBytePositionsInViewport[1],
                )

                pager.moveToNextRow()
                assertEquals(2L + firstRow.toByteArray(StandardCharsets.UTF_16LE).size, pager.viewportStartBytePosition)
                assertEquals("Z", pager.textInViewport.first().toString())
            }
        }
    }

    @Test
    fun crlfBeforeCurrentRowDoesNotRenderCarriageReturnAsPreviousRowUtf8() {
        val content = "abcdefghij\r\nnext"

        createTestFile(content) { file ->
            GiantFileReader(file.absolutePath, 16).use { fileReader ->
                val pager = CoroutineGiantFileTextPager(
                    fileReader,
                    MonospaceBidirectionalTextLayouter(FixedWidthCharMeasurer(16f)),
                )
                pager.viewport = Viewport(width = 16 * 10, height = 12 * 3, density = 1f)

                pager.moveToTheLastRow()
                assertEquals("next", pager.textInViewport.first().toString())

                pager.moveToPrevRow()
                assertEquals(0L, pager.viewportStartBytePosition)
                assertEquals("abcdefghij", pager.textInViewport.first().toString())
            }
        }
    }

    @Test
    fun crlfBeforeCurrentRowDoesNotRenderCarriageReturnAsPreviousRowUtf16LittleEndian() {
        val content = "abcdefghij\r\nnext"
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) +
            content.toByteArray(StandardCharsets.UTF_16LE)

        createTestFile(bytes) { file ->
            GiantFileReader(file.absolutePath, 32).use { fileReader ->
                val pager = CoroutineGiantFileTextPager(
                    fileReader,
                    MonospaceBidirectionalTextLayouter(FixedWidthCharMeasurer(16f)),
                )
                pager.viewport = Viewport(width = 16 * 10, height = 12 * 3, density = 1f)

                pager.moveToTheLastRow()
                assertEquals("next", pager.textInViewport.first().toString())

                pager.moveToPrevRow()
                assertEquals(2L, pager.viewportStartBytePosition)
                assertEquals("abcdefghij", pager.textInViewport.first().toString())
            }
        }
    }

    private fun supportedEncodingScenarios(content: String): List<EncodingScenario> {
        return listOf(
            EncodingScenario(
                name = "UTF-8 without BOM",
                bytes = content.toByteArray(StandardCharsets.UTF_8),
                textEncoding = TextEncoding.Auto,
                contentStartBytePosition = 0L,
            ),
            EncodingScenario(
                name = "UTF-8 with BOM",
                bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
                    content.toByteArray(StandardCharsets.UTF_8),
                textEncoding = TextEncoding.Auto,
                contentStartBytePosition = 3L,
            ),
            EncodingScenario(
                name = "UTF-16LE with BOM",
                bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) +
                    content.toByteArray(StandardCharsets.UTF_16LE),
                textEncoding = TextEncoding.Auto,
                contentStartBytePosition = 2L,
            ),
            EncodingScenario(
                name = "UTF-16LE without BOM",
                bytes = content.toByteArray(StandardCharsets.UTF_16LE),
                textEncoding = TextEncoding.Utf16LE,
                contentStartBytePosition = 0L,
            ),
            EncodingScenario(
                name = "UTF-16BE with BOM",
                bytes = byteArrayOf(0xFE.toByte(), 0xFF.toByte()) +
                    content.toByteArray(StandardCharsets.UTF_16BE),
                textEncoding = TextEncoding.Auto,
                contentStartBytePosition = 2L,
            ),
            EncodingScenario(
                name = "UTF-16BE without BOM",
                bytes = content.toByteArray(StandardCharsets.UTF_16BE),
                textEncoding = TextEncoding.Utf16BE,
                contentStartBytePosition = 0L,
            ),
        )
    }

    private data class EncodingScenario(
        val name: String,
        val bytes: ByteArray,
        val textEncoding: TextEncoding,
        val contentStartBytePosition: Long,
    )
}
