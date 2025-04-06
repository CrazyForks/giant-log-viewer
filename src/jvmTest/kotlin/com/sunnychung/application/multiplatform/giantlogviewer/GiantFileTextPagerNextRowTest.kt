package com.sunnychung.application.multiplatform.giantlogviewer

import com.sunnychung.application.multiplatform.giantlogviewer.io.CoroutineGiantFileTextPager
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

class GiantFileTextPagerNextRowTest {

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
            val pager = CoroutineGiantFileTextPager(fileReader, MonospaceBidirectionalTextLayouter(FixedWidthCharMeasurer(16f)))
            pager.viewport = Viewport(width = 16 * 23, height = 12 * 12 + 1, density = 1f)
            var start = 0
            var loop = 0
            while (loop < fileLength / 10 && start < fileLength) {
                assertEquals(start.toLong(), pager.viewportStartCharPosition)
                assertEquals(start.toLong(), pager.viewportStartBytePosition)
                val pageEnd = (start + 23 * 13).coerceAtMost(fileLength)
                assertListOfStringStartWith(
                    fileContent.substring(start..< pageEnd).windowed(23, 23, true),
                    pager.textInViewport,
                    "range: [$start, $pageEnd)"
                )

                pager.moveToNextRow()
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
            val pager = CoroutineGiantFileTextPager(fileReader, MonospaceBidirectionalTextLayouter(FixedWidthCharMeasurer(16f)))
            pager.viewport = Viewport(width = 16 * 23, height = 12 * 12 + 1, density = 1f)
            val pageState = PageState(fileContent = fileContent)
            var loop = 0
            with (pageState) {
                while (loop < fileLength && start < fileLength) {
                    assertEquals(start.toLong(), pager.viewportStartCharPosition)
                    assertEquals(start.toLong(), pager.viewportStartBytePosition)
                    updatePageState(this)

                    assertListOfStringStartWith(
                        fileContent.substring(start ..< visibleEnd).split(lineSeparatorRegex)
                            .flatMap { it.windowed(23, 23, true) },
                        pager.textInViewport,
                        "range: [$start, $visibleEnd)"
                    )

                    pager.moveToNextRow()
                    start = if (firstRowBreakPos > 0) {
                        firstRowBreakPos
                    } else {
                        fileLength
                    }
                    ++loop
                }
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
            val pager = CoroutineGiantFileTextPager(fileReader, MonospaceBidirectionalTextLayouter(FixedWidthCharMeasurer(16f)))
            pager.viewport = Viewport(width = 16 * 23, height = 12 * 12 + 1, density = 1f)
            (0 .. 1).forEach { loop ->
                println("pos: ${pager.viewportStartCharPosition}")
                assertEquals(0, pager.viewportStartCharPosition, "loop $loop")
                assertEquals(0, pager.viewportStartBytePosition, "loop $loop")
                assertListOfStringStartWith(
                    listOf(fileContent),
                    pager.textInViewport,
                    "loop $loop"
                )

                pager.moveToNextRow()
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
            val pager = CoroutineGiantFileTextPager(fileReader, MonospaceBidirectionalTextLayouter(FixedWidthCharMeasurer(16f)))
            pager.viewport = Viewport(width = 16 * 23, height = 12 * 12 + 1, density = 1f)
            (0 .. 1).forEach { loop ->
                println("pos: ${pager.viewportStartCharPosition}")
                assertEquals(0, pager.viewportStartCharPosition, "loop $loop")
                assertEquals(0, pager.viewportStartBytePosition, "loop $loop")
                assertListOfStringStartWith(
                    emptyList(),
                    pager.textInViewport,
                    "loop $loop"
                )

                pager.moveToNextRow()
            }
        }
    }

    @Test
    fun multipleBlocks() {
        val fileLength = 100000
        val random = Random(24681)
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
            val fileReader = GiantFileReader(file.absolutePath, 4096)
            val pager = CoroutineGiantFileTextPager(fileReader, MonospaceBidirectionalTextLayouter(FixedWidthCharMeasurer(16f)))
            pager.viewport = Viewport(width = 16 * 23, height = 12 * 12 + 1, density = 1f)
            val pageState = PageState(fileContent = fileContent)
            var loop = 0
            with (pageState) {
                while (loop < fileLength && start < fileLength) {
                    assertEquals(start.toLong(), pager.viewportStartCharPosition)
                    assertEquals(start.toLong(), pager.viewportStartBytePosition)
                    updatePageState(this)

                    assertListOfStringStartWith(
                        fileContent.substring(start ..< visibleEnd).split(lineSeparatorRegex)
                            .flatMap { it.windowed(23, 23, true) },
                        pager.textInViewport,
                        "range: [$start, $visibleEnd)"
                    )

                    pager.moveToNextRow()
                    start = if (firstRowBreakPos > 0) {
                        firstRowBreakPos
                    } else {
                        fileLength
                    }
                    ++loop
                }
            }
            if (loop >= fileLength) {
                throw StackOverflowError("Infinite loop detected")
            }
//            assertEquals(fileLength.toLong(), pager.viewportStartCharPosition)
        }
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
        val fileLength = fileContent.toByteArray(Charsets.UTF_8).size
        val pageState = PageState(fileContent = fileContent)
        createTestFile(fileContent) { file ->
            val fileReader = GiantFileReader(file.absolutePath)
            val pager = CoroutineGiantFileTextPager(fileReader, MonospaceBidirectionalTextLayouter(DivisibleWidthCharMeasurer(16f)))
            pager.viewport = Viewport(width = 16 * 23, height = 12 * 12 + 1, density = 1f)
            var loop = 0
            with (pageState) {
                while (loop < fileLength && start < fileLength) {
                    assertEquals(start.toLong(), pager.viewportStartCharPosition)
                    assertEquals(
                        fileContent.substring(0..<start).toByteArray(Charsets.UTF_8).size.toLong(),
                        pager.viewportStartBytePosition
                    )
                    updatePageState(pageState)

                    assertListOfStringStartWith(
                        fileContent.substring(start..visibleEnd).split(lineSeparatorRegex),
                        pager.textInViewport,
                        "range: [$start, $visibleEnd]"
                    )

                    pager.moveToNextRow()
                    start = if (firstRowBreakPos > 0) {
                        firstRowBreakPos /*-
                        fileContent.subSequence(0..<firstRowBreakPos)
                            .count { it.isLowSurrogate() }*/
                    } else {
                        fileLength
                    }
                    ++loop
                }
            }
            if (loop >= fileLength) {
                throw StackOverflowError("Infinite loop detected")
            }
//            assertEquals(fileLength.toLong(), pager.viewportStartCharPosition)
        }
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
            (23 - 9 .. 23 + 9 + 2).map { numOfPrefixChars ->
                (0 ..< numOfPrefixChars).joinToString("") {
                    ('0'.code + (it % 10)).toChar().toString()
                } + line
            }
        }
        val fileContent = multipliedLines.joinToString("\n")
        val fileLength = fileContent.toByteArray(Charsets.UTF_8).size
        val pageState = PageState(fileContent = fileContent)
        createTestFile(fileContent) { file ->
            val fileReader = GiantFileReader(file.absolutePath)
            val pager = CoroutineGiantFileTextPager(fileReader, MonospaceBidirectionalTextLayouter(DivisibleWidthCharMeasurer(16f)))
            pager.viewport = Viewport(width = 16 * 23, height = 12 * 12 + 1, density = 1f)
            var loop = 0
            with (pageState) {
                while (loop < fileLength && start < fileLength) {
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
                        pager.textInViewport,
                        "range: [$start, $visibleEnd]"
                    )

                    pager.moveToNextRow()
                    start = if (firstRowBreakPos > 0) {
                        firstRowBreakPos
                    } else {
                        fileLength
                    }
                    ++loop
                }
            }
            if (loop >= fileLength) {
                throw StackOverflowError("Infinite loop detected")
            }
//            assertEquals(fileLength.toLong(), pager.viewportStartCharPosition)
        }
    }

    @Test
    fun unicodeFullPage() {
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
            val pager = CoroutineGiantFileTextPager(fileReader, MonospaceBidirectionalTextLayouter(DivisibleWidthCharMeasurer(16f)))
            pager.viewport = Viewport(width = 16 * 23, height = 12 * 12 + 1, density = 1f)
            var loop = 0
            with (pageState) {
                while (loop < fileLength && start < fileLength) {
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
                        pager.textInViewport,
                        "range: [$start, $visibleEnd]"
                    )

                    pager.moveToNextRow()
                    start = if (firstRowBreakPos > 0) {
                        firstRowBreakPos
                    } else {
                        fileLength
                    }
                    ++loop
                }
            }
            if (loop >= fileLength) {
                throw StackOverflowError("Infinite loop detected")
            }
