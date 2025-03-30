package com.sunnychung.application.multiplatform.giantlogviewer

import com.sunnychung.application.multiplatform.giantlogviewer.io.GiantFileReader
import com.sunnychung.application.multiplatform.giantlogviewer.io.GiantFileTextPager
import com.sunnychung.application.multiplatform.giantlogviewer.io.Viewport
import com.sunnychung.application.multiplatform.giantlogviewer.layout.MonospaceBidirectionalTextLayouter
import com.sunnychung.application.multiplatform.giantlogviewer.util.DivisibleWidthCharMeasurer
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class GiantFileTextPagerPrevPageTest {

    @Test
    fun nextPageThenPrevPage() {
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
        createTestFile(fileContent) { file ->
            val fileReader = GiantFileReader(file.absolutePath)
            val pager = GiantFileTextPager(fileReader, MonospaceBidirectionalTextLayouter(DivisibleWidthCharMeasurer(16f)))
            pager.viewport = Viewport(width = 16 * 23, height = 12 * 12 + 1, density = 1f)
            val pageCharPositions = mutableListOf(0L)
            val pageBytePositions = mutableListOf(0L)
            (0 ..< 5).forEach {
                pager.moveToNextPage()
                pageCharPositions += pager.viewportStartCharPosition
                pageBytePositions += pager.viewportStartBytePosition
            }
            (4 downTo 0).forEach {
                pager.moveToPrevPage()
                println("> ${pager.viewportStartBytePosition} ${pager.viewportStartCharPosition}")
                assertEquals(pageCharPositions[it], pager.viewportStartCharPosition, "it $it")
                assertEquals(pageBytePositions[it], pager.viewportStartBytePosition, "it $it")
            }
            pager.moveToPrevPage()
            assertEquals(0L, pager.viewportStartCharPosition)
            assertEquals(0L, pager.viewportStartBytePosition)
        }
    }

    @Test
    fun nextLineNextPageAndPrevPage1() {
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
        createTestFile(fileContent) { file ->
            val fileReader = GiantFileReader(file.absolutePath)
            val pager = GiantFileTextPager(fileReader, MonospaceBidirectionalTextLayouter(DivisibleWidthCharMeasurer(16f)))
            pager.viewport = Viewport(width = 16 * 23, height = 12 * 12 + 1, density = 1f)
            var iteration = 0
            pager.moveToNextPage()
            pager.moveToNextLine()
            pager.moveToNextPage()
            (25 ..< 35).forEach {
                pager.moveToNextLine()
            }
            assertEquals(508L, pager.viewportStartCharPosition, "after next")
            assertEquals(508L, pager.viewportStartBytePosition, "after next")

            pager.moveToPrevPage()
            ++iteration
            assertEquals(282L, pager.viewportStartCharPosition, "after prev $iteration")
            assertEquals(282L, pager.viewportStartBytePosition, "after prev $iteration")

            pager.moveToPrevPage()
            ++iteration
            assertEquals(45L, pager.viewportStartCharPosition, "after prev $iteration")
            assertEquals(45L, pager.viewportStartBytePosition, "after prev $iteration")

            pager.moveToNextPage()
            (23 ..< 27).forEach {
                pager.moveToNextLine()
            }
            assertEquals(355L, pager.viewportStartCharPosition, "after next")
            assertEquals(355L, pager.viewportStartBytePosition, "after next")

            pager.moveToPrevPage()
            ++iteration
            assertEquals(97L, pager.viewportStartCharPosition, "after prev $iteration")
            assertEquals(97L, pager.viewportStartBytePosition, "after prev $iteration")

            pager.moveToPrevPage()
            ++iteration
            assertEquals(28L, pager.viewportStartCharPosition, "after prev $iteration")
            assertEquals(28L, pager.viewportStartBytePosition, "after prev $iteration")

            pager.moveToPrevPage()
            ++iteration
            assertEquals(0L, pager.viewportStartCharPosition, "after prev $iteration")
            assertEquals(0L, pager.viewportStartBytePosition, "after prev $iteration")

            (0 ..< 29).forEach {
                pager.moveToNextLine()
            }
            assertEquals(381L, pager.viewportStartCharPosition, "after next")
            assertEquals(381L, pager.viewportStartBytePosition, "after next")

            pager.moveToPrevPage()
            ++iteration
            assertEquals(144L, pager.viewportStartCharPosition, "after prev $iteration")
            assertEquals(144L, pager.viewportStartBytePosition, "after prev $iteration")

            pager.moveToPrevPage()
            ++iteration
            assertEquals(32L, pager.viewportStartCharPosition, "after prev $iteration")
            assertEquals(32L, pager.viewportStartBytePosition, "after prev $iteration")

            pager.moveToPrevPage()
            assertEquals(0L, pager.viewportStartCharPosition)
            assertEquals(0L, pager.viewportStartBytePosition)
        }
    }

    @Test
    fun nextLineNextPageAndPrevPage2() {
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
        createTestFile(fileContent) { file ->
            val fileReader = GiantFileReader(file.absolutePath)
            val pager = GiantFileTextPager(fileReader, MonospaceBidirectionalTextLayouter(DivisibleWidthCharMeasurer(16f)))
            pager.viewport = Viewport(width = 16 * 23, height = 12 * 12 + 1, density = 1f)
            var iteration = 0
            repeat(15) {
                pager.moveToNextPage()
            }
            repeat(4) {
                pager.moveToNextLine()
            }
            assertEquals(3839L, pager.viewportStartCharPosition, "after next")
            assertEquals(3839L, pager.viewportStartBytePosition, "after next")

            pager.moveToPrevPage()
            ++iteration
            assertEquals(3600L, pager.viewportStartCharPosition, "after prev $iteration")
            assertEquals(3600L, pager.viewportStartBytePosition, "after prev $iteration")

            pager.moveToPrevPage()
            ++iteration
            assertEquals(3362L, pager.viewportStartCharPosition, "after prev $iteration")
            assertEquals(3362L, pager.viewportStartBytePosition, "after prev $iteration")

            repeat(4) {
                pager.moveToNextLine()
            }
            pager.moveToNextPage()
            assertEquals(3692L, pager.viewportStartCharPosition, "after next")
            assertEquals(3692L, pager.viewportStartBytePosition, "after next")

            pager.moveToPrevPage()
            ++iteration
            assertEquals(3434L, pager.viewportStartCharPosition, "after prev $iteration")
            assertEquals(3434L, pager.viewportStartBytePosition, "after prev $iteration")

            repeat(11) {
                pager.moveToNextLine()
            }

            pager.moveToPrevPage()
            ++iteration
            assertEquals(3411L, pager.viewportStartCharPosition, "after prev $iteration")
            assertEquals(3411L, pager.viewportStartBytePosition, "after prev $iteration")

            repeat(11) {
                pager.moveToNextLine()
            }

            pager.moveToPrevPage()
            ++iteration
            assertEquals(3388L, pager.viewportStartCharPosition, "after prev $iteration")
            assertEquals(3388L, pager.viewportStartBytePosition, "after prev $iteration")

            pager.moveToPrevPage()
            ++iteration
            assertEquals(3340L, pager.viewportStartCharPosition, "after prev $iteration")
            assertEquals(3340L, pager.viewportStartBytePosition, "after prev $iteration")

            pager.moveToPrevPage()
            ++iteration
            assertEquals(3074L, pager.viewportStartCharPosition, "after prev $iteration")
            assertEquals(3074L, pager.viewportStartBytePosition, "after prev $iteration")

            pager.moveToPrevPage()
            ++iteration
            assertEquals(2786L, pager.viewportStartCharPosition, "after prev $iteration")
            assertEquals(2786L, pager.viewportStartBytePosition, "after prev $iteration")

            pager.moveToPrevPage()
            ++iteration
            assertEquals(2498L, pager.viewportStartCharPosition, "after prev $iteration")
            assertEquals(2498L, pager.viewportStartBytePosition, "after prev $iteration")

            pager.moveToPrevPage()
            ++iteration
            assertEquals(2210L, pager.viewportStartCharPosition, "after prev $iteration")
            assertEquals(2210L, pager.viewportStartBytePosition, "after prev $iteration")

            (81 downTo 0 step 12).forEach {
                pager.moveToPrevPage()
                ++iteration
                assertEquals(24L * it, pager.viewportStartCharPosition, "after prev $iteration")
                assertEquals(24L * it, pager.viewportStartBytePosition, "after prev $iteration")
            }

            pager.moveToPrevPage()
            ++iteration
            assertEquals(0L, pager.viewportStartCharPosition, "after prev $iteration")
            assertEquals(0L, pager.viewportStartBytePosition, "after prev $iteration")

            pager.moveToPrevPage()
            assertEquals(0L, pager.viewportStartCharPosition)
            assertEquals(0L, pager.viewportStartBytePosition)
        }
    }

    @Test
    fun unicode() {
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
        createTestFile(fileContent) { file ->
            val fileReader = GiantFileReader(file.absolutePath)
            val pager =
                GiantFileTextPager(fileReader, MonospaceBidirectionalTextLayouter(DivisibleWidthCharMeasurer(16f)))
            pager.viewport = Viewport(width = 16 * 23, height = 12 * 12 + 1, density = 1f)
            with(PageState(fileContent = fileContent)) {
                var iteration = 0
                repeat(126) {
                    pager.moveToNextLine()
                }
                moveToRow(126)
                // emoji 2 chars, otherwise 1 char
                assertEquals(start.toLong() /* 1640L */, pager.viewportStartCharPosition, "after next")
                // chinese char 3 bytes, emoji 4 bytes, otherwise 1 byte
                assertEquals(startBytePos() /* 2398L */, pager.viewportStartBytePosition, "after next")

                pager.moveToPrevPage()
                ++iteration
                moveToRow(114)
                assertEquals(start.toLong() /* 1457L */, pager.viewportStartCharPosition, "after prev $iteration")
                assertEquals(startBytePos() /* 1977L */, pager.viewportStartBytePosition, "after prev $iteration")

                pager.moveToNextLine()

                pager.moveToPrevPage()
                ++iteration
                moveToRow(103)
                assertEquals(start.toLong() /* 1296L*/, pager.viewportStartCharPosition, "after prev $iteration")
                assertEquals(startBytePos() /* 1604L */, pager.viewportStartBytePosition, "after prev $iteration")

                repeat(30) {
                    pager.moveToNextLine()
                }

                pager.moveToPrevPage()
                ++iteration
                moveToRow(121)
                assertEquals(start.toLong(), pager.viewportStartCharPosition, "after prev $iteration")
                assertEquals(startBytePos(), pager.viewportStartBytePosition, "after prev $iteration")

                pager.moveToPrevPage()
                ++iteration
                moveToRow(109)
                assertEquals(start.toLong(), pager.viewportStartCharPosition, "after prev $iteration")
                assertEquals(startBytePos(), pager.viewportStartBytePosition, "after prev $iteration")

                pager.moveToPrevPage()
                ++iteration
                moveToRow(97)
                assertEquals(start.toLong(), pager.viewportStartCharPosition, "after prev $iteration")
                assertEquals(startBytePos(), pager.viewportStartBytePosition, "after prev $iteration")

                (97 ..< 131).forEach {
                    pager.moveToNextLine()
                }

                pager.moveToPrevPage()
                ++iteration
                moveToRow(119)
                assertEquals(start.toLong(), pager.viewportStartCharPosition, "after prev $iteration")
                assertEquals(startBytePos(), pager.viewportStartBytePosition, "after prev $iteration")

                pager.moveToPrevPage()
                ++iteration
                moveToRow(107)
                assertEquals(start.toLong(), pager.viewportStartCharPosition, "after prev $iteration")
                assertEquals(startBytePos(), pager.viewportStartBytePosition, "after prev $iteration")

                (95 downTo 0 step 12).forEach {
                    pager.moveToPrevPage()
                    ++iteration
                    moveToRow(it)
                    assertEquals(start.toLong(), pager.viewportStartCharPosition, "after prev $iteration")
                    assertEquals(startBytePos(), pager.viewportStartBytePosition, "after prev $iteration")
                }

                pager.moveToPrevPage()
                ++iteration
                assertEquals(0L, pager.viewportStartCharPosition, "after prev $iteration")
                assertEquals(0L, pager.viewportStartBytePosition, "after prev $iteration")

                pager.moveToPrevPage()
                assertEquals(0L, pager.viewportStartCharPosition)
                assertEquals(0L, pager.viewportStartBytePosition)
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

internal fun PageState.startBytePos(): Long {
    return fileContent.substring(0 ..< start).toByteArray(Charsets.UTF_8).size.toLong()
}
