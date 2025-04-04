package com.sunnychung.application.multiplatform.giantlogviewer

import com.sunnychung.application.multiplatform.giantlogviewer.io.CoroutineGiantFileTextPager
import com.sunnychung.application.multiplatform.giantlogviewer.io.GiantFileReader
import com.sunnychung.application.multiplatform.giantlogviewer.io.Viewport
import com.sunnychung.application.multiplatform.giantlogviewer.io.lineSeparatorRegex
import com.sunnychung.application.multiplatform.giantlogviewer.layout.MonospaceBidirectionalTextLayouter
import com.sunnychung.application.multiplatform.giantlogviewer.util.DivisibleWidthCharMeasurer
import com.sunnychung.lib.multiplatform.kdatetime.KInstant
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class GiantFileTextPagerFirstRowTest {

    @Test
    fun jumpFromMiddleOfLargeAsciiFile() {
        val random = Random(123456)
        val fileLength = 100 * 1024 * 1024

        val firstContentCacheLength = 10000
        val firstContent = StringBuilder(firstContentCacheLength)

        createTestFile(contentWriter = {
            (0 ..< fileLength).forEach {
                val charToWrite = when (val r = random.nextInt(37)) {
                    in 0 ..< 26 -> ('a'.code + r).toChar().toString()
                    in 26 ..< 36 -> ('0'.code + (r - 26)).toChar().toString()
                    else -> "\n"
                }
                write(charToWrite)
                if (firstContent.length < firstContentCacheLength) {
                    firstContent.append(charToWrite)
                }
            }
        }) { file ->
            val fileReader = GiantFileReader(file.absolutePath)
            val pager = CoroutineGiantFileTextPager(
                fileReader, MonospaceBidirectionalTextLayouter(
                    DivisibleWidthCharMeasurer(16f)
                )
            )
            pager.viewport = Viewport(width = 16 * 23, height = 12 * 19 + 1, density = 1f)

            pager.moveToBytePosition(fileLength / 2L)
            pager.moveToNextPage()
            pager.moveToNextPage()
            pager.moveToNextPage()
            pager.moveToNextRow()
            pager.moveToNextRow()

            val startTime = KInstant.now()
            try {
                pager.moveToTheFirstRow()
                assertEquals(0L, pager.viewportStartBytePosition)
                assertListOfStringStartWith(
                    firstContent.split(lineSeparatorRegex)
                        .flatMap { it.chunkedUnicode(23) }
                        .take(19 + 1),
                    pager.textInViewport,
                    "first row"
                )
            } finally {
                println("jumpFromMiddleOfLargeAsciiFile: ${KInstant.now() - startTime}")
            }
        }
    }

    @Test
    fun jumpFromTheFirstRow() {
        val fileContent = "\nabcd\nefg\n\n\n\nhi\nj"

        createTestFile(fileContent) { file ->
            val fileReader = GiantFileReader(file.absolutePath)
            val pager = CoroutineGiantFileTextPager(
                fileReader, MonospaceBidirectionalTextLayouter(
                    DivisibleWidthCharMeasurer(16f)
                )
            )
            pager.viewport = Viewport(width = 16 * 23, height = 12 * 19 + 1, density = 1f)

            val startTime = KInstant.now()
            try {
                pager.moveToTheFirstRow()
                assertEquals(0L, pager.viewportStartBytePosition)
                assertListOfStringStartWith(
                    fileContent.split(lineSeparatorRegex)
                        .flatMap { it.chunkedUnicode(23) }
                        .take(19 + 1),
                    pager.textInViewport,
                    "first row"
                )
            } finally {
                println("jumpFromFirstRow: ${KInstant.now() - startTime}")
            }
        }
    }

    @Test
    fun emptyFile() {
        createTestFile("") { file ->
            val fileReader = GiantFileReader(file.absolutePath)
            val pager = CoroutineGiantFileTextPager(
                fileReader, MonospaceBidirectionalTextLayouter(
                    DivisibleWidthCharMeasurer(16f)
                )
            )
            pager.viewport = Viewport(width = 16 * 23, height = 12 * 19 + 1, density = 1f)

            pager.moveToTheFirstRow()
            assertEquals(0L, pager.viewportStartBytePosition)
            assertListOfStringStartWith(
                emptyList(),
                pager.textInViewport,
                "first row"
            )
        }
    }
}