//            assertEquals(fileLength.toLong(), pager.viewportStartCharPosition)
        }
    }

    @Test
    fun unicodeMultiBlocks() {
        val random = Random(24683)
        val charset = "零一二三四五六七八九ABCDabc".map { it.toString() } + listOf("😄", "😄", "😇", "🤣", "🤯", "🤬", "🫡", "🫠", "😵")
        val fileContent = (0 ..< 100000).joinToString("") {
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
        val fileLength = fileContent.toByteArray(Charsets.UTF_8).size
        val pageState = PageState(fileContent = fileContent)
        createTestFile(fileContent) { file ->
            val fileReader = GiantFileReader(file.absolutePath, 4096)
            val pager = CoroutineGiantFileTextPager(fileReader, MonospaceBidirectionalTextLayouter(DivisibleWidthCharMeasurer(16f)))
            pager.viewport = Viewport(width = 16 * 23, height = 12 * 12 + 1, density = 1f)
            var loop = 0
            with (pageState) {
                while (loop < fileLength && start < fileLength) {
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
                        pager.textInViewport,
                        "range: [$start, $visibleEnd]"
                    )

                    pager.moveToNextRow()
                    start = if (firstRowBreakPos > 0) {
                        firstRowBreakPos
                    } else {
                        fileLength
                    }
                    ++loop
                }
            }
            if (loop >= fileLength) {
                throw StackOverflowError("Infinite loop detected")
            }
//            assertEquals(fileLength.toLong(), pager.viewportStartCharPosition)
        }
    }
}

