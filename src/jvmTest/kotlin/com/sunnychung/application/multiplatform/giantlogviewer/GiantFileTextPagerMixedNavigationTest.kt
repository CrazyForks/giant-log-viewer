package com.sunnychung.application.multiplatform.giantlogviewer

import com.sunnychung.application.multiplatform.giantlogviewer.io.CoroutineGiantFileTextPager
import com.sunnychung.application.multiplatform.giantlogviewer.io.GiantFileReader
import com.sunnychung.application.multiplatform.giantlogviewer.io.Viewport
import com.sunnychung.application.multiplatform.giantlogviewer.layout.MonospaceBidirectionalTextLayouter
import com.sunnychung.application.multiplatform.giantlogviewer.util.FixedWidthCharMeasurer
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GiantFileTextPagerMixedNavigationTest {

    @ParameterizedTest
    @EnumSource(TestFileEncoding::class)
    fun computedPropertiesAndEncodedLengthReflectCurrentConfiguration(encoding: TestFileEncoding) {
        val fileContent = "ab\ncd\n"
        createTestFile(fileContent, encoding) { file ->
            val fileReader = GiantFileReader(file.absolutePath)
            val pager = CoroutineGiantFileTextPager(
                fileReader,
                MonospaceBidirectionalTextLayouter(FixedWidthCharMeasurer(charWidth = 10f, rowHeight = 5f)),
            )

            pager.viewport = Viewport(width = 20, height = 15, density = 1f)

            assertEquals(5f, pager.rowHeight())
            assertEquals(3L, pager.numOfRowsInViewport)
            assertTrue(pager.isSoftWrapEnabled)
            assertEquals("A\uD83D\uDE04".toByteArray(encoding.charset).size.toLong(), pager.encodedLengthOfText("A\uD83D\uDE04"))
            assertEquals(Viewport(width = 20, height = 15, density = 1f), pager.viewport)
            assertEquals(listOf("ab", "cd"), pager.textInViewport.take(2).map { it.toString() })
            assertEquals(listOf(encoding.bytePosition(fileContent, 0), encoding.bytePosition(fileContent, 3)), pager.startBytePositionsInViewport.take(2))

            pager.viewportStartBytePosition = encoding.bytePosition(fileContent, 3)

            assertEquals(encoding.bytePosition(fileContent, 3), pager.viewportStartBytePosition)
            assertEquals("cd", pager.textInViewport.first().toString())
        }
    }

    @ParameterizedTest
    @EnumSource(TestFileEncoding::class)
    fun moveToRowOfBytePositionAlignsToRenderedRowStart(encoding: TestFileEncoding) {
        val fileContent = "abcde\nsecond\n"
        createTestFile(fileContent, encoding) { file ->
            val fileReader = GiantFileReader(file.absolutePath)
            val pager = CoroutineGiantFileTextPager(
                fileReader,
                MonospaceBidirectionalTextLayouter(FixedWidthCharMeasurer(10f)),
            )
            pager.viewport = Viewport(width = 20, height = 36, density = 1f)

            pager.moveToRowOfBytePosition(encoding.bytePosition(fileContent, 3))

            assertEquals(encoding.bytePosition(fileContent, 2), pager.viewportStartBytePosition)
            assertEquals("cd", pager.textInViewport.first().toString())
        }
    }

    @ParameterizedTest
    @EnumSource(TestFileEncoding::class)
    fun moveMultipleRowsForwardAndBackward(encoding: TestFileEncoding) {
        val fileContent = "r0\nr1\nr2\nr3\n"
        createTestFile(fileContent, encoding) { file ->
            val fileReader = GiantFileReader(file.absolutePath)
            val pager = CoroutineGiantFileTextPager(
                fileReader,
                MonospaceBidirectionalTextLayouter(FixedWidthCharMeasurer(10f)),
            )
            pager.viewport = Viewport(width = 80, height = 36, density = 1f)

            pager.moveToNextRow(2L)

            assertEquals(encoding.bytePosition(fileContent, 6), pager.viewportStartBytePosition)
            assertEquals("r2", pager.textInViewport.first().toString())

            pager.moveToPrevRow(1L)

            assertEquals(encoding.bytePosition(fileContent, 3), pager.viewportStartBytePosition)
            assertEquals("r1", pager.textInViewport.first().toString())

            pager.moveToPrevRow(5L)

            assertEquals(encoding.contentStartBytePosition, pager.viewportStartBytePosition)
            assertEquals("r0", pager.textInViewport.first().toString())
        }
    }
}
