package com.sunnychung.application.multiplatform.giantlogviewer

import com.sunnychung.application.multiplatform.giantlogviewer.io.GiantFileReader
import org.junit.jupiter.api.Assertions.assertEquals
import java.io.File
import java.util.UUID
import kotlin.random.Random
import kotlin.test.Test

class GiantFileReaderTest {

    @Test
    fun readAsciiFile() {
        val random = Random(13579)
        val fileContent = (0 ..< 9999).joinToString("") {
            val newLineFactor = if (random.nextInt(13) == 0) {
                8
            } else {
                131
            }
            if (random.nextInt(newLineFactor) == 0) {
                return@joinToString "\n"
            }
            ('A'.code + (random.nextInt(26))).toChar().toString()
        }
        createTestFile(fileContent) { file ->
            GiantFileReader(file.absolutePath, 1024).use { reader ->
                val readSize = 96
                (0..fileContent.length / readSize).forEach {
                    val readFrom = it * readSize
                    val readUntil = ((it + 1) * readSize).coerceAtMost(fileContent.length)
                    val expectedContent = fileContent.substring(readFrom ..< readUntil)
                    val (readContent, readBytesRange) = reader.readString(readFrom.toLong(), readUntil - readFrom)
                    assertEquals(expectedContent, readContent) {
                        "Mismatch content at $readFrom"
                    }
                    assertEquals(readFrom.toLong()..<readUntil.toLong(), readBytesRange) {
                        "Mismatch range at $readFrom"
                    }
                }
            }
        }
    }

    @Test
    fun readMultibyteUnicode() {
        val testCases = listOf(
            "一我",
            "一我a",
            "一我ab",
            "一我abc",
            "a一我",
            "ab一我",
            "abc一我",
            "一a我",
            "一ab我",
            "一abc我",
            "a一a我",
            "a一ab我",
            "a一abc我",
            "a一a我a",
            "a一a我ab",
            "a一a我abc",
            "a一ab我a",
            "a一ab我ab",
            "a一ab我abc",
            "a一abc我a",
            "a一abc我ab",
            "a一abc我abc",
            "ab一a我",
            "ab一ab我",
            "ab一abc我",
            "ab一a我a",
            "ab一a我ab",
            "ab一a我abc",
            "ab一ab我a",
            "ab一ab我ab",
            "ab一ab我abc",
            "ab一abc我a",
            "ab一abc我ab",
            "ab一abc我abc",
            "abc一a我",
            "abc一ab我",
            "abc一abc我",
            "abc一a我a",
            "abc一a我ab",
            "abc一a我abc",
            "abc一ab我a",
            "abc一ab我ab",
            "abc一ab我abc",
            "abc一abc我a",
            "abc一abc我ab",
            "abc一abc我abc",
        )
        testCases.forEach { testCase ->
            val fullBytes = testCase.toByteArray(Charsets.UTF_8)
            createTestFile(testCase) { file ->
                GiantFileReader(file.absolutePath, 10240).use { reader ->
                    listOf(10000, fullBytes.size).forEach { readLength ->
                        val (readContent, readBytesRange) = reader.readString(0L, readLength)
                        assertEquals(testCase, readContent)
                        assertEquals(0L ..< fullBytes.size, readBytesRange)
                    }
                }
            }
        }
    }