fun assertListOfStringStartWith(expected: List<CharSequence>, actual: List<CharSequence>, message: String) {
    val message = "$message - $expected VS $actual"
    assert(expected.size <= actual.size) { "$message - ${expected.size} should <= ${actual.size}" }
    expected.forEachIndexed { index, expectedText ->
        if (index < expected.lastIndex) {
            assertEquals(expectedText, actual[index], message)
        } else {
            assert(actual[index].startsWith(expectedText)) { message }
        }
    }
}

internal fun updatePageState(state: PageState) {
    with (state) {
        pageEnd = start.coerceAtMost(contentLength)
        visibleEnd = start.coerceAtMost(contentLength)
        rowBreaks = 0
        firstRowBreakPos = -1
        col = 0

        (start ..< (start + 23 * 13 + 1).coerceAtMost(contentLength)).forEach {
            if (rowBreaks < 13) {
                if (fileContent[it] == '\n') {
                    ++rowBreaks
                    col = 0
                    if (rowBreaks == 1) {
                        firstRowBreakPos = it + 1
                    }
                    if (rowBreaks <= 12) {
                        pageEnd = it + 1
                    }
                } else if (!fileContent[it].isLowSurrogate()) { // a hi-lo surrogate pair should be processed only once
                    val charSpan = if (fileContent[it].code <= 126) 1 else 2
                    col += charSpan
                    if (col > 23) {
                        ++rowBreaks
                        col = charSpan
                        if (rowBreaks == 1) {
                            firstRowBreakPos = it
                        }
                        if (rowBreaks <= 12) {
                            pageEnd = it
                        }
                    }
                }
                visibleEnd = it - if (fileContent[it].isHighSurrogate()) 1 else 0
            }
        }
        if (pageEnd == start) {
            pageEnd = fileLength
        }
    }
}

internal data class PageState(
    var start: Int = 0,
//    var loop: Int = 0,
    var col: Int = 0,
    var visibleEnd: Int = 0, // inclusive
    var pageEnd: Int = 0, // exclusive
    var rowBreaks: Int = 0,
    var firstRowBreakPos: Int = -1,
    var fileContent: String,

) {
    val fileLength: Int = fileContent.toByteArray(Charsets.UTF_8).size
    val contentLength: Int = fileContent.length
}

fun CharSequence.chunkedUnicode(chunkSize: Int): List<CharSequence> {
    if (isEmpty()) {
        return listOf("")
    }

    val chunked = mutableListOf<CharSequence>()
    var start = 0
    var len = 0
    indices.forEach {
        if (this[it].isHighSurrogate()) {
            return@forEach
        }
        val charSpan = if (this[it].code <= 126) 1 else 2
        len += charSpan
        if (len > chunkSize) {
            val lastEnd = it - 1 - if (this[it - 1].isHighSurrogate()) 1 else 0
            chunked += subSequence(start .. lastEnd)
            len = charSpan
            start = lastEnd + 1
        } else if (len == chunkSize) {
            chunked += subSequence(start .. it)
            len = 0
            start = it + 1
        }
    }
    if (start < length) {
        chunked += subSequence(start ..< length)
    }
    return chunked
}
