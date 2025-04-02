package com.sunnychung.application.multiplatform.giantlogviewer

import com.sunnychung.application.multiplatform.giantlogviewer.io.GiantFileReader
import com.sunnychung.application.multiplatform.giantlogviewer.io.GiantFileTextPager
import com.sunnychung.application.multiplatform.giantlogviewer.io.Viewport
import com.sunnychung.application.multiplatform.giantlogviewer.layout.MonospaceBidirectionalTextLayouter
import com.sunnychung.application.multiplatform.giantlogviewer.util.FixedWidthCharMeasurer
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class GiantFileTextPagerPrevRowTest {

    @Test
    fun singleLineLongText() {
        val fileLength = 10000
        val random = Random(34567)
        val fileContent = (0..<fileLength).joinToString("") {
            when (val r = random.nextInt(10 + 26)) {
                in 0 ..< 10 -> ('0'.code + r).toChar().toString()
                else -> ('a'.code + (r - 10)).toChar().toString()
            }
        }
        testNextRowThenPrevRow(fileContent)
    }

    @Test
    fun multipleLines() {
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
        testNextRowThenPrevRow(fileContent)
    }

    @Test
    fun shortLine() {
        val fileContent = "ab"
        testNextRowThenPrevRow(fileContent)
    }

    @Test
    fun lastLineIsEmpty() {
        val fileContent = "abc\n"
        testNextRowThenPrevRow(fileContent)
    }

    @Test
    fun emptyFile() {
        val fileContent = ""
        testNextRowThenPrevRow(fileContent)
    }

    @Test
    fun multipleBlocks() {
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
        testNextRowThenPrevRow(fileContent, blockSize = 2048)
    }

    @Test
    fun unicodeSimple() {
        val lines = listOf(
            "你好呀",
            "啲Emoji好q麻煩",
            "😄😄...😇🤣🤯🤬🫡🫠😵",
            "zzz😪",
            "🤑🤑$$",
            "🍙",
        )
        val fileContent = lines.joinToString("\n")
        testNextRowThenPrevRow(fileContent)
    }

    @Test
    fun unicodeAcrossRowBreaks() {
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
        testNextRowThenPrevRow(fileContent)
    }

    @Test
    fun unicodeMultiBlocks() {
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
        testNextRowThenPrevRow(fileContent = fileContent, blockSize = 4096)
    }
}

private fun testNextRowThenPrevRow(fileContent: String, blockSize: Int = 1 * 1024 * 1024) {
    createTestFile(fileContent) { file ->
        val fileLength = file.length()
        val fileReader = GiantFileReader(file.absolutePath, blockSize)
        val pager = GiantFileTextPager(fileReader, MonospaceBidirectionalTextLayouter(FixedWidthCharMeasurer(16f)))
        pager.viewport = Viewport(width = 16 * 23, height = 12 * 12 + 1, density = 1f)
        val rowStarts = mutableListOf<Long>()
        val rowByteStarts = mutableListOf<Long>()
        var start = 0L
        var loop = 0
        while (start < fileLength && loop <= fileLength) {
            rowStarts += pager.viewportStartCharPosition
            rowByteStarts += pager.viewportStartBytePosition

            pager.moveToNextRow()

            if (pager.viewportStartCharPosition > start) {
                start = pager.viewportStartCharPosition
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
            assertEquals(rowStarts[it], pager.viewportStartCharPosition)
            assertEquals(rowByteStarts[it], pager.viewportStartBytePosition)
        }

        repeat(2) {
            pager.moveToPrevRow()
            assertEquals(0L, pager.viewportStartCharPosition)
            assertEquals(0L, pager.viewportStartBytePosition)
        }
    }
}
