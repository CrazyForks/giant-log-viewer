package com.sunnychung.application.multiplatform.giantlogviewer.io

import com.sunnychung.application.multiplatform.giantlogviewer.layout.BidirectionalTextLayouter
import com.sunnychung.lib.multiplatform.bigtext.compose.ComposeUnicodeCharMeasurer
import com.sunnychung.lib.multiplatform.bigtext.extension.runIf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.ArrayList
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

val lineSeparatorRegex = "\r?\n".toRegex()

class GiantFileTextPager(val fileReader: GiantFileReader, val textLayouter: BidirectionalTextLayouter) {

    private val lock = ReentrantReadWriteLock()

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

    private val viewportTextMutableStateFlow: MutableStateFlow<List<CharSequence>> = MutableStateFlow(emptyList())

    val textInViewport: StateFlow<List<CharSequence>> = viewportTextMutableStateFlow
        get() = lock.read { field }

    private var viewportCacheKey: ViewportCacheKey = ViewportCacheKey(viewport, viewportStartCharPosition)

    private fun isViewportCacheInvalid(): Boolean {
        return lock.read {
            viewportCacheKey != ViewportCacheKey(viewport, viewportStartCharPosition)
        }
    }

    private fun rowHeight(): Float {
        // TODO avoid this hardcode
        return when (val charMeasurer = textLayouter.charMeasurer) {
            is ComposeUnicodeCharMeasurer -> charMeasurer.getRowHeight()
            else -> 12f
        }
    }

    private fun maxNumOfCharInARow(): Int {
        val charMeasurer = textLayouter.charMeasurer
        return ceil(viewport.width / charMeasurer.findCharWidth("I")).roundToInt()
    }

    private fun rebuildCacheIfInvalid() {
        if (isViewportCacheInvalid()) {
            lock.write {
                if (!isViewportCacheInvalid()) {
                    return
                }
                val maxNumOfCharInARow = maxNumOfCharInARow()
                val maxNumOfCharInViewport = maxNumOfCharInARow * numOfRowsInViewport
                val viewportText = if (maxNumOfCharInViewport > 0) {
                    val manyText = fileReader.readString(viewportStartBytePosition, maxNumOfCharInViewport * 4 + 3 /* UTF-8 tail */).first
                    val lineSeparators = lineSeparatorRegex.findAll(manyText).map { it.groups[0]!!.range }
                    val rows: MutableList<String> = ArrayList<String>(lineSeparators.count() + 1)
                    var lastStart = 0

                    fun layoutLines(line: String) {
                        val (rowStarts, lastRowWidth) = textLayouter.layoutOneLine(line, viewport.width.toFloat(), 0f, 0)
                        var lastRowStart = 0
                        rowStarts.forEach {
                            rows += line.substring(lastRowStart ..< it)
                            lastRowStart = it
                        }
                        if (lastRowStart < line.length) {
                            rows += line.substring(lastRowStart)
                        }
                    }

                    lineSeparators.forEach {
                        val currentLineEndExclusive = it.first
                        val nextLineStart = it.endExclusive
                        layoutLines(manyText.substring(lastStart ..< currentLineEndExclusive))
                        lastStart = nextLineStart
                    }
                    if (lastStart < manyText.length) {
                        layoutLines(manyText.substring(lastStart ..< manyText.length))
                    }
                    rows
                } else {
                    emptyList()
                }
                viewportTextMutableStateFlow.value = viewportText
                println("rebuilt text cache")
            }
        }
    }

    fun moveToNextLine() {
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

    fun moveToPrevPage() {
        lock.write {
            val numOfRowsInViewport = floor(viewport.height / rowHeight()).roundToInt()
            if (numOfRowsInViewport == 0) {
                return
            }
            val maxNumOfCharInARow = maxNumOfCharInARow()
            val maxNumOfCharInViewport = maxNumOfCharInARow * numOfRowsInViewport

            var readByteStart: Long = (viewportStartBytePosition - (maxNumOfCharInViewport * 4 + 3)).coerceAtLeast(0)
            var readByteEndInclusive: Long = (readByteStart + (maxNumOfCharInViewport + 2) * 4 + 3).coerceAtMost(viewportStartBytePosition + 4 + 3)
            var rowStarts: List<Long>
            var manyText: String = ""
            var lastBytePosition = viewportStartBytePosition
            // TODO this loop is pretty inefficient. optimize.
            // but from test case runs, this loop never loops.
            while (true) {
                println("rbs $readByteStart")
                val (manyTextWithExtra, byteRangeWithExtra) = fileReader.readStringBytes(
                    readByteStart,
                    (readByteEndInclusive - readByteStart + 1L).toInt()
                )
                val extraByteLength = byteRangeWithExtra.endExclusive - lastBytePosition
                manyText =
                    String(manyTextWithExtra, 0, (manyTextWithExtra.size - extraByteLength).toInt(), Charsets.UTF_8) +
                        manyText
                val lineSeparators = lineSeparatorRegex.findAll(manyText).toList() //.takeLast(numOfRowsInViewport + 1)

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
                if (rowStarts.size - numOfRowsInViewport >= 0 || byteRangeWithExtra.start == 0L) {
                    val prevPageStart = rowStarts.getOrNull((rowStarts.size - numOfRowsInViewport).coerceAtLeast(0))
                    if (prevPageStart != null) {
                        // TODO overflow is possible
                        viewportStartBytePosition = byteRangeWithExtra.start +
                            manyText.substring(0 ..< prevPageStart.toInt()).toByteArray(Charsets.UTF_8).size
                        viewportStartCharPosition -= manyText.length - prevPageStart
                        rebuildCacheIfInvalid()
                        return
                    }
                }
                lastBytePosition = byteRangeWithExtra.start
                val lastReadStart: Long = readByteStart
                readByteStart = (readByteStart - (maxNumOfCharInViewport * 4 + 3)).coerceAtLeast(0)
                readByteEndInclusive = (readByteStart + (maxNumOfCharInViewport + 2) * 4 + 3).coerceAtMost(lastReadStart + 4 + 3)
            }
        }
    }

    private data class ViewportCacheKey(val viewport: Viewport, val viewportStartCharPosition: Long)
}

data class Viewport(val width: Int, val height: Int, val density: Float) {
    init {
        require(width >= 0)
        require(height >= 0)
        require(density > 0)
    }
}
