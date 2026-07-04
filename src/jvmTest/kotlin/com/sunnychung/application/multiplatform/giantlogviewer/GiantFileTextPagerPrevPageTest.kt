package com.sunnychung.application.multiplatform.giantlogviewer

import com.sunnychung.application.multiplatform.giantlogviewer.io.CoroutineGiantFileTextPager
import com.sunnychung.application.multiplatform.giantlogviewer.io.GiantFileReader
import com.sunnychung.application.multiplatform.giantlogviewer.io.GiantFileTextPager
import com.sunnychung.application.multiplatform.giantlogviewer.io.Viewport
import com.sunnychung.application.multiplatform.giantlogviewer.layout.MonospaceBidirectionalTextLayouter
import com.sunnychung.application.multiplatform.giantlogviewer.util.DivisibleWidthCharMeasurer
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import kotlin.random.Random
import kotlin.test.assertEquals

class GiantFileTextPagerPrevPageTest {

    @ParameterizedTest
    @EnumSource(TestFileEncoding::class)
    fun nextPageThenPrevPage(encoding: TestFileEncoding) {
        val random = Random(12345)
        val fileContent = (0 ..< 3000).joinToString("") {
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
        createTestFile(fileContent, encoding) { file ->
            val fileReader = GiantFileReader(file.absolutePath)
            val pager = CoroutineGiantFileTextPager(fileReader, MonospaceBidirectionalTextLayouter(DivisibleWidthCharMeasurer(16f)))
            pager.viewport = Viewport(width = 16 * 23, height = 12 * 12 + 1, density = 1f)
            val pageCharPositions = mutableListOf(0L)
            val pageBytePositions = mutableListOf(encoding.contentStartBytePosition)
            (0 ..< 5).forEach {
                pager.moveToNextPage()
                pageCharPositions += pager.viewportStartCharPosition
                pageBytePositions += pager.viewportStartBytePosition
            }
            (4 downTo 0).forEach {
                pager.moveToPrevPage()
                println("> ${pager.viewportStartBytePosition} ${pager.viewportStartCharPosition}")
//                assertEquals(pageCharPositions[it], pager.viewportStartCharPosition, "it $it")
                assertEquals(pageBytePositions[it], pager.viewportStartBytePosition, "it $it")
            }
            pager.moveToPrevPage()
//            assertEquals(0L, pager.viewportStartCharPosition)
            assertEquals(encoding.contentStartBytePosition, pager.viewportStartBytePosition)
        }
    }

    @ParameterizedTest
    @EnumSource(TestFileEncoding::class)
    fun nextRowNextPageAndPrevPage1(encoding: TestFileEncoding) {
        val fileContent = """
            01234567890123456789012
            1
            2
            3
            4
            5
            6
            7
            8
            9
            10
            11
            12
            01234567890123456789012012345678901234567890120123456789012345678901201234567890123456789012
            17jasdnfalsdjnfafsandfassdfgksdfmgksdfmgksdfmgsdfmgldsfmkgsdfkgmkmfksdmsdkfmsdogmsdfogfggsd
            21kfaksmfksakfmlsamflafmamfsdmfasdfsfakfamgsdgsfb
            24kmasgkasfmgkasfgmasfmgasflgkasfgmflsakmgkafsmgamkfosdfsmdkfmsdofmsdofmsdmfsdfosdmfokasdmfoas
            29amksfdmsdfmamsgkdfmgsdmfgklsdmfglmsdflgmskdflmgskldfmgklsdfmglksdfmgklsmdfgklsmdfkglmklsgmdflgmmsfklg
            34jfaksdfjalsflkfjalansfdjaknsfhsdghsdg
        """.trimIndent()
        createTestFile(fileContent, encoding) { file ->
            val fileReader = GiantFileReader(file.absolutePath)
            val pager = CoroutineGiantFileTextPager(fileReader, MonospaceBidirectionalTextLayouter(DivisibleWidthCharMeasurer(16f)))
            pager.viewport = Viewport(width = 16 * 23, height = 12 * 12 + 1, density = 1f)
            var iteration = 0
            pager.moveToNextPage()
            pager.moveToNextRow()
            pager.moveToNextPage()
            (25 ..< 35).forEach {
                pager.moveToNextRow()
            }
//            assertEquals(508L, pager.viewportStartCharPosition, "after next")
            assertEquals(encoding.bytePosition(fileContent, 508), pager.viewportStartBytePosition, "after next")

            pager.moveToPrevPage()
            ++iteration
//            assertEquals(282L, pager.viewportStartCharPosition, "after prev $iteration")
            assertEquals(encoding.bytePosition(fileContent, 282), pager.viewportStartBytePosition, "after prev $iteration")

            pager.moveToPrevPage()
            ++iteration
//            assertEquals(45L, pager.viewportStartCharPosition, "after prev $iteration")
            assertEquals(encoding.bytePosition(fileContent, 45), pager.viewportStartBytePosition, "after prev $iteration")

            pager.moveToNextPage()
            (23 ..< 27).forEach {
                pager.moveToNextRow()
            }
//            assertEquals(355L, pager.viewportStartCharPosition, "after next")
            assertEquals(encoding.bytePosition(fileContent, 355), pager.viewportStartBytePosition, "after next")

            pager.moveToPrevPage()
            ++iteration
//            assertEquals(97L, pager.viewportStartCharPosition, "after prev $iteration")
            assertEquals(encoding.bytePosition(fileContent, 97), pager.viewportStartBytePosition, "after prev $iteration")

            pager.moveToPrevPage()
            ++iteration
//            assertEquals(28L, pager.viewportStartCharPosition, "after prev $iteration")
            assertEquals(encoding.bytePosition(fileContent, 28), pager.viewportStartBytePosition, "after prev $iteration")

            pager.moveToPrevPage()
            ++iteration
//            assertEquals(0L, pager.viewportStartCharPosition, "after prev $iteration")
            assertEquals(encoding.contentStartBytePosition, pager.viewportStartBytePosition, "after prev $iteration")

            (0 ..< 29).forEach {
                pager.moveToNextRow()
            }
//            assertEquals(381L, pager.viewportStartCharPosition, "after next")
            assertEquals(encoding.bytePosition(fileContent, 381), pager.viewportStartBytePosition, "after next")

            pager.moveToPrevPage()
            ++iteration
//            assertEquals(144L, pager.viewportStartCharPosition, "after prev $iteration")
            assertEquals(encoding.bytePosition(fileContent, 144), pager.viewportStartBytePosition, "after prev $iteration")

            pager.moveToPrevPage()
            ++iteration
//            assertEquals(32L, pager.viewportStartCharPosition, "after prev $iteration")
            assertEquals(encoding.bytePosition(fileContent, 32), pager.viewportStartBytePosition, "after prev $iteration")

            pager.moveToPrevPage()
//            assertEquals(0L, pager.viewportStartCharPosition)
            assertEquals(encoding.contentStartBytePosition, pager.viewportStartBytePosition)
        }
    }

    @ParameterizedTest
    @EnumSource(TestFileEncoding::class)
    fun nextRowNextPageAndPrevPage2(encoding: TestFileEncoding) {
        val fileContent = """
            01234567890123456789012
            11234567890123456789012
            21234567890123456789012
            31234567890123456789012
            41234567890123456789012
            51234567890123456789012
            61234567890123456789012
            71234567890123456789012
            81234567890123456789012
            91234567890123456789012
            10234567890123456789012
            11234567890123456789012
            12234567890123456789012
            13234567890123456789012
            14234567890123456789012
            15234567890123456789012
            16234567890123456789012
            17234567890123456789012
            18234567890123456789012
            19234567890123456789012
            20234567890123456789012
            21234567890123456789012
            22234567890123456789012
            01234567890123456789012
            11234567890123456789012
            21234567890123456789012
            31234567890123456789012
            41234567890123456789012
            51234567890123456789012
            61234567890123456789012
            71234567890123456789012
            81234567890123456789012
            91234567890123456789012
            10234567890123456789012
            11234567890123456789012
            12234567890123456789012
            13234567890123456789012
            14234567890123456789012
            15234567890123456789012
            16234567890123456789012
            17234567890123456789012
            18234567890123456789012
            19234567890123456789012
            20234567890123456789012
            21234567890123456789012
            22234567890123456789012
            01234567890123456789012
            11234567890123456789012
            21234567890123456789012
            31234567890123456789012
            41234567890123456789012
            51234567890123456789012
            61234567890123456789012
            71234567890123456789012
            81234567890123456789012
            91234567890123456789012
            10234567890123456789012
            11234567890123456789012
            12234567890123456789012
            13234567890123456789012
            14234567890123456789012
            15234567890123456789012
            16234567890123456789012
            17234567890123456789012
            18234567890123456789012
            19234567890123456789012
            20234567890123456789012
            21234567890123456789012
            22234567890123456789012
            01234567890123456789012
            11234567890123456789012
            21234567890123456789012
            31234567890123456789012
            41234567890123456789012
            51234567890123456789012
            61234567890123456789012
            71234567890123456789012
            81234567890123456789012
            91234567890123456789012
            10234567890123456789012
            11234567890123456789012
            12234567890123456789012
            13234567890123456789012
            14234567890123456789012
            15234567890123456789012
            16234567890123456789012
            17234567890123456789012
            18234567890123456789012
            19234567890123456789012
            20234567890123456789012
            21234567890123456789012
            22234567890123456789012
            -
            01234567890123456789012
            11234567890123456789012
            21234567890123456789012
            31234567890123456789012
            41234567890123456789012
            51234567890123456789012
            61234567890123456789012
            71234567890123456789012
            81234567890123456789012
            91234567890123456789012
            10234567890123456789012
            11234567890123456789012
            12234567890123456789012
            13234567890123456789012
            14234567890123456789012
            15234567890123456789012
            16234567890123456789012
            17234567890123456789012
            18234567890123456789012
            19234567890123456789012
            20234567890123456789012
            21234567890123456789012
            22234567890123456789012
            01234567890123456789012
            11234567890123456789012
            21234567890123456789012
            31234567890123456789012
            41234567890123456789012
            51234567890123456789012
            61234567890123456789012
            71234567890123456789012
            81234567890123456789012
            91234567890123456789012
            10234567890123456789012
            11234567890123456789012
            12234567890123456789012
            13234567890123456789012
            14234567890123456789012
            15234567890123456789012
            16234567890123456789012
            17234567890123456789012
            18234567890123456789012
            19234567890123456789012
            20234567890123456789012
            21234567890123456789012
            22234567890123456789012
            01234567890123456789012
            1
            2
            3
            4
            5
            6
            7
            8
            9
            10
            11
            12
            01234567890123456789012012345678901234567890120123456789012345678901201234567890123456789012
            17jasdnfalsdjnfafsandfassdfgksdfmgksdfmgksdfmgsdfmgldsfmkgsdfkgmkmfksdmsdkfmsdogmsdfogfggsd
            21kfaksmfksakfmlsamflafmamfsdmfasdfsfakfamgsdgsfb
            24kmasgkasfmgkasfgmasfmgasflgkasfgmflsakmgkafsmgamkfosdfsmdkfmsdofmsdofmsdmfsdfosdmfokasdmfoas
            29amksfdmsdfmamsgkdfmgsdmfgklsdmfglmsdflgmskdflmgskldfmgklsdfmglksdfmgklsmdfgklsmdfkglmklsgmdflgmmsfklg
            34jfaksdfjalsflkfjalansfdjaknsfhsdghsdg
            36g
        """.trimIndent()
        createTestFile(fileContent, encoding) { file ->
            val fileReader = GiantFileReader(file.absolutePath)
            val pager = CoroutineGiantFileTextPager(fileReader, MonospaceBidirectionalTextLayouter(DivisibleWidthCharMeasurer(16f)))
            pager.viewport = Viewport(width = 16 * 23, height = 12 * 12 + 1, density = 1f)
            var iteration = 0
            repeat(15) {
                pager.moveToNextPage()
            }
            repeat(4) {
                pager.moveToNextRow()
            }
//            assertEquals(3839L, pager.viewportStartCharPosition, "after next")
            assertEquals(encoding.bytePosition(fileContent, 3839), pager.viewportStartBytePosition, "after next")

            pager.moveToPrevPage()
            ++iteration
//            assertEquals(3600L, pager.viewportStartCharPosition, "after prev $iteration")
            assertEquals(encoding.bytePosition(fileContent, 3600), pager.viewportStartBytePosition, "after prev $iteration")

            pager.moveToPrevPage()
            ++iteration
//            assertEquals(3362L, pager.viewportStartCharPosition, "after prev $iteration")
            assertEquals(encoding.bytePosition(fileContent, 3362), pager.viewportStartBytePosition, "after prev $iteration")

            repeat(4) {
                pager.moveToNextRow()
            }
            pager.moveToNextPage()
//            assertEquals(3692L, pager.viewportStartCharPosition, "after next")
            assertEquals(encoding.bytePosition(fileContent, 3692), pager.viewportStartBytePosition, "after next")

            pager.moveToPrevPage()
            ++iteration
//            assertEquals(3434L, pager.viewportStartCharPosition, "after prev $iteration")
            assertEquals(encoding.bytePosition(fileContent, 3434), pager.viewportStartBytePosition, "after prev $iteration")

            repeat(11) {
                pager.moveToNextRow()
            }

            pager.moveToPrevPage()
            ++iteration
//            assertEquals(3411L, pager.viewportStartCharPosition, "after prev $iteration")
            assertEquals(encoding.bytePosition(fileContent, 3411), pager.viewportStartBytePosition, "after prev $iteration")

            repeat(11) {
                pager.moveToNextRow()
            }

            pager.moveToPrevPage()
            ++iteration
//            assertEquals(3388L, pager.viewportStartCharPosition, "after prev $iteration")
            assertEquals(encoding.bytePosition(fileContent, 3388), pager.viewportStartBytePosition, "after prev $iteration")

            pager.moveToPrevPage()
            ++iteration
//            assertEquals(3340L, pager.viewportStartCharPosition, "after prev $iteration")
            assertEquals(encoding.bytePosition(fileContent, 3340), pager.viewportStartBytePosition, "after prev $iteration")

            pager.moveToPrevPage()
            ++iteration
//            assertEquals(3074L, pager.viewportStartCharPosition, "after prev $iteration")
            assertEquals(encoding.bytePosition(fileContent, 3074), pager.viewportStartBytePosition, "after prev $iteration")

            pager.moveToPrevPage()
            ++iteration
//            assertEquals(2786L, pager.viewportStartCharPosition, "after prev $iteration")
            assertEquals(encoding.bytePosition(fileContent, 2786), pager.viewportStartBytePosition, "after prev $iteration")

            pager.moveToPrevPage()
            ++iteration
//            assertEquals(2498L, pager.viewportStartCharPosition, "after prev $iteration")
            assertEquals(encoding.bytePosition(fileContent, 2498), pager.viewportStartBytePosition, "after prev $iteration")

            pager.moveToPrevPage()
            ++iteration
//            assertEquals(2210L, pager.viewportStartCharPosition, "after prev $iteration")
            assertEquals(encoding.bytePosition(fileContent, 2210), pager.viewportStartBytePosition, "after prev $iteration")

            (81 downTo 0 step 12).forEach {
                pager.moveToPrevPage()
                ++iteration
//                assertEquals(24L * it, pager.viewportStartCharPosition, "after prev $iteration")
                assertEquals(encoding.bytePosition(fileContent, 24 * it), pager.viewportStartBytePosition, "after prev $iteration")
            }

            pager.moveToPrevPage()
            ++iteration
//            assertEquals(0L, pager.viewportStartCharPosition, "after prev $iteration")
            assertEquals(encoding.contentStartBytePosition, pager.viewportStartBytePosition, "after prev $iteration")

            pager.moveToPrevPage()
//            assertEquals(0L, pager.viewportStartCharPosition)
            assertEquals(encoding.contentStartBytePosition, pager.viewportStartBytePosition)
        }
    }

    @ParameterizedTest
    @EnumSource(TestFileEncoding::class)
    fun unicode(encoding: TestFileEncoding) {
        val fileContent = """
            01234567890123456789你好👋
            01234567890123456789你好👋
            01234567890123456789你好👋
            01234567890123456789你好👋
            01234567890123456789你好👋
            01234567890123456789你好👋
            01234567890123456789你好👋
            01234567890123456789你好👋
            01234567890123456789你好👋
            01234567890123456789你好👋
            01234567890123456789你好👋
            01234567890123456789你好👋
            01234567890123456789你好👋
            01234567890123456789你好👋
            01234567890123456789你好👋
            01234567890123456789你好👋
            01234567890123456789你好👋
            01234567890123456789你好👋
            01234567890123456789你好👋
            01234567890123456789你好👋
            01234567890123456789你好👋
            01234567890123456789你好👋
            01234567890123456789你好👋
            01234567890123456789你好👋
            01234567890123456789你好👋
            01234567890123456789你好👋
            01234567890123456789你好👋
            01234567890123456789你好👋
            01234567890123456789你好👋
            01234567890123456789你好👋
            01234567890123456789你好👋
            01234567890123456789你好👋
            01234567890123456789你好👋
            01234567890123456789你好👋
            01234567890123456789你好👋
            01234567890123456789你好👋
            01234567890123456789你好👋
            01234567890123456789你好👋
            01234567890123456789你好👋
            01234567890123456789你好👋
            01234567890123456789你好👋
            01234567890123456789你好👋
            01234567890123456789你好👋
            01234567890123456789你好👋
            01234567890123456789你好👋
            01234567890123456789你好👋
            01234567890123456789你好👋
            01234567890123456789你好👋
            01234567890123456789你好👋
            01234567890123456789你好👋
            01234567890123456789你好👋
            01234567890123456789你好👋
            1中文字👍🫤🌭中文字👍🫤🌭中文字👍🫤🌭中文字👍🫤🌭中文字
            4中文字👍🫤🌭中文字👍🫤🌭中文字👍🫤🌭中文字👍🫤🌭中文字👍🫤🌭中文字👍🫤🌭中文字👍🫤🌭中文字👍🫤🌭中文字👍🫤🌭中文字👍🫤🌭中文字👍🫤🌭中文字👍🫤🌭中文字👍🫤🌭中文字👍🫤🌭中文字👍🫤🌭中文字👍🫤🌭中文字👍🫤🌭
            14中文字中文字👍🫤🌭中文字👍🫤🌭中文字👍🫤🌭中文字👍🫤🌭中文字👍🫤🌭中文字👍🫤🌭中文字👍🫤🌭中文字👍🫤🌭
            19中文字👍🫤🌭中文字👍🫤🌭中文字👍🫤🌭中文字👍🫤🌭中文字👍🫤🌭中文字👍🫤🌭中文字👍🫤🌭中文字👍🫤🌭中文字👍🫤🌭中文字
            25中文字👍🫤🌭中文字👍🫤🌭中文字👍🫤🌭中文字👍🫤🌭中文字👍🫤🌭中文字👍🫤🌭中文字👍🫤🌭中文字👍🫤🌭中文字👍🫤🌭中文字
            31中文字👍🫤🌭中文字👍🫤🌭中文字👍🫤🌭中文字👍🫤🌭中文字
            34
        """.trimIndent()
        createTestFile(fileContent, encoding) { file ->
            val fileReader = GiantFileReader(file.absolutePath)
            val pager =
                CoroutineGiantFileTextPager(fileReader, MonospaceBidirectionalTextLayouter(DivisibleWidthCharMeasurer(16f)))
            pager.viewport = Viewport(width = 16 * 23, height = 12 * 12 + 1, density = 1f)
            with(PageState(fileContent = fileContent)) {
                var iteration = 0
                repeat(126) {
                    pager.moveToNextRow()
                }
                moveToRow(126)
                // emoji 2 chars, otherwise 1 char
//                assertEquals(start.toLong() /* 1640L */, pager.viewportStartCharPosition, "after next")
                // chinese char 3 bytes, emoji 4 bytes, otherwise 1 byte
                assertEquals(startBytePos(encoding) /* 2398L */, pager.viewportStartBytePosition, "after next")

                pager.moveToPrevPage()
                ++iteration
                moveToRow(114)
//                assertEquals(start.toLong() /* 1457L */, pager.viewportStartCharPosition, "after prev $iteration")
                assertEquals(startBytePos(encoding) /* 1977L */, pager.viewportStartBytePosition, "after prev $iteration")

                pager.moveToNextRow()

                pager.moveToPrevPage()
                ++iteration
                moveToRow(103)
//                assertEquals(start.toLong() /* 1296L*/, pager.viewportStartCharPosition, "after prev $iteration")
                assertEquals(startBytePos(encoding) /* 1604L */, pager.viewportStartBytePosition, "after prev $iteration")

                repeat(30) {
                    pager.moveToNextRow()
                }

                pager.moveToPrevPage()
                ++iteration
                moveToRow(121)
//                assertEquals(start.toLong(), pager.viewportStartCharPosition, "after prev $iteration")
                assertEquals(startBytePos(encoding), pager.viewportStartBytePosition, "after prev $iteration")

                pager.moveToPrevPage()
                ++iteration
                moveToRow(109)
//                assertEquals(start.toLong(), pager.viewportStartCharPosition, "after prev $iteration")
                assertEquals(startBytePos(encoding), pager.viewportStartBytePosition, "after prev $iteration")

                pager.moveToPrevPage()
                ++iteration
                moveToRow(97)
//                assertEquals(start.toLong(), pager.viewportStartCharPosition, "after prev $iteration")
                assertEquals(startBytePos(encoding), pager.viewportStartBytePosition, "after prev $iteration")

                (97 ..< 131).forEach {
                    pager.moveToNextRow()
                }

                pager.moveToPrevPage()
                ++iteration
                moveToRow(119)
//                assertEquals(start.toLong(), pager.viewportStartCharPosition, "after prev $iteration")
                assertEquals(startBytePos(encoding), pager.viewportStartBytePosition, "after prev $iteration")

                pager.moveToPrevPage()
                ++iteration
                moveToRow(107)
//                assertEquals(start.toLong(), pager.viewportStartCharPosition, "after prev $iteration")
                assertEquals(startBytePos(encoding), pager.viewportStartBytePosition, "after prev $iteration")

                (95 downTo 0 step 12).forEach {
                    pager.moveToPrevPage()
                    ++iteration
                    moveToRow(it)
//                    assertEquals(start.toLong(), pager.viewportStartCharPosition, "after prev $iteration")
                    assertEquals(startBytePos(encoding), pager.viewportStartBytePosition, "after prev $iteration")
                }

                pager.moveToPrevPage()
                ++iteration
//                assertEquals(0L, pager.viewportStartCharPosition, "after prev $iteration")
                assertEquals(encoding.contentStartBytePosition, pager.viewportStartBytePosition, "after prev $iteration")

                pager.moveToPrevPage()
//                assertEquals(0L, pager.viewportStartCharPosition)
                assertEquals(encoding.contentStartBytePosition, pager.viewportStartBytePosition)
            }
        }
    }
}

internal fun PageState.moveToRow(row: Int) {
    start = 0
    repeat(row) {
        updatePageState(this)
        start = firstRowBreakPos
    }
    updatePageState(this)
}

internal fun PageState.startBytePos(encoding: TestFileEncoding): Long {
    return encoding.bytePosition(fileContent, start)
}
