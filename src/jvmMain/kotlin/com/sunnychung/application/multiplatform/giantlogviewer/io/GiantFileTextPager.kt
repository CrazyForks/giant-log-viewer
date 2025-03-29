package com.sunnychung.application.multiplatform.giantlogviewer.io

import com.sunnychung.application.multiplatform.giantlogviewer.layout.BidirectionalTextLayouter
import com.sunnychung.lib.multiplatform.bigtext.compose.ComposeUnicodeCharMeasurer
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
            // FIXME what if `manyText` starts with the end part of a previous line?
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

    private data class ViewportCacheKey(val viewport: Viewport, val viewportStartCharPosition: Long)
}

data class Viewport(val width: Int, val height: Int, val density: Float) {
    init {
        require(width >= 0)
        require(height >= 0)
        require(density > 0)
    }
}
