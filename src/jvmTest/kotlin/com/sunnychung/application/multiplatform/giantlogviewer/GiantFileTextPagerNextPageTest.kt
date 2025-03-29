package com.sunnychung.application.multiplatform.giantlogviewer

import com.sunnychung.application.multiplatform.giantlogviewer.io.GiantFileReader
import com.sunnychung.application.multiplatform.giantlogviewer.io.GiantFileTextPager
import com.sunnychung.application.multiplatform.giantlogviewer.io.Viewport
import com.sunnychung.application.multiplatform.giantlogviewer.io.lineSeparatorRegex
import com.sunnychung.application.multiplatform.giantlogviewer.layout.MonospaceBidirectionalTextLayouter
import com.sunnychung.application.multiplatform.giantlogviewer.util.DivisibleWidthCharMeasurer
import com.sunnychung.application.multiplatform.giantlogviewer.util.FixedWidthCharMeasurer
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class GiantFileTextPagerNextPageTest {

    fun testPaging(fileContent: String, blockSize: Int = 1 * 1024 * 1024) {
        createTestFile(fileContent) { file ->
            val fileReader = GiantFileReader(file.absolutePath, blockSize)
            val pager = GiantFileTextPager(fileReader, MonospaceBidirectionalTextLayouter(DivisibleWidthCharMeasurer(16f)))
            pager.viewport = Viewport(width = 16 * 23, height = 12 * 12 + 1, density = 1f)
            var loop = 0
            with(PageState(fileContent = fileContent)) {
                while (loop < fileLength && start < fileLength) {
                    println("pos: ${pager.viewportStartCharPosition}")
                    assertEquals(start.toLong(), pager.viewportStartCharPosition, "loop $loop")
                    assertEquals(
                        fileContent.substring(0 ..< start).toByteArray(Charsets.UTF_8).size.toLong(),
                        pager.viewportStartBytePosition,
                        "loop $loop"
                    )
                    updatePageState(this)

                    assertListOfStringStartWith(
                        fileContent.substring(start ..< visibleEnd).split(lineSeparatorRegex)
                            .flatMap { it.chunkedUnicode(23) },
                        pager.textInViewport.value,
                        "range: [$start, $visibleEnd)"
                    )

                    pager.moveToNextPage()
                    start = pageEnd
                    ++loop
                }
                if (loop > fileLength) {
                    throw StackOverflowError("Infinite loop detected")
                }
            }
//            assertEquals(fileLength.toLong(), pager.viewportStartCharPosition)
        }
    }

    @Test
    fun multipleLines() {
        val fileLength = 10000
        val random = Random(24680)
        val fileContent = (0 ..< fileLength).joinToString("") {
            val newLineFactor = if (random.nextInt(13) == 0) {
                8
            } else {
                131
            }
            if (random.nextInt(newLineFactor) == 0) {
                return@joinToString "\n"
            }

            if (it % 10 == 0) {
                ('0'.code + ((it / 10) % 10)).toChar().toString()
            } else {
                ('a'.code + (it % 10)).toChar().toString()
            }
        }
//        println(fileContent)
        testPaging(fileContent)
    }

    @Test
    fun singlePage() {
        val fileLength = 60
        val random = Random(24680)
        val fileContent = (0 ..< fileLength).joinToString("") {
            val newLineFactor = if (random.nextInt(3) != 0) {
                8
            } else {
                131
            }
            if (random.nextInt(newLineFactor) == 0) {
                return@joinToString "\n"
            }

            if (it % 10 == 0) {
                ('0'.code + ((it / 10) % 10)).toChar().toString()
            } else {
                ('a'.code + (it % 10)).toChar().toString()
            }
        }
//        println(fileContent)
        testPaging(fileContent)
    }

    @Test
    fun emptyFile() {
        val fileLength = 0
        val random = Random(24680)
        val fileContent = ""
//        println(fileContent)
        createTestFile(fileContent) { file ->
            val fileReader = GiantFileReader(file.absolutePath)
            val pager = GiantFileTextPager(fileReader, MonospaceBidirectionalTextLayouter(FixedWidthCharMeasurer(16f)))
            pager.viewport = Viewport(width = 16 * 23, height = 12 * 12 + 1, density = 1f)
            (0 .. 1).forEach { loop ->
                println("pos: ${pager.viewportStartCharPosition}")
                assertEquals(0, pager.viewportStartCharPosition, "loop $loop")
                assertEquals(0, pager.viewportStartBytePosition, "loop $loop")
                assertListOfStringStartWith(
                    emptyList(),
                    pager.textInViewport.value,
                    "loop $loop"
                )

                pager.moveToNextPage()
            }
        }
    }

    @Test
    fun multipleBlocks() {
        val fileLength = 100000
        val random = Random(24682)
        val fileContent = (0 ..< fileLength).joinToString("") {
            val newLineFactor = if (random.nextInt(13) == 0) {
                8
            } else {
                131
            }
            if (random.nextInt(newLineFactor) == 0) {
                return@joinToString "\n"
            }

            if (it % 10 == 0) {
                ('0'.code + ((it / 10) % 10)).toChar().toString()
            } else {
                ('a'.code + (it % 10)).toChar().toString()
            }
        }
//        println(fileContent)
        testPaging(fileContent = fileContent, blockSize = 4096)
    }

    @Test
    fun unicodeFullPage1() {
        val random = Random(24683)
        val charset = "零一二三四五六七八九"
        val fileContent = (0 ..< 10000).joinToString("") {
            val newLineFactor = if (random.nextInt(13) == 0) {
                8
            } else {
                131
            }
            if (random.nextInt(newLineFactor) == 0) {
                return@joinToString "\n"
            }

            charset[it % 10].toString()
        }
        val fileLength = fileContent.toByteArray(Charsets.UTF_8).size
        val pageState = PageState(fileContent = fileContent)
        createTestFile(fileContent) { file ->
            val fileReader = GiantFileReader(file.absolutePath)
            val pager = GiantFileTextPager(fileReader, MonospaceBidirectionalTextLayouter(DivisibleWidthCharMeasurer(16f)))
            pager.viewport = Viewport(width = 16 * 23, height = 12 * 12 + 1, density = 1f)
            var loop = 0
            with (pageState) {
                while (loop < fileLength && start < fileLength) {
                    println("pos: ${pager.viewportStartCharPosition}")
                    assertEquals(start.toLong(), pager.viewportStartCharPosition)
                    assertEquals(
                        fileContent.substring(0..< start).toByteArray(Charsets.UTF_8).size.toLong(),
                        pager.viewportStartBytePosition
                    )
                    updatePageState(pageState)

                    assertListOfStringStartWith(
                        fileContent.substring(start..visibleEnd).split(lineSeparatorRegex)
                            .flatMap { it.chunkedUnicode(23) }
                            .take(13),
                        pager.textInViewport.value,
                        "range: [$start, $visibleEnd]"
                    )

                    // each page contains 12 lines
                    (0 ..< 12).forEach {
                        start = if (firstRowBreakPos > 0) {
                            firstRowBreakPos
                        } else {
                            fileLength
                        }
                        updatePageState(pageState)
                    }
                    pager.moveToNextPage()
                    ++loop
                }
            }
            if (loop > fileLength) {
                throw StackOverflowError("Infinite loop detected")
            }
//            assertEquals(fileLength.toLong(), pager.viewportStartCharPosition)
        }
    }

    @Test
    fun unicodeFullPage2() {
        val random = Random(24683)
        val charset = "零一二三四五六七八九"
        val fileContent = (0 ..< 10000).joinToString("") {
            val newLineFactor = if (random.nextInt(13) == 0) {
                8
            } else {
                131
            }
            if (random.nextInt(newLineFactor) == 0) {
                return@joinToString "\n"
            }

            charset[it % 10].toString()
        }
        testPaging(fileContent = fileContent)
    }

    @Test
    fun unicodeMultiBlocks() {
        val random = Random(24685)
        val charset = "零一二三四五六七八九ABCDabc".map { it.toString() } + listOf("😄", "😄", "😇", "🤣", "🤯", "🤬", "🫡", "🫠", "😵")
        val fileContent = (0 ..< 500000).joinToString("") {
            val newLineFactor = if (random.nextInt(13) == 0) {
                8
            } else {
                131
            }
            if (random.nextInt(newLineFactor) == 0) {
                return@joinToString "\n"
            }

            charset[random.nextInt(0, charset.size)]
        }
        testPaging(fileContent = fileContent, blockSize = 8192)
    }
}
