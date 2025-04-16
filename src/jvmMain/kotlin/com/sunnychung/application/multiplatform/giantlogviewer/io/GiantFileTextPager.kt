package com.sunnychung.application.multiplatform.giantlogviewer.io

import com.sunnychung.application.multiplatform.giantlogviewer.layout.BidirectionalTextLayouter
import com.sunnychung.lib.multiplatform.bigtext.annotation.TemporaryBigTextApi
import com.sunnychung.lib.multiplatform.bigtext.compose.ComposeUnicodeCharMeasurer
import com.sunnychung.lib.multiplatform.bigtext.extension.runIf
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

val lineSeparatorRegex = "\r?\n".toRegex()

abstract class GiantFileTextPager(val fileReader: GiantFileReader, val textLayouter: BidirectionalTextLayouter) {

    protected val lock = ReentrantReadWriteLock()

    @Deprecated("No longer maintained as no fast way to determine the value when the cursor is moved to EOF")
    var viewportStartCharPosition: Long = 0L
        get() = lock.read { field }
        set(value) {
            lock.write {
                field = value
                rebuildCacheIfInvalid()
            }
        }

    var viewportStartBytePosition: Long = 0L
        get() = lock.read { field }
        set(value) {
            lock.write {
                field = value
                rebuildCacheIfInvalid()
            }
        }

    /**
     * Pair of width and height.
     */
    var viewport: Viewport = Viewport(0, 0, 1f)
        get() = lock.read { field }
        set(value) {
            lock.write {
                val rowHeight = rowHeight()
                numOfRowsInViewport = ceil(value.height / rowHeight).roundToInt()
                field = value
                rebuildCacheIfInvalid()
            }
        }

    val isSoftWrapEnabled: Boolean = true

    private var numOfRowsInViewport = 0

    abstract var textInViewport: List<CharSequence>
        protected set

    abstract var startBytePositionsInViewport: List<Long>
        protected set

    private var viewportCacheKey: ViewportCacheKey = ViewportCacheKey(viewport, viewportStartBytePosition)

    private fun isViewportCacheInvalid(): Boolean {
        return lock.read {
            viewportCacheKey != ViewportCacheKey(viewport, viewportStartBytePosition)
        }
    }

    @OptIn(TemporaryBigTextApi::class)
    fun rowHeight(): Float {
        // TODO avoid this hardcode
        return when (val charMeasurer = textLayouter.charMeasurer) {
            is ComposeUnicodeCharMeasurer -> charMeasurer.getRowHeight()
//            is ComposeUnicodeCharMeasurer2 -> charMeasurer.getRowHeight()
            else -> 12f
        }
    }

    private fun maxNumOfCharInARow(): Int {
//        val charMeasurer = textLayouter.charMeasurer
        return ceil(viewport.width / textLayouter.measureCharWidth("I")).roundToInt()
    }

