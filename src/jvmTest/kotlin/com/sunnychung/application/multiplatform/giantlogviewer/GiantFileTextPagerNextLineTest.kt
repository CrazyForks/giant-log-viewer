package com.sunnychung.application.multiplatform.giantlogviewer

import com.sunnychung.application.multiplatform.giantlogviewer.io.GiantFileReader
import com.sunnychung.application.multiplatform.giantlogviewer.io.GiantFileTextPager
import com.sunnychung.application.multiplatform.giantlogviewer.io.Viewport
import com.sunnychung.application.multiplatform.giantlogviewer.io.lineSeparatorRegex
import com.sunnychung.application.multiplatform.giantlogviewer.layout.MonospaceBidirectionalTextLayouter
import com.sunnychung.application.multiplatform.giantlogviewer.util.FixedWidthCharMeasurer
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class GiantFileTextPagerNextLineTest {

    @Test
    fun singleLineLongText() {
        val fileLength = 10000
        val fileContent = (0 ..< fileLength).joinToString("") {
            if (it % 10 == 0) {
                ('0'.code + ((it / 10) % 10)).toChar().toString()
            } else {
                ('a'.code + (it % 10)).toChar().toString()
            }
        }
        createTestFile(fileContent) { file ->
            val fileReader = GiantFileReader(file.absolutePath)
            val pager = GiantFileTextPager(fileReader, MonospaceBidirectionalTextLayouter(FixedWidthCharMeasurer(16f)))
            pager.viewport = Viewport(width = 16 * 23, height = 12 * 12 + 1, density = 1f)
            var start = 0
            var loop = 0
            while (loop < fileLength / 10 && start < fileLength) {
                assertEquals(start.toLong(), pager.viewportStartCharPosition)
                val pageEnd = (start + 23 * 13).coerceAtMost(fileLength)
                assertListOfStringStartWith(
                    fileContent.substring(start..< pageEnd).windowed(23, 23, true),
                    pager.textInViewport.value,
                    "range: [$start, $pageEnd)"
                )

                pager.moveToNextLine()
                start += 23
                ++loop
            }
            if (loop >= fileLength / 10) {
                throw StackOverflowError("Infinite loop detected")
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
        createTestFile(fileContent) { file ->
            val fileReader = GiantFileReader(file.absolutePath)
            val pager = GiantFileTextPager(fileReader, MonospaceBidirectionalTextLayouter(FixedWidthCharMeasurer(16f)))
            pager.viewport = Viewport(width = 16 * 23, height = 12 * 12 + 1, density = 1f)
            var start = 0
            var loop = 0
            while (loop < fileLength && start < fileLength) {
                assertEquals(start.toLong(), pager.viewportStartCharPosition)
                var pageEnd = start.coerceAtMost(fileLength)
                var rowBreaks = 0
                var firstRowBreakPos = -1
                var col = 0
                (start ..< (start + 23 * 13).coerceAtMost(fileLength)).forEach {
                    if (rowBreaks < 13) {
                        if (fileContent[it] == '\n') {
                            ++rowBreaks
                            col = 0
                            if (rowBreaks == 1) {
                                firstRowBreakPos = it + 1
                            }
                        } else {
                            ++col
                            if (col > 23) {
                                ++rowBreaks
                                col = 0
                                if (rowBreaks == 1) {
                                    firstRowBreakPos = it
                                }
                            }
                        }
                        pageEnd = it
                    }
                }

                assertListOfStringStartWith(
                    fileContent.substring(start..< pageEnd).split(lineSeparatorRegex).flatMap { it.windowed(23, 23, true) },
                    pager.textInViewport.value,
                    "range: [$start, $pageEnd)"
                )

                pager.moveToNextLine()
                start = if (firstRowBreakPos > 0) {
                    firstRowBreakPos
                } else {
                    fileLength
                }
                ++loop
            }
            if (loop >= fileLength) {
                throw StackOverflowError("Infinite loop detected")
            }
//            assertEquals(fileLength.toLong(), pager.viewportStartCharPosition)
        }
    }

    @Test
    fun shortLine() {
        val fileLength = 5
        val random = Random(24680)
        val fileContent = "abcdE"
//        println(fileContent)
        createTestFile(fileContent) { file ->
            val fileReader = GiantFileReader(file.absolutePath)
            val pager = GiantFileTextPager(fileReader, MonospaceBidirectionalTextLayouter(FixedWidthCharMeasurer(16f)))
            pager.viewport = Viewport(width = 16 * 23, height = 12 * 12 + 1, density = 1f)
            (0 .. 1).forEach { loop ->
                println("pos: ${pager.viewportStartCharPosition}")
                assertEquals(0, pager.viewportStartCharPosition, "loop $loop")
                assertListOfStringStartWith(
                    listOf(fileContent),
                    pager.textInViewport.value,
                    "loop $loop"
                )

                pager.moveToNextLine()
            }
        }
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
                assertListOfStringStartWith(
                    emptyList(),
                    pager.textInViewport.value,
                    "loop $loop"
                )

                pager.moveToNextLine()
            }
        }
    }
}

fun assertListOfStringStartWith(expected: List<CharSequence>, actual: List<CharSequence>, message: String) {
    val message = "$message - $expected VS $actual"
    assert(expected.size <= actual.size) { message }
    expected.forEachIndexed { index, expectedText ->
        if (index < expected.lastIndex) {
            assertEquals(expectedText, actual[index], message)
        } else {
            assert(actual[index].startsWith(expectedText)) { message }
        }
    }
}
