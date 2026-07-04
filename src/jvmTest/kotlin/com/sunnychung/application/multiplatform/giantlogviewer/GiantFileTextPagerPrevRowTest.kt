package com.sunnychung.application.multiplatform.giantlogviewer

import com.sunnychung.application.multiplatform.giantlogviewer.io.CoroutineGiantFileTextPager
import com.sunnychung.application.multiplatform.giantlogviewer.io.GiantFileReader
import com.sunnychung.application.multiplatform.giantlogviewer.io.GiantFileTextPager
import com.sunnychung.application.multiplatform.giantlogviewer.io.Viewport
import com.sunnychung.application.multiplatform.giantlogviewer.layout.MonospaceBidirectionalTextLayouter
import com.sunnychung.application.multiplatform.giantlogviewer.util.FixedWidthCharMeasurer
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import kotlin.random.Random
import kotlin.test.assertEquals

class GiantFileTextPagerPrevRowTest {

    @ParameterizedTest
    @EnumSource(TestFileEncoding::class)
    fun singleLineLongText(encoding: TestFileEncoding) {
        val fileLength = 10000
        val random = Random(34567)
        val fileContent = (0..<fileLength).joinToString("") {
            when (val r = random.nextInt(10 + 26)) {
                in 0 ..< 10 -> ('0'.code + r).toChar().toString()
                else -> ('a'.code + (r - 10)).toChar().toString()
            }
        }
        testNextRowThenPrevRow(fileContent, encoding)
    }

    @ParameterizedTest
    @EnumSource(TestFileEncoding::class)
    fun multipleLines(encoding: TestFileEncoding) {
        val fileLength = 100000
        val random = Random(34568)
        val fileContent = (0..< fileLength).joinToString("") {
            val newLineFactor = if (random.nextInt(13) == 0) {
                8
            } else {
                131
            }
            if (random.nextInt(newLineFactor) == 0) {
                return@joinToString "\n"
            }

            when (val r = random.nextInt(10 + 26)) {
                in 0 ..< 10 -> ('0'.code + r).toChar().toString()
                else -> ('a'.code + (r - 10)).toChar().toString()
            }
        }
        testNextRowThenPrevRow(fileContent, encoding)
    }

    @ParameterizedTest
    @EnumSource(TestFileEncoding::class)
    fun shortLine(encoding: TestFileEncoding) {
        val fileContent = "ab"
        testNextRowThenPrevRow(fileContent, encoding)
    }

    @ParameterizedTest
    @EnumSource(TestFileEncoding::class)
    fun lastLineIsEmpty(encoding: TestFileEncoding) {
        val fileContent = "abc\n"
        testNextRowThenPrevRow(fileContent, encoding)
    }

    @ParameterizedTest
    @EnumSource(TestFileEncoding::class)
    fun emptyFile(encoding: TestFileEncoding) {
        val fileContent = ""
        testNextRowThenPrevRow(fileContent, encoding)
    }

    @ParameterizedTest
    @EnumSource(TestFileEncoding::class)
    fun multipleBlocks(encoding: TestFileEncoding) {
        val fileLength = 1000000
        val random = Random(34569)
        val fileContent = (0..< fileLength).joinToString("") {
            val newLineFactor = if (random.nextInt(13) == 0) {
                8
            } else {
                131
            }
            if (random.nextInt(newLineFactor) == 0) {
                return@joinToString "\n"
            }

            when (val r = random.nextInt(10 + 26)) {
                in 0 ..< 10 -> ('0'.code + r).toChar().toString()
                else -> ('a'.code + (r - 10)).toChar().toString()
            }
        }
        testNextRowThenPrevRow(fileContent, encoding, blockSize = 2048)
    }

    @ParameterizedTest
    @EnumSource(TestFileEncoding::class)
    fun unicodeSimple(encoding: TestFileEncoding) {
        val lines = listOf(
            "你好呀",
            "啲Emoji好q麻煩",
            "😄😄...😇🤣🤯🤬🫡🫠😵",
            "zzz😪",
            "🤑🤑$$",
            "🍙",
        )
        val fileContent = lines.joinToString("\n")
        testNextRowThenPrevRow(fileContent, encoding)
    }

    @ParameterizedTest
    @EnumSource(TestFileEncoding::class)
    fun unicodeAcrossRowBreaks(encoding: TestFileEncoding) {
        val lines = listOf(
            "喂你好",
            "啲Emoji好q麻煩",
            "😄😄...😇🤣🤯🤬🫡🫠😵",
            "zzz😪",
            "🤑🤑$$",
            "🍙",
        )
        val multipliedLines = lines.flatMap { line ->
            (23 - 9..23 + 9 + 2).map { numOfPrefixChars ->
                (0..<numOfPrefixChars).joinToString("") {
                    ('0'.code + (it % 10)).toChar().toString()
                } + line
            }
        }
        val fileContent = multipliedLines.joinToString("\n")
        testNextRowThenPrevRow(fileContent, encoding)
    }

    @ParameterizedTest
    @EnumSource(TestFileEncoding::class)
    fun unicodeMultiBlocks(encoding: TestFileEncoding) {
        val random = Random(45678)
        val charset = "零一二三四五六七八九ABCDabc".map { it.toString() } + listOf("😄", "😄", "😇", "🤣", "🤯", "🤬", "🫡", "🫠", "😵")
        val fileContent = (0 ..< 400000).joinToString("") {
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
        testNextRowThenPrevRow(fileContent = fileContent, encoding = encoding, blockSize = 4096)
    }
}

private fun testNextRowThenPrevRow(
    fileContent: String,
    encoding: TestFileEncoding,
    blockSize: Int = 1 * 1024 * 1024,
) {
    createTestFile(fileContent, encoding) { file ->
        val fileLength = file.length()
        val fileReader = GiantFileReader(file.absolutePath, blockSize)
        val pager = CoroutineGiantFileTextPager(fileReader, MonospaceBidirectionalTextLayouter(FixedWidthCharMeasurer(16f)))
        pager.viewport = Viewport(width = 16 * 23, height = 12 * 12 + 1, density = 1f)
        val rowStarts = mutableListOf<Long>()
        val rowByteStarts = mutableListOf<Long>()
        var start = 0L
        var loop = 0
        while (start < fileLength && loop <= fileLength) {
            rowStarts += pager.viewportStartCharPosition
            rowByteStarts += pager.viewportStartBytePosition

            pager.moveToNextRow()

            if (pager.viewportStartBytePosition > start) {
                start = pager.viewportStartBytePosition
            } else {
                start = fileLength
            }
            ++loop
        }
        if (loop > fileLength) {
            throw StackOverflowError("Infinite loop detected")
        }
        if (rowStarts.size > 1) {
            rowStarts.removeLast()
        }
        rowStarts.indices.reversed().forEach {
            pager.moveToPrevRow()
//            assertEquals(rowStarts[it], pager.viewportStartCharPosition)
            assertEquals(rowByteStarts[it], pager.viewportStartBytePosition)
        }

        repeat(2) {
            pager.moveToPrevRow()
//            assertEquals(0L, pager.viewportStartCharPosition)
            assertEquals(encoding.contentStartBytePosition, pager.viewportStartBytePosition)
        }
    }
}