    private fun rebuildCacheIfInvalid() {
        if (isViewportCacheInvalid()) {
            lock.write {
                if (!isViewportCacheInvalid()) {
                    return
                }
                val maxNumOfCharInARow = maxNumOfCharInARow()
                val maxNumOfCharInViewport = maxNumOfCharInARow * numOfRowsInViewport
                val (viewportText, rowBytePositions) = if (maxNumOfCharInViewport > 0) {
                    val (manyText, manyTextByteRange) = fileReader.readString(viewportStartBytePosition, maxNumOfCharInViewport * 4 + 3 /* UTF-8 tail */)
                    val lineSeparators = lineSeparatorRegex.findAll(manyText).map { it.groups[0]!!.range }
                    val rows: MutableList<String> = ArrayList(lineSeparators.count() + 1)
                    val rowBytePositions: MutableList<Long> = ArrayList(lineSeparators.count() + 2)
                    var lastStart = 0
//                    var lastNewLineByteLength = 0
                    rowBytePositions += manyTextByteRange.start

                    fun layoutLines(line: String) {
                        if (line.isEmpty()) { // an empty line is still a line to be displayed
                            rows += ""
                            rowBytePositions += rowBytePositions.last()
                            return
                        }

                        val (rowStarts, lastRowWidth) = textLayouter.layoutOneLine(line, viewport.width.toFloat(), 0f, 0)
                        var lastRowStart = 0
                        rowStarts.forEach {
                            val rowString = line.substring(lastRowStart ..< it)
                            rows += rowString
                            // adding the NEXT row start position to `rowBytePositions`
                            rowBytePositions += rowBytePositions.last() + /*lastNewLineByteLength +*/ rowString.toByteArray(Charsets.UTF_8).size
                            lastRowStart = it
//                            lastNewLineByteLength = 0 // consumed
                        }
                        if (lastRowStart < line.length) {
                            val rowString = line.substring(lastRowStart)
                            rows += rowString
                            // adding the NEXT line start position to `rowBytePositions`
                            rowBytePositions += rowBytePositions.last() + /*lastNewLineByteLength +*/ rowString.toByteArray(Charsets.UTF_8).size
//                            lastNewLineByteLength = 0 // consumed
                        }
                    }

                    lineSeparators.forEach {
                        val currentLineEndExclusive = it.first
                        val nextLineStart = it.endExclusive
                        layoutLines(manyText.substring(lastStart ..< currentLineEndExclusive))
                        lastStart = nextLineStart
                        rowBytePositions[rowBytePositions.lastIndex] += manyText.substring(it).toByteArray(Charsets.UTF_8).size.toLong()
                    }
                    if (lastStart < manyText.length) {
                        layoutLines(manyText.substring(lastStart ..< manyText.length))
                    }
                    rows to rowBytePositions
                } else {
                    emptyList<String>() to emptyList<Long>()
                }
                textInViewport = viewportText
                startBytePositionsInViewport = rowBytePositions
                viewportCacheKey = ViewportCacheKey(viewport, viewportStartBytePosition)
                println("rebuilt text cache")
            }
        }
    }

    fun moveToNextRow() {
        lock.write {
            val numOfRowsInViewport = numOfRowsInViewport
            if (numOfRowsInViewport == 0) {
                return
            }
            val maxNumOfCharInARow = maxNumOfCharInARow()
            val maxNumOfCharInViewport = maxNumOfCharInARow * numOfRowsInViewport
            val (manyText, byteRange) = fileReader.readString(viewportStartBytePosition, maxNumOfCharInViewport * 4 + 3 /* UTF-8 tail */)
            val firstLineSeparator = lineSeparatorRegex.find(manyText)?.range
            val nextRowStart = if (firstLineSeparator != null) {
                // the next row is the next line
                val firstLine = manyText.subSequence(0 ..< firstLineSeparator.start)
                val (rowStarts, lastRowWidth) = textLayouter.layoutOneLine(firstLine, viewport.width.toFloat(), 0f, 0)
                rowStarts.getOrNull(0)?.let {
                    if (it >= firstLine.length) {
                        firstLineSeparator.endExclusive
                    } else {
                        it
                    }
                } ?: firstLineSeparator.endExclusive
            } else {
                // the next row is still the current line or does not exist (end of file)
                // FIXME what if `manyText` starts with the end part of a previous line?
                val (rowStarts, lastRowWidth) = textLayouter.layoutOneLine(manyText, viewport.width.toFloat(), 0f, 0)
                rowStarts.getOrNull(0)
            }
            if (nextRowStart != null) {
                viewportStartBytePosition = byteRange.start +
                        manyText.substring(0 ..< nextRowStart).toByteArray(Charsets.UTF_8).size
                viewportStartCharPosition += nextRowStart /*-
                        manyText.subSequence(0 ..< nextRowStart)
                            .count { it.isLowSurrogate() }*/
                rebuildCacheIfInvalid()
            }
        }
    }

