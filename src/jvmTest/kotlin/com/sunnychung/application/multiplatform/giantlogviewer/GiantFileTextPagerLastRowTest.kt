package com.sunnychung.application.multiplatform.giantlogviewer

import com.sunnychung.application.multiplatform.giantlogviewer.io.CoroutineGiantFileTextPager
import com.sunnychung.application.multiplatform.giantlogviewer.io.GiantFileReader
import com.sunnychung.application.multiplatform.giantlogviewer.io.Viewport
import com.sunnychung.application.multiplatform.giantlogviewer.io.lineSeparatorRegex
import com.sunnychung.application.multiplatform.giantlogviewer.layout.MonospaceBidirectionalTextLayouter
import com.sunnychung.application.multiplatform.giantlogviewer.util.DivisibleWidthCharMeasurer
import com.sunnychung.lib.multiplatform.kdatetime.KInstant
import java.io.File
import java.io.Writer
import java.util.UUID
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class GiantFileTextPagerLastRowTest {

    @Test
    fun simpleLargeAsciiFile1() {
        val random = Random(123456)
        val fileLength = 100 * 1024 * 1024
        val lastTestContent = "abcdefg"
        val lastContent = "\n$lastTestContent"

        createTestFile(contentWriter = {
            (0 ..< fileLength - lastContent.length).forEach {
                val charToWrite = when (val r = random.nextInt(37)) {
                    in 0 ..< 26 -> ('a'.code + r).toChar().toString()
                    in 26 ..< 36 -> ('0'.code + (r - 26)).toChar().toString()
                    else -> "\n"
                }
                write(charToWrite)
            }
            write(lastContent)
        }) { file ->
            val fileReader = GiantFileReader(file.absolutePath)
            val pager = CoroutineGiantFileTextPager(
                fileReader, MonospaceBidirectionalTextLayouter(
                    DivisibleWidthCharMeasurer(16f)
                )
            )
            pager.viewport = Viewport(width = 16 * 23, height = 12 * 19 + 1, density = 1f)

            val startTime = KInstant.now()
            try {
                pager.moveToTheLastRow()
                assertEquals((fileLength - lastTestContent.length).toLong(), pager.viewportStartBytePosition)
                assertListOfStringStartWith(
                    lastTestContent.split(lineSeparatorRegex),
                    pager.textInViewport,
                    "last row"
                )
            } finally {
                println("simpleLargeAsciiFile1: ${KInstant.now() - startTime}")
            }
        }
    }

    @Test
    fun simpleLargeAsciiFile2() {
        val random = Random(123456)
        val fileLength = 100 * 1024 * 1024 + random.nextInt(1000)
        var lastNewLine = -1

        createTestFile(contentWriter = {
            (0 ..< fileLength).forEach { i ->
                val charToWrite = run {
                    val newLineFactor = if (random.nextInt(13) == 0) {
                        8
                    } else {
                        1314
                    }
                    if (random.nextInt(newLineFactor) == 0 && i < fileLength - 1) { // not to end the file with '\n'
                        lastNewLine = i
                        return@run "\n"
                    }

                    when (val r = random.nextInt(36)) {
                        in 0 ..< 26 -> ('a'.code + r).toChar().toString()
                        else -> ('0'.code + (r - 26)).toChar().toString()
                    }
                }
                write(charToWrite)
            }
        }) { file ->
            val fileReader = GiantFileReader(file.absolutePath)
            val pager = CoroutineGiantFileTextPager(
                fileReader, MonospaceBidirectionalTextLayouter(
                    DivisibleWidthCharMeasurer(16f)
                )
            )
            pager.viewport = Viewport(width = 16 * 23, height = 12 * 19 + 1, density = 1f)

            val startTime = KInstant.now()
            try {
                pager.moveToTheLastRow()
                val lastRowPosition = fileLength - ((fileLength - (lastNewLine + 1)) % 23)
                println("last row = $lastRowPosition")
                assertEquals(lastRowPosition.toLong(), pager.viewportStartBytePosition)
            } finally {
                println("simpleLargeAsciiFile2: ${KInstant.now() - startTime}")
            }
        }
    }

    @Test
    fun simpleLargeAsciiFileEndsWithNewLine() {
        val random = Random(123457)
        val fileLength = 100 * 1024 * 1024 + random.nextInt(1000)
        var lastNewLine = -1

        createTestFile(contentWriter = {
            (0 ..< fileLength - 1).forEach { i ->
                val charToWrite = run {
                    val newLineFactor = if (random.nextInt(13) == 0) {
                        8
                    } else {
                        1314
                    }
                    if (random.nextInt(newLineFactor) == 0 && i < fileLength - 1) { // not to end the file with '\n'
                        lastNewLine = i
                        return@run "\n"
                    }

                    when (val r = random.nextInt(36)) {
                        in 0 ..< 26 -> ('a'.code + r).toChar().toString()
                        else -> ('0'.code + (r - 26)).toChar().toString()
                    }
                }
                write(charToWrite)
            }
            write("\n")
        }) { file ->
            val fileReader = GiantFileReader(file.absolutePath)
            val pager = CoroutineGiantFileTextPager(
                fileReader, MonospaceBidirectionalTextLayouter(
                    DivisibleWidthCharMeasurer(16f)
                )
            )
            pager.viewport = Viewport(width = 16 * 23, height = 12 * 19 + 1, density = 1f)

            val startTime = KInstant.now()
            try {
                pager.moveToTheLastRow()
                val lastRowPosition = fileLength
                println("last row = $lastRowPosition")
                assertEquals(lastRowPosition.toLong(), pager.viewportStartBytePosition)
                println("actual viewport text = ${pager.textInViewport}")
                assertListOfStringStartWith(
                    emptyList(),
                    pager.textInViewport,
                    "last row"
                )
            } finally {
                println("simpleLargeAsciiFileEndsWithNewLine: ${KInstant.now() - startTime}")
            }
        }
    }

    @Test
    fun singleRow() {
        val fileContent = "ab"
        createTestFile(fileContent) { file ->
            val fileReader = GiantFileReader(file.absolutePath)
            val pager = CoroutineGiantFileTextPager(
                fileReader, MonospaceBidirectionalTextLayouter(
                    DivisibleWidthCharMeasurer(16f)
                )
            )
            pager.viewport = Viewport(width = 16 * 23, height = 12 * 19 + 1, density = 1f)

            pager.moveToTheLastRow()
            assertEquals(0L, pager.viewportStartBytePosition)
            assertListOfStringStartWith(
                listOf(fileContent),
                pager.textInViewport,
                "last row"
            )
        }
    }

    @Test
    fun singleLongLine() {
        val random = Random(1212)
        val fileContent = (0 ..< 1234567).joinToString("") {
            when (val r = random.nextInt(36)) {
                in 0 ..< 26 -> ('a'.code + r).toChar().toString()
                else -> ('0'.code + (r - 26)).toChar().toString()
            }
        }
        createTestFile(fileContent) { file ->
            val fileReader = GiantFileReader(file.absolutePath)
            val pager = CoroutineGiantFileTextPager(
                fileReader, MonospaceBidirectionalTextLayouter(
                    DivisibleWidthCharMeasurer(16f)
                )
            )
            pager.viewport = Viewport(width = 16 * 23, height = 12 * 19 + 1, density = 1f)

            pager.moveToTheLastRow()
            assertEquals(file.length() - (file.length() % 23L), pager.viewportStartBytePosition)
            val textInViewport: List<CharSequence> = pager.textInViewport
            assertEquals(1, textInViewport.size)
            assertEquals((file.length() % 23L).toInt(), textInViewport.first().length)
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

            pager.moveToTheLastRow()
            assertEquals(0L, pager.viewportStartBytePosition)
            assertListOfStringStartWith(
                emptyList(),
                pager.textInViewport,
                "last row"
            )
        }
    }

    @Test
    fun onlyANewLine() {
        createTestFile("\n") { file ->
            val fileReader = GiantFileReader(file.absolutePath)
            val pager = CoroutineGiantFileTextPager(
                fileReader, MonospaceBidirectionalTextLayouter(
                    DivisibleWidthCharMeasurer(16f)
                )
            )
            pager.viewport = Viewport(width = 16 * 23, height = 12 * 19 + 1, density = 1f)

            pager.moveToTheLastRow()
            assertEquals(1L, pager.viewportStartBytePosition)
            assertListOfStringStartWith(
                emptyList(),
                pager.textInViewport,
                "last row"
            )
        }
    }

    @Test
    fun largeUnicodeFileSimple() {
        val random = Random(123456)
        val contentLength = 100 * 1024 * 1024

        val lastTestContent = "中文字串🫡"
        val lastContent = "\n$lastTestContent"

        createTestFile(contentWriter = {
            val charset = "\n零一二三四五六七八九ABCDabc".map { it.toString() } + listOf("😄", "😄", "😇", "🤣", "🤯", "🤬", "🫡", "🫠", "😵")
            (0 ..< contentLength - lastContent.length).forEach {
                val charToWrite = charset[random.nextInt(0, charset.size)]
                write(charToWrite)
            }
            write(lastContent)
        }) { file ->
            val fileLength = file.length()
            val fileReader = GiantFileReader(file.absolutePath)
            val pager = CoroutineGiantFileTextPager(
                fileReader, MonospaceBidirectionalTextLayouter(
                    DivisibleWidthCharMeasurer(16f)
                )
            )
            pager.viewport = Viewport(width = 16 * 23, height = 12 * 19 + 1, density = 1f)

            val startTime = KInstant.now()
            try {
                pager.moveToTheLastRow()
                assertEquals(fileLength - lastTestContent.toByteArray(Charsets.UTF_8).size, pager.viewportStartBytePosition)
                assertListOfStringStartWith(
                    listOf(lastTestContent),
                    pager.textInViewport,
                    "last row"
                )
            } finally {
                println("largeUnicodeFile: ${KInstant.now() - startTime}")
            }
        }
    }

    @Test
    fun largeUnicodeFileEndWithLongRow() {
        val random = Random(123456)
        val contentLength = 100 * 1024 * 1024

        val lastRowContent = "中文字串🫡"
        val lastTestContent = "你好啊".repeat((23 / 2) * 100) + lastRowContent
        val lastContent = "\n$lastTestContent"

        createTestFile(contentWriter = {
            val charset = "\n零一二三四五六七八九ABCDabc".map { it.toString() } + listOf("😄", "😄", "😇", "🤣", "🤯", "🤬", "🫡", "🫠", "😵")
            (0 ..< contentLength - lastContent.length).forEach {
                val charToWrite = charset[random.nextInt(0, charset.size)]
                write(charToWrite)
            }
            write(lastContent)
        }) { file ->
            val fileLength = file.length()
            val fileReader = GiantFileReader(file.absolutePath)
            val pager = CoroutineGiantFileTextPager(
                fileReader, MonospaceBidirectionalTextLayouter(
                    DivisibleWidthCharMeasurer(16f)
                )
            )
            pager.viewport = Viewport(width = 16 * 23, height = 12 * 19 + 1, density = 1f)

            val startTime = KInstant.now()
            try {
                pager.moveToTheLastRow()
                assertEquals(fileLength - lastRowContent.toByteArray(Charsets.UTF_8).size, pager.viewportStartBytePosition)
                assertListOfStringStartWith(
                    listOf(lastRowContent),
                    pager.textInViewport,
                    "last row"
                )
            } finally {
                println("largeUnicodeFileEndWithLongRow: ${KInstant.now() - startTime}")
            }
        }
    }
}

fun createTestFile(contentWriter: Writer.() -> Unit, testBlock: (File) -> Unit) {
    val file = File("build/test", "${UUID.randomUUID()}.txt")
    file.parentFile.mkdirs()
    file.bufferedWriter(Charsets.UTF_8).use {
        it.contentWriter()
    }
    try {
        testBlock(file)
    } finally {
        file.delete()
    }
}
