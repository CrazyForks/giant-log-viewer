package com.sunnychung.application.multiplatform.giantlogviewer

import com.sunnychung.application.multiplatform.giantlogviewer.io.CoroutineGiantFileTextPager
import com.sunnychung.application.multiplatform.giantlogviewer.io.GiantFileReader
import com.sunnychung.application.multiplatform.giantlogviewer.io.Viewport
import com.sunnychung.application.multiplatform.giantlogviewer.layout.MonospaceBidirectionalTextLayouter
import com.sunnychung.application.multiplatform.giantlogviewer.util.DivisibleWidthCharMeasurer
import java.io.File
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class GiantFileTextPagerSearchBackwardTest {

    @Test
    fun simpleSearch() {
        val fileContent = "abcdfg\nefg\nhijfgfkfgl"
        val searchPattern = "fg"
        createTestFile(fileContent) { file ->
            verifySearch(file, fileContent, searchPattern)
        }
    }

    @Test
    fun overlappedSearches() {
        val fileContent = "fffabcdffff\nefg\nfffffffff"
        val searchPattern = "ff"
        createTestFile(fileContent) { file ->
            verifySearch(file, fileContent, searchPattern)
        }
    }

    @Test
    fun singleChar() {
        val fileContent = "fffabcdffff\nefg\nfffffffff"
        val searchPattern = "f"
        createTestFile(fileContent) { file ->
            verifySearch(file, fileContent, searchPattern)
        }
    }

    @Test
    fun notFound() {
        val fileContent = "fffabcdffff\nefg\nfffffffff"
        val searchPattern = "z"
        createTestFile(fileContent) { file ->
            verifySearch(file, fileContent, searchPattern)
        }
    }

    @Test
    fun unicode() {
        val fileContent = "喂你好你好你好你好你好呀."
        val searchPattern = "你好你"
        createTestFile(fileContent) { file ->
            verifySearch(file, fileContent, searchPattern)
        }
    }

    @Test
    fun asciiAcrossMultipleBlocks() {
        val random = Random(2345)
        val searchPattern = "@@AB"
        val blockSize = 200
        val fileContent: String = (0 ..< 10000).joinToString { i ->
            if (i % blockSize >= blockSize - searchPattern.length && random.nextInt(9) == 0) {
                return@joinToString searchPattern
            }
            when (val r = random.nextInt(38)) {
                in 0 ..< 10 -> ('0'.code + r).toChar().toString()
                in 10 ..< 36 -> ('a'.code + (r - 10)).toChar().toString()
                36 -> "\n"
                else -> searchPattern
            }
        }
        createTestFile(fileContent) { file ->
            verifySearch(file, fileContent, searchPattern, blockSize)
        }
    }

    @Test
    fun searchLongTextInAsciiAcrossMultipleBlocks() {
        val random = Random(2346)
        val searchPattern = "@@ABadiofjbafasoijgfafkmsgoasoksfoiasjfggnJngjgnsgnnsgaIJROEtmkfamskfgsdfgmksldmfgmksglsfGLMSFKGllmksgl"
        val blockSize = 200
        val fileContent: String = (0 ..< 4000).joinToString { i ->
            if (i % blockSize >= blockSize - searchPattern.length && random.nextInt(9) == 0) {
                return@joinToString searchPattern
            }
            when (val r = random.nextInt(38)) {
                in 0 ..< 10 -> ('0'.code + r).toChar().toString()
                in 10 ..< 36 -> ('a'.code + (r - 10)).toChar().toString()
                36 -> "\n"
                else -> searchPattern
            }
        }
        createTestFile(fileContent) { file ->
            verifySearch(file, fileContent, searchPattern, blockSize)
        }
    }

    @Test
    fun unicodeAcrossMultipleBlocks() {
        val random = Random(2347)
        val searchPattern = "喂你好😄😄!"
        val blockSize = 200
        val charset = "零一二三四五六七八九ABCDabc".map { it.toString() } + listOf("😄", "😄", "😇", "🤣", "🤯", "🤬", "🫡", "🫠", "😵")
        val fileContent: String = (0 ..< 5000).joinToString { i ->
            if (i % blockSize >= blockSize - searchPattern.length && random.nextInt(9) == 0) {
                return@joinToString searchPattern
            }
            when (val r = random.nextInt(100)) {
                in 0 ..< 95 -> charset[random.nextInt(0, charset.size)]
                in 96 ..< 97 -> "\n"
                else -> searchPattern
            }
        }
        createTestFile(fileContent) { file ->
            verifySearch(file, fileContent, searchPattern, blockSize)
        }
    }

    @Test
    fun unicodeAcrossMultipleBlocksNotFound() {
        val random = Random(2347)
        val searchPattern = "喂你好😄😄!"
        val blockSize = 200
        val charset = "零一二三四五六七八九ABCDabc".map { it.toString() } + listOf("😄", "😄", "😇", "🤣", "🤯", "🤬", "🫡", "🫠", "😵")
        val fileContent: String = (0 ..< 10000).joinToString { i ->
            when (val r = random.nextInt(100)) {
                in 0 ..< 96 -> charset[random.nextInt(0, charset.size)]
                else -> "\n"
            }
        }
        createTestFile(fileContent) { file ->
            val fileSize = file.length()
            verifySearch(file, fileContent, searchPattern, blockSize, searchRange = fileSize downTo fileSize - 1005)
            verifySearch(file, fileContent, searchPattern, blockSize, searchRange = fileSize * 7 / 10 + 300 downTo fileSize * 7 / 10 - 300)
            verifySearch(file, fileContent, searchPattern, blockSize, searchRange = fileSize / 2 + 600 downTo fileSize / 2 - 600)
            verifySearch(file, fileContent, searchPattern, blockSize, searchRange = fileSize / 5 + 300 downTo fileSize / 5 - 300)
            verifySearch(file, fileContent, searchPattern, blockSize, searchRange = 1200L downTo 0L)
        }
    }
}

private fun verifySearch(file: File, fileContent: String, searchPattern: String, blockSize: Int = 1 * 1024 * 1024, searchRange: LongProgression? = null) {
    val fileReader = GiantFileReader(file.absolutePath, blockSize)
    val pager = CoroutineGiantFileTextPager(
        fileReader, MonospaceBidirectionalTextLayouter(
            DivisibleWidthCharMeasurer(16f)
        )
    )
    pager.viewport = Viewport(width = 16 * 7, height = 12 * 5, density = 1f)
    val fileSize = file.length()
    val searchRegex = searchPattern.toRegex()
    (searchRange ?: (fileSize downTo 0)).forEach { i ->
        assertEquals(
            lastBytePositionOf(fileContent, searchRegex, i),
            pager.searchBackward(i, searchRegex).also {
                println("search starts at $i found $it")
            },
            "search starts at $i"
        )
    }
}

private fun lastBytePositionOf(content: String, regex: Regex, start: Long): Long {
    return regex.findAll(content).findLast {
        content.substring(0 ..< it.range.start).toByteArray(Charsets.UTF_8).size.toLong() < start
    }?.let {
        content.substring(0 ..< it.range.start).toByteArray(Charsets.UTF_8).size.toLong()
    } ?: -1L
}