    fun moveToNextPage() {
        lock.write {
            val numOfRowsInViewport = floor(viewport.height / rowHeight()).roundToInt()
            if (numOfRowsInViewport == 0) {
                return
            }
            val maxNumOfCharInARow = maxNumOfCharInARow()
            val maxNumOfCharInViewport = maxNumOfCharInARow * numOfRowsInViewport
            val (manyText, byteRange) = fileReader.readString(viewportStartBytePosition, maxNumOfCharInViewport * 4 + 3 /* UTF-8 tail */)
            val lineSeparators = lineSeparatorRegex.findAll(manyText).take(numOfRowsInViewport + 1)
            var start = 0
            var rowStarts = lineSeparators.flatMap { // this works only if running sequential (NOT in parallel)
                val line = manyText.subSequence(start ..< it.range.first)
                val (rowStarts, lastRowWidth) = textLayouter.layoutOneLine(line, viewport.width.toFloat(), 0f, start)
                start = it.range.endExclusive
                rowStarts + listOf(it.range.endExclusive)
            }.toList()
            if (start < manyText.length && rowStarts.size < numOfRowsInViewport + 1) {
                val line = manyText.subSequence(start ..< manyText.length)
                val (endRowStarts, lastRowWidth) = textLayouter.layoutOneLine(line, viewport.width.toFloat(), 0f, start)
                rowStarts += endRowStarts
            }
            println(rowStarts)
            val nextPageStart = rowStarts.getOrNull((numOfRowsInViewport - 1).coerceAtMost(rowStarts.lastIndex))
            if (nextPageStart != null) {
                viewportStartBytePosition = byteRange.start +
                        manyText.substring(0 ..< nextPageStart).toByteArray(Charsets.UTF_8).size
                viewportStartCharPosition += nextPageStart // FIXME UTF-8 offset diff in manyText
                rebuildCacheIfInvalid()
            }
        }
    }

    private fun findBytePositionOfPrevRow(numOfRowsToMove: Int, startBytePosition: Long): Long {
//        lock.read {
            val numOfRowsInViewport = floor(viewport.height / rowHeight()).roundToInt()
            if (numOfRowsInViewport == 0) {
                return startBytePosition // a dummy value to prevent changes
            }
            val maxNumOfCharInARow = maxNumOfCharInARow()
            val maxNumOfCharInViewport = maxNumOfCharInARow * numOfRowsInViewport

            var readByteStart: Long = (startBytePosition - (maxNumOfCharInViewport * 4 + 3)).coerceAtLeast(0)
            var readByteEndInclusive: Long = (readByteStart + (maxNumOfCharInViewport + 2) * 4 + 3).coerceAtMost(startBytePosition + 4 + 3)
            var rowStarts: List<Long>
            var manyText: String = ""
            var lastBytePosition = startBytePosition
            // TODO this loop is pretty inefficient. optimize.
            // this loop loops when it hits a very long line.
            while (true) {
                println("rbs $readByteStart")
                val (manyTextWithExtra, byteRangeWithExtra) = fileReader.readStringBytes(
                    readByteStart,
                    (readByteEndInclusive - readByteStart + 1L).toInt()
                )
                val extraByteLength = (byteRangeWithExtra.endExclusive - lastBytePosition).coerceAtLeast(0L)

                if (manyTextWithExtra.size - extraByteLength <= 0) {
                    // has reached the start of string although the move request is not completely fulfilled
//                    viewportStartBytePosition = 0L
//                    viewportStartCharPosition = 0L
//                    rebuildCacheIfInvalid()
                    return 0L
                }

                manyText =
                    String(manyTextWithExtra, 0, (manyTextWithExtra.size - extraByteLength).toInt(), Charsets.UTF_8) +
                        manyText
                val lineSeparators = lineSeparatorRegex.findAll(manyText).toList() //.takeLast(numOfRowsToMove + 1)

                if (lineSeparators.isEmpty()) {
                    // TODO optimize this case to fast return
                }

                val lineSeparatorsOfCompleteLines = lineSeparators.runIf(byteRangeWithExtra.start > 0) {
                    drop(1)
                }
                // discard the partial line which is the first line
                rowStarts = lineSeparatorsOfCompleteLines
                    .flatMapIndexed { i: Int, it: MatchResult ->
                        val line = manyText.subSequence(
                            it.range.endExclusive..<
                                ((lineSeparatorsOfCompleteLines.getOrNull(i + 1))?.range?.first ?: manyText.length)
                        )
                        val (rowStarts, lastRowWidth) = textLayouter.layoutOneLine(
                            line,
                            viewport.width.toFloat(),
                            0f,
                            it.range.endExclusive
                        )
                        listOf(it.range.endExclusive) + rowStarts
                    }.map { it.toLong() }.toList()
                if (rowStarts.lastOrNull() == manyText.length.toLong()) { // end with '\n'
                    rowStarts = rowStarts.dropLast(1)
                }
                if (byteRangeWithExtra.start == 0L) {
                    val line = manyText.subSequence(0 ..< (lineSeparators.firstOrNull()?.range?.start ?: manyText.length))
                    val (endRowStarts, lastRowWidth) = textLayouter.layoutOneLine(
                        line,
                        viewport.width.toFloat(),
                        0f,
                        0
                    )
                    rowStarts = listOf(0L) + endRowStarts.map { it.toLong() } + rowStarts
                }
                println(rowStarts)
                if (rowStarts.size - numOfRowsToMove >= 0 || byteRangeWithExtra.start == 0L) {
                    val prevPageStart = rowStarts.getOrNull((rowStarts.size - numOfRowsToMove).coerceAtLeast(0))
                    if (prevPageStart != null) {
                        // TODO overflow is possible
//                        viewportStartBytePosition = byteRangeWithExtra.start +
//                            manyText.substring(0 ..< prevPageStart.toInt()).toByteArray(Charsets.UTF_8).size
//                        viewportStartCharPosition -= manyText.length - prevPageStart
//                        rebuildCacheIfInvalid()
                        return byteRangeWithExtra.start +
                            manyText.substring(0 ..< prevPageStart.toInt()).toByteArray(Charsets.UTF_8).size
                    }
                }
                lastBytePosition = byteRangeWithExtra.start
                val lastReadStart: Long = readByteStart
                readByteStart = (readByteStart - (maxNumOfCharInViewport * 4 + 3)).coerceAtLeast(0)
                readByteEndInclusive = (readByteStart + (maxNumOfCharInViewport + 2) * 4 + 3).coerceAtMost(lastReadStart + 4 + 3)
            }
//        }
    }