    @Test
    fun readUnicodeSubstringAmongBlockBoundaries() {
        val testCases = listOf(
            TestCase( // the first 3-byte unicode char is split into 1 byte and 2 bytes
                fileContent = "1234567一我abcd",
                blockSize = 8,
                queries = listOf(
                    TestCase.Query(6..13, "7一我a"),
                    TestCase.Query(7..13, "一我a"),
                    TestCase.Query(8..13, "一我a"),
                    TestCase.Query(9..13, "一我a"),
                    TestCase.Query(10..13, "我a"),
                    TestCase.Query(11..13, "我a"),
                    TestCase.Query(12..13, "我a"),
                    TestCase.Query(13..13, "a"),
                    TestCase.Query(12..12, "我"),
                    TestCase.Query(11..11, "我"),
                    TestCase.Query(10..10, "我"),
                    TestCase.Query(6..12, "7一我"),
                    TestCase.Query(6..11, "7一我"),
                    TestCase.Query(6..10, "7一我"),
                    TestCase.Query(6..9, "7一"),
                    TestCase.Query(6..8, "7一"),
                    TestCase.Query(6..7, "7一"),
                    TestCase.Query(6..6, "7"),
                    TestCase.Query(7..7, "一"),
                    TestCase.Query(8..8, "一"),
                    TestCase.Query(9..9, "一"),
                    TestCase.Query(7..8, "一"),
                    TestCase.Query(7..9, "一"),
                    TestCase.Query(7..10, "一我"),
                    TestCase.Query(7..11, "一我"),
                    TestCase.Query(7..12, "一我"),
                    TestCase.Query(8..11, "一我"),
                    TestCase.Query(9..10, "一我"),
                    TestCase.Query(13..16, "abcd"),
                    TestCase.Query(14..20, "bcd"),
                )
            ),
            TestCase( // the last 3-byte unicode char is split into 2 bytes and 1 byte
                fileContent = "12345678123一我abcd1234",
                blockSize = 8,
                queries = listOf(
                    TestCase.Query(16..21, "我abcd1"),
                    TestCase.Query(15..21, "我abcd1"),
                    TestCase.Query(14..21, "我abcd1"),
                    TestCase.Query(14..17, "我a"),
                    TestCase.Query(13..17, "一我a"),
                    TestCase.Query(12..17, "一我a"),
                    TestCase.Query(11..17, "一我a"),
                    TestCase.Query(10..17, "3一我a"),
                    TestCase.Query(10..16, "3一我"),
                    TestCase.Query(10..15, "3一我"),
                    TestCase.Query(10..14, "3一我"),
                    TestCase.Query(10..13, "3一"),
                    TestCase.Query(10..12, "3一"),
                    TestCase.Query(10..11, "3一"),
                    TestCase.Query(11..11, "一"),
                    TestCase.Query(12..12, "一"),
                    TestCase.Query(13..13, "一"),
                    TestCase.Query(14..14, "我"),
                    TestCase.Query(15..15, "我"),
                    TestCase.Query(16..16, "我"),
                )
            ),
            TestCase( // long unicode
                fileContent = "我是一位香港人。",
                blockSize = 8,
                queries = listOf(
                    TestCase.Query(0..7, "我是一"),
                    TestCase.Query(8..15, "一位香港"),
                    TestCase.Query(16..23, "港人。"),
                    TestCase.Query(20..27, "人。"),
                    TestCase.Query(1..7, "我是一"),
                    TestCase.Query(2..7, "我是一"),
                    TestCase.Query(0..5, "我是"),
                    TestCase.Query(6..11, "一位"),
                    TestCase.Query(12..17, "香港"),
                    TestCase.Query(12..18, "香港人"),
                    TestCase.Query(12..19, "香港人"),
                    TestCase.Query(13..20, "香港人"),
                    TestCase.Query(14..20, "香港人"),
                    TestCase.Query(15..20, "港人"),
                    TestCase.Query(21..21, "。"),
                    TestCase.Query(22..22, "。"),
                    TestCase.Query(23..23, "。"),
                )
            ),
            TestCase( // long unicode starts between boundaries
                fileContent = "Umm我是一位香港人。",
                blockSize = 8,
                queries = listOf(
                    TestCase.Query(0..7, "Umm我是"),
                    TestCase.Query(8..15, "是一位香"),
                    TestCase.Query(16..23, "香港人"),
                    TestCase.Query(24..26, "。"),
                    TestCase.Query(24..30, "。"),
                    TestCase.Query(21..28, "人。"),
                    TestCase.Query(21..22, "人"),
                    TestCase.Query(18..23, "港人"),
                    TestCase.Query(15..18, "香港"),
                    TestCase.Query(16..18, "香港"),
                    TestCase.Query(17..18, "香港"),
                    TestCase.Query(24..24, "。"),
                    TestCase.Query(25..25, "。"),
                    TestCase.Query(3..3, "我"),
                    TestCase.Query(4..4, "我"),
                    TestCase.Query(5..5, "我"),
                    TestCase.Query(25..30, "。"),
                    TestCase.Query(26..30, "。"),
                )
            ),
            TestCase( // an emoji char here has 4-byte; another emoji is split into 2-2 bytes
                fileContent = "🌗月有陰晴圓缺🌒",
                blockSize = 8,
                queries = listOf(
                    TestCase.Query(0..7, "🌗月有"),
                    TestCase.Query(8..15, "有陰晴"),
                    TestCase.Query(16..23, "圓缺🌒"),
                    TestCase.Query(24..25, "🌒"),

                    TestCase.Query(1..7, "🌗月有"),
                    TestCase.Query(2..7, "🌗月有"),
                    TestCase.Query(3..7, "🌗月有"),
                    TestCase.Query(4..7, "月有"),
                    TestCase.Query(18..25, "圓缺🌒"),
                    TestCase.Query(18..24, "圓缺🌒"),
                    TestCase.Query(18..23, "圓缺🌒"),
                    TestCase.Query(18..22, "圓缺🌒"),
                    TestCase.Query(18..22, "圓缺🌒"),
                    TestCase.Query(18..21, "圓缺"),
                    TestCase.Query(18..20, "圓缺"),
                    TestCase.Query(18..19, "圓缺"),
                    TestCase.Query(18..18, "圓"),
                    TestCase.Query(2..2, "🌗"),
                    TestCase.Query(3..3, "🌗"),
                    TestCase.Query(1..1, "🌗"),
                    TestCase.Query(0..0, "🌗"),
                    TestCase.Query(22..22, "🌒"),
                    TestCase.Query(24..24, "🌒"),
                    TestCase.Query(25..25, "🌒"),
                    TestCase.Query(23..23, "🌒"),
                    TestCase.Query(25..30, "🌒"),
                    TestCase.Query(26..30, ""),
                )
            ),
            TestCase( // an emoji char is split into 1-3 bytes
                fileContent = "1234567🌗月有陰晴圓缺🌒",
                blockSize = 8,
                queries = listOf(
                    TestCase.Query(7..14, "🌗月有"),
                    TestCase.Query(15..22, "有陰晴"),
                    TestCase.Query(23..30, "圓缺🌒"),
                    TestCase.Query(31..32, "🌒"),
                    TestCase.Query(8..14, "🌗月有"),
                    TestCase.Query(9..14, "🌗月有"),
                    TestCase.Query(10..14, "🌗月有"),
                    TestCase.Query(6..6, "7"),
                    TestCase.Query(7..7, "🌗"),
                    TestCase.Query(8..8, "🌗"),
                    TestCase.Query(9..9, "🌗"),
                    TestCase.Query(10..10, "🌗"),
                    TestCase.Query(10..11, "🌗月"),
                    TestCase.Query(9..12, "🌗月"),
                    TestCase.Query(8..13, "🌗月"),
                    TestCase.Query(7..13, "🌗月"),
                    TestCase.Query(7..12, "🌗月"),
                    TestCase.Query(7..11, "🌗月"),
                    TestCase.Query(7..10, "🌗"),
                    TestCase.Query(29..36, "🌒"),
                    TestCase.Query(32..39, "🌒"),
                    TestCase.Query(33..39, ""),
                )
            ),
            TestCase( // an emoji char is split into 3-1 bytes
                fileContent = "12345🌗月有陰晴圓缺🌒",
                blockSize = 8,
                queries = listOf(
                    TestCase.Query(5..12, "🌗月有"),
                    TestCase.Query(13..20, "有陰晴"),
                    TestCase.Query(21..28, "圓缺🌒"),
                    TestCase.Query(29..30, "🌒"),
                    TestCase.Query(6..12, "🌗月有"),
                    TestCase.Query(7..12, "🌗月有"),
                    TestCase.Query(8..12, "🌗月有"),
                    TestCase.Query(4..4, "5"),
                    TestCase.Query(5..5, "🌗"),
                    TestCase.Query(6..6, "🌗"),
                    TestCase.Query(7..7, "🌗"),
                    TestCase.Query(8..8, "🌗"),
                    TestCase.Query(8..9, "🌗月"),
                    TestCase.Query(7..10, "🌗月"),
                    TestCase.Query(6..11, "🌗月"),
                    TestCase.Query(5..11, "🌗月"),
                    TestCase.Query(5..10, "🌗月"),
                    TestCase.Query(5..9, "🌗月"),
                    TestCase.Query(5..8, "🌗"),
                    TestCase.Query(27..34, "🌒"),
                    TestCase.Query(30..37, "🌒"),
                    TestCase.Query(31..37, ""),
                )
            ),
        )
        testCases.forEachIndexed { testCaseIndex, testCase ->
            createTestFile(testCase.fileContent) { file ->
                GiantFileReader(file.absolutePath, testCase.blockSize).use { reader ->
                    testCase.queries.forEachIndexed { queryIndex, query ->
                        try {
                            val (readContent, readBytesRange) = reader.readString(
                                query.bytePositions.start,
                                (query.bytePositions.endExclusive - query.bytePositions.start).toInt()
                            )
                            assertEquals(query.expectedResult, readContent) {
                                "Test Case #$testCaseIndex Query #$queryIndex"
                            }
                            val expectedRangeStart = testCase.fileContent.substring(0, testCase.fileContent.indexOf(query.expectedResult))
                                .toByteArray(Charsets.UTF_8).size.toLong()
                            val expectedRange = expectedRangeStart ..< expectedRangeStart + query.expectedResult.toByteArray(Charsets.UTF_8).size.toLong()
                            assertEquals(expectedRange, readBytesRange) {
                                "Test Case #$testCaseIndex Query #$queryIndex"
                            }
                        } catch (e: Throwable) {
                            throw Exception("Test Case #$testCaseIndex Query #$queryIndex throws an exception", e)
                        }
                    }
                }
            }
        }
    }
}

fun createTestFile(content: String, testBlock: (File) -> Unit) {
    val file = File("build/test", "${UUID.randomUUID()}.txt")
    file.parentFile.mkdirs()
    file.writeText(content, Charsets.UTF_8)
    try {
        testBlock(file)
    } finally {
        file.delete()
    }
}

private data class TestCase(val fileContent: String, val blockSize: Int, val queries: List<Query>) {
    data class Query(val bytePositions: LongRange, val expectedResult: String) {
        constructor(bytePositions: IntRange, expectedResult: String) : this(bytePositions.first.toLong() .. bytePositions.last.toLong(), expectedResult)
    }
}