    protected fun findBytePositionOfStartOfRow(bytePosition: Long): Long {
        val (lastPortionBytes, lastPortionRange) = fileReader.readStringBytes(
            (bytePosition - 7).coerceAtLeast(0L),
            14
        )
        val lastChar = lastPortionBytes.getOrNull((bytePosition - 1L - lastPortionRange.start).toInt())
        return if (lastChar?.toInt()?.toChar() == '\n') {
            bytePosition
        } else {
            findBytePositionOfPrevRow(1, bytePosition)
        }
    }

    internal fun moveToPrev(numOfRowsToMove: Int, startBytePosition: Long = viewportStartBytePosition) {
        viewportStartBytePosition = findBytePositionOfPrevRow(numOfRowsToMove, startBytePosition)
        rebuildCacheIfInvalid()
    }

    fun moveToPrevPage() {
        val numOfRowsInViewport = floor(viewport.height / rowHeight()).roundToInt()
        moveToPrev(numOfRowsInViewport)
    }

    fun moveToPrevRow() {
        moveToPrev(1)
    }

    fun moveToTheLastRow() {
        val fileLength: Long = fileReader.lengthInBytes()
        if (fileLength < 1) {
            return
        }
        lock.write {
            viewportStartBytePosition = findBytePositionOfStartOfRow(fileLength)
            rebuildCacheIfInvalid()
        }
    }

    fun moveToTheFirstRow() {
        lock.write {
            viewportStartBytePosition = 0L
            rebuildCacheIfInvalid()
        }
    }

    internal fun moveToBytePosition(bytePosition: Long) {
        lock.write {
            viewportStartBytePosition = bytePosition
            rebuildCacheIfInvalid()
        }
    }

    fun moveToRowOfBytePosition(bytePosition: Long) {
        lock.write {
            viewportStartBytePosition = findBytePositionOfStartOfRow(bytePosition)
            rebuildCacheIfInvalid()
        }
    }

    fun searchBackward(startBytePosition: Long, searchPredicate: Regex): LongRange {
        require(startBytePosition >= 0) { "startBytePosition should not be negative" }

        val searchTailLength = searchPredicate.pattern
        val searchTailSize = searchTailLength.toByteArray(Charsets.UTF_8).size
        val searchPattern = searchPredicate
        val windowSize = fileReader.blockSize.toLong() - searchTailSize

        lock.write {
            var readByteStart: Long = (startBytePosition - windowSize).coerceAtLeast(0)
            var readByteEndInclusive: Long = (readByteStart + windowSize + searchTailSize - 1).coerceAtMost(startBytePosition + searchTailSize)
            var manyText: String = ""
            var lastBytePosition = startBytePosition
            while (true) {
                val (manyTextWithExtra, byteRangeWithExtra) = fileReader.readStringBytes(
                    readByteStart,
                    (readByteEndInclusive - readByteStart + 1L).toInt()
                )
                val extraByteLength = (byteRangeWithExtra.endExclusive - (lastBytePosition + searchTailSize)).coerceAtLeast(0L)

                if (manyTextWithExtra.size - extraByteLength <= 0) {
                    // has reached the start of file
                    return NOT_FOUND
                }

                manyText =
                    String(manyTextWithExtra, 0, (manyTextWithExtra.size - extraByteLength).toInt(), Charsets.UTF_8) //+ manyText
                val searchResult = searchPattern.findAll(manyText).toList() //.takeLast(numOfRowsToMove + 1)

                searchResult.asReversed().forEach {
                    val bytePositionStart = byteRangeWithExtra.start + manyText.substring(0 ..< it.range.first).toByteArray(Charsets.UTF_8).size
                    if (bytePositionStart < startBytePosition) {
                        val bytePositionEndExclusive = bytePositionStart + manyText.substring(it.range.first ..< it.range.endExclusive).toByteArray(Charsets.UTF_8).size
                        return bytePositionStart ..< bytePositionEndExclusive
                    }
                }

                lastBytePosition = byteRangeWithExtra.start
                if (lastBytePosition <= 0) {
                    return NOT_FOUND
                }
                val lastReadStart: Long = readByteStart
                readByteStart = (readByteStart - windowSize).coerceAtLeast(0)
                readByteEndInclusive = (readByteStart + windowSize + searchTailSize - 1).coerceAtMost(lastReadStart + searchTailSize)
            }
        }
    }

    fun searchAtAndForward(startBytePosition: Long, searchPredicate: Regex): LongRange {
        require(startBytePosition >= 0) { "startBytePosition should not be negative" }

        val fileLength = fileReader.lengthInBytes()
        val searchTailLength = searchPredicate.pattern
        val searchTailSize = searchTailLength.toByteArray(Charsets.UTF_8).size
        val searchPattern = searchPredicate
        val windowSize = fileReader.blockSize.toLong() - searchTailSize

        lock.write {
            var readByteStart: Long = startBytePosition
            var readByteEndInclusive: Long = (readByteStart + windowSize + searchTailSize - 1).coerceAtMost(fileLength)
            var manyText: String = ""
            var lastBytePosition = startBytePosition
            while (true) {
                val (manyTextWithExtra, byteRangeWithExtra) = fileReader.readStringBytes(
                    readByteStart,
                    (readByteEndInclusive - readByteStart + 1L).toInt()
                )

                manyText = String(manyTextWithExtra, 0, manyTextWithExtra.size, Charsets.UTF_8)
                val searchStartCharPosition = if (byteRangeWithExtra.start < readByteStart) 1 else 0
                val searchResult = searchPattern.find(manyText, searchStartCharPosition)

                searchResult?.let {
                    val bytePositionStart = byteRangeWithExtra.start + manyText.substring(0 ..< it.range.first).toByteArray(Charsets.UTF_8).size
                    assert(bytePositionStart >= startBytePosition)
                    val bytePositionEndExclusive = bytePositionStart + manyText.substring(it.range.first ..< it.range.endExclusive).toByteArray(Charsets.UTF_8).size
                    return bytePositionStart ..< bytePositionEndExclusive
                }

                lastBytePosition = byteRangeWithExtra.start
                val lastReadStart: Long = readByteStart
                readByteStart = (readByteStart + windowSize).coerceAtMost(fileLength)
                readByteEndInclusive = (readByteStart + windowSize + searchTailSize - 1)
                    .coerceAtMost(fileLength - 1)
                if (lastBytePosition >= fileLength || readByteStart >= fileLength) {
                    return NOT_FOUND
                }
            }
        }
    }

    private data class ViewportCacheKey(val viewport: Viewport, val viewportStartBytePosition: Long)

    companion object {
        val NOT_FOUND: LongRange = -1L .. -2L
    }
}

data class Viewport(val width: Int, val height: Int, val density: Float) {
    init {
        require(width >= 0)
        require(height >= 0)
        require(density > 0)
    }
}
