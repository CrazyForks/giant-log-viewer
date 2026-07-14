package com.sunnychung.application.multiplatform.giantlogviewer.io

import com.sunnychung.application.multiplatform.giantlogviewer.extension.coerceToLong
import com.sunnychung.application.multiplatform.giantlogviewer.extension.safeEndExclusive
import com.sunnychung.application.multiplatform.giantlogviewer.extension.saturatedAdd
import com.sunnychung.application.multiplatform.giantlogviewer.extension.saturatedMultiply
import com.sunnychung.application.multiplatform.giantlogviewer.extension.toClampedInt
import com.sunnychung.application.multiplatform.giantlogviewer.layout.BidirectionalTextLayouter
import com.sunnychung.application.multiplatform.giantlogviewer.util.GraphemeClusters
import com.sunnychung.lib.multiplatform.bigtext.annotation.TemporaryBigTextApi
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.math.ceil
import kotlin.math.floor

val lineSeparatorRegex = "\r?\n".toRegex()

abstract class GiantFileTextPager(
    val fileReader: GiantFileReader,
    val textLayouter: BidirectionalTextLayouter,
    initialFileLength: Long,
) {

    protected val lock = ReentrantReadWriteLock()

    var fileLength: Long = initialFileLength.takeIf { it > 0 } ?:
        fileReader.lengthInBytes()

    @Deprecated("No longer maintained as no fast way to determine the value when the cursor is moved to EOF")
    var viewportStartCharPosition: Long = 0L
        get() = lock.read { field }
        set(value) {
            lock.write {
                field = value
                rebuildCacheIfInvalid()
            }
        }

    var viewportStartBytePosition: Long = fileReader.contentStartBytePosition
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
                numOfRowsInViewport = computeNumOfRowsInViewport(value)
                field = value
                rebuildCacheIfInvalid()
            }
        }

    val isSoftWrapEnabled: Boolean = true

    var numOfRowsInViewport = 0L
        private set

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
        return textLayouter.charMeasurer.getRowHeight()
    }

    private fun computeNumOfRowsInViewport(viewport: Viewport): Long {
        val rowHeight = rowHeight()
        if (viewport.height <= 0 || !java.lang.Float.isFinite(rowHeight) || rowHeight <= 0f) {
            return 0L
        }
        return ceil(viewport.height.toDouble() / rowHeight.toDouble()).coerceToLong()
    }

    private fun numOfRowsForPageMovement(): Long {
        val rowHeight = rowHeight()
        if (viewport.height <= 0 || !java.lang.Float.isFinite(rowHeight) || rowHeight <= 0f) {
            return 0L
        }
        return floor(viewport.height.toDouble() / rowHeight.toDouble()).coerceToLong()
    }

    private fun maxNumOfCharInARow(): Int {
        val charWidth = textLayouter.measureCharWidth("I")
        if (viewport.width <= 0 || !java.lang.Float.isFinite(charWidth) || charWidth <= 0f) {
            return 0
        }
        return ceil(viewport.width.toDouble() / charWidth.toDouble()).coerceToLong()
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
    }

    private fun numOfCharsInViewport(): Long = maxNumOfCharInARow().toLong() saturatedMultiply numOfRowsInViewport

    private fun readByteWindowSize(numOfChars: Long): Int {
        return readByteWindowSize(numOfChars, fileReader.resolvedTextEncoding.maxBytesPerCharacter.toLong())
    }

    private fun expandedGraphemeReadByteWindowSize(numOfChars: Long): Int {
        val bytesPerDisplayUnit = fileReader.resolvedTextEncoding.maxBytesPerCharacter
            .coerceAtLeast(MAX_BYTES_PER_GRAPHEME_CLUSTER_ESTIMATE)
            .toLong()
        return readByteWindowSize(numOfChars, bytesPerDisplayUnit)
    }

    private fun searchReadByteWindowSize(numOfChars: Long): Int {
        return readByteWindowSize(numOfChars)
    }

    private fun readByteWindowSize(numOfChars: Long, bytesPerUnit: Long): Int {
        val encoding = fileReader.resolvedTextEncoding
        val minSize = encoding.maxBytesPerCharacter.toLong() + encoding.lookAheadBytes.toLong()
        val requestedSize = (numOfChars.coerceAtLeast(1L) + READ_WINDOW_EXTRA_CHARS) * bytesPerUnit +
            encoding.lookAheadBytes.toLong()
        return requestedSize
            .coerceAtLeast(minSize)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
    }

    private fun rowListInitialCapacity(maxRows: Long): Int {
        val requestedRows = (numOfRowsInViewport + ROW_LIST_EXTRA_ROWS)
            .coerceAtMost(MAX_INITIAL_ROW_LIST_CAPACITY.toLong())
            .toInt()
        return maxRows.coerceAtMost(requestedRows.toLong()).coerceAtLeast(1L).toInt()
    }

    fun encodedLengthOfText(text: CharSequence): Long = fileReader.encodedLength(text)

    internal fun bytePositionAtRowCharIndex(rowIndex: Int, charIndex: Int): Long {
        val rowStartBytePosition = startBytePositionsInViewport.getOrNull(rowIndex) ?: return viewportStartBytePosition
        val rowText = textInViewport.getOrNull(rowIndex) ?: return rowStartBytePosition
        val safeCharIndex = GraphemeClusters.boundaryAtOrBefore(rowText, charIndex)
        return rowStartBytePosition + fileReader.encodedLength(rowText.subSequence(0, safeCharIndex))
    }

    private fun alignCharIndexToCharacterBoundary(text: CharSequence, index: Int): Int {
        return GraphemeClusters.boundaryAtOrBefore(text, index)
    }

    /**
     * Streams row-break discovery over one physical line without allocating a width list for every
     * character. Unlike `TextLayouter.layoutOneLine()`, this emits row starts as soon as they are
     * known and can stop after `maxRowStarts` when the caller only needs a viewport-sized prefix.
     *
     * The return value is the occupied width of the final partial row, which backward reconstruction
     * needs when a logical line spans multiple decoded windows.
     */
    private fun forEachRowStartInLine(
        line: CharSequence,
        contentWidth: Float,
        firstRowOccupiedWidth: Float,
        offset: Int,
        maxRowStarts: Long = Long.MAX_VALUE,
        onRowStart: (Int) -> Unit,
    ): Float {
        if (maxRowStarts <= 0L) {
            return firstRowOccupiedWidth
        }

        var emittedRowStarts = 0L
        var currentRowOccupiedWidth = firstRowOccupiedWidth
        var hasCharInCurrentRow = firstRowOccupiedWidth > 0f
        GraphemeClusters.forEachUntil(line) { index, nextIndex ->
            val char = line.subSequence(index, nextIndex)
            val charWidth = textLayouter.measureCharWidth(char)
            if (currentRowOccupiedWidth + charWidth > contentWidth && hasCharInCurrentRow) {
                onRowStart(offset + index)
                ++emittedRowStarts
                if (emittedRowStarts >= maxRowStarts) {
                    return@forEachUntil false
                }
                currentRowOccupiedWidth = 0f
                hasCharInCurrentRow = false
            }
            currentRowOccupiedWidth += charWidth
            hasCharInCurrentRow = true
            true
        }
        return currentRowOccupiedWidth
    }

    /**
     * Collects the streamed row starts into a bounded list for callers that need concrete indexes.
     * The returned indexes are relative to `offset`, matching `TextLayouter.layoutOneLine()`'s
     * contract, but the implementation avoids the layouter's intermediate per-character widths.
     */
    private fun layoutOneLineStreaming(
        line: CharSequence,
        contentWidth: Float,
        firstRowOccupiedWidth: Float,
        offset: Int,
        maxRowStarts: Long = Long.MAX_VALUE,
    ): List<Int> {
        val capacity = maxRowStarts
            .coerceAtMost(MAX_INITIAL_ROW_LIST_CAPACITY.toLong())
            .coerceAtLeast(0L)
            .toInt()
        val rowStarts = ArrayList<Int>(capacity)
        forEachRowStartInLine(
            line = line,
            contentWidth = contentWidth,
            firstRowOccupiedWidth = firstRowOccupiedWidth,
            offset = offset,
            maxRowStarts = maxRowStarts,
        ) {
            rowStarts += it
        }
        return rowStarts
    }

    private fun layoutRows(window: DecodedTextWindow, maxRows: Long = Long.MAX_VALUE): Pair<List<String>, List<Long>> {
        val manyText = window.text
        val rowCapacity = rowListInitialCapacity(maxRows)
        val rows: MutableList<String> = ArrayList(rowCapacity)
        val rowBytePositions: MutableList<Long> = ArrayList(rowCapacity)
        var lastStart = 0

        // Preconditions: rowStart and rowEnd must be known grapheme-cluster boundaries in
        // manyText. Callers below pass physical line boundaries or row starts emitted by
        // forEachRowStartInLine(), which iterates with GraphemeClusters.
        fun addRow(rowStart: Int, rowEnd: Int) {
            if (rows.size.toLong() >= maxRows) {
                return
            }
            val safeRowStart = rowStart.coerceIn(0, manyText.length)
            val safeRowEnd = rowEnd.coerceIn(safeRowStart, manyText.length)
            rows += manyText.substring(safeRowStart..<safeRowEnd)
            rowBytePositions += window.bytePositionAtCharIndex(safeRowStart)
        }

        fun layoutLine(lineStart: Int, lineEnd: Int) {
            if (rows.size.toLong() >= maxRows) {
                return
            }
            if (lineStart == lineEnd) {
                addRow(lineStart, lineStart)
                return
            }
            val line = manyText.subSequence(lineStart..<lineEnd)
            val rowStarts = layoutOneLineStreaming(
                line = line,
                contentWidth = viewport.width.toFloat(),
                firstRowOccupiedWidth = 0f,
                offset = 0,
                maxRowStarts = maxRows - rows.size.toLong(),
            )
            var lastRowStart = 0
            rowStarts.forEach {
                if (rows.size.toLong() >= maxRows) {
                    return
                }
                // `it` and `lastRowStart` are relative indexes produced by
                // forEachRowStartInLine(), so both are already grapheme boundaries in `line`.
                addRow(lineStart + lastRowStart, lineStart + it)
                lastRowStart = it
            }
            if (lastRowStart < line.length && rows.size.toLong() < maxRows) {
                addRow(lineStart + lastRowStart, lineEnd)
            }
        }

        lineSeparatorRegex.findAll(manyText).forEach {
            if (rows.size.toLong() >= maxRows) {
                return@forEach
            }
            layoutLine(lastStart, it.range.first)
            lastStart = it.range.safeEndExclusive
        }
        if (lastStart < manyText.length && rows.size.toLong() < maxRows) {
            layoutLine(lastStart, manyText.length)
        }
        return rows to rowBytePositions
    }

    private fun layoutRowsFromAdaptiveWindow(
        startBytePosition: Long,
        numOfCharsToRead: Long,
        maxRows: Long,
    ): Pair<List<String>, List<Long>> {
        val window = fileReader.readText(startBytePosition, readByteWindowSize(numOfCharsToRead))
        val rows = layoutRows(window, maxRows)
        if (rows.first.size.toLong() >= maxRows || window.byteRange.safeEndExclusive >= fileLength) {
            return rows
        }

        val expandedWindow = fileReader.readText(startBytePosition, expandedGraphemeReadByteWindowSize(numOfCharsToRead))
        return layoutRows(expandedWindow, maxRows)
    }

    private fun rowStartsInWindow(window: DecodedTextWindow, maxRowsToRead: Long): List<Int> {
        val manyText = window.text
        val lineSeparators = lineSeparatorRegex.findAll(manyText).take(maxRowsToRead.toClampedInt())
        var start = 0
        val rowStarts = ArrayList<Int>(rowListInitialCapacity(maxRowsToRead))
        lineSeparators.forEach { // this works only if running sequential (NOT in parallel)
            val line = manyText.subSequence(start ..< it.range.first)
            val lineBreaks = layoutOneLineStreaming(
                line = line,
                contentWidth = viewport.width.toFloat(),
                firstRowOccupiedWidth = 0f,
                offset = start,
                maxRowStarts = maxRowsToRead - rowStarts.size.toLong(),
            )
            rowStarts += lineBreaks
            start = it.range.safeEndExclusive
            rowStarts += it.range.safeEndExclusive
        }
        if (start < manyText.length && rowStarts.size.toLong() < maxRowsToRead) {
            val line = manyText.subSequence(start ..< manyText.length)
            val endRowStarts = layoutOneLineStreaming(
                line = line,
                contentWidth = viewport.width.toFloat(),
                firstRowOccupiedWidth = 0f,
                offset = start,
                maxRowStarts = maxRowsToRead - rowStarts.size.toLong(),
            )
            rowStarts += endRowStarts
        }
        return rowStarts
    }

    private fun rowStartsFromAdaptiveWindow(
        startBytePosition: Long,
        numOfCharsToRead: Long,
        maxRowsToRead: Long,
        requiredRowIndex: Int,
    ): Pair<DecodedTextWindow, List<Int>> {
        val window = fileReader.readText(startBytePosition, readByteWindowSize(numOfCharsToRead))
        val rowStarts = rowStartsInWindow(window, maxRowsToRead)
        if (rowStarts.getOrNull(requiredRowIndex) != null || window.byteRange.safeEndExclusive >= fileLength) {
            return window to rowStarts
        }

        val expandedWindow = fileReader.readText(startBytePosition, expandedGraphemeReadByteWindowSize(numOfCharsToRead))
        return expandedWindow to rowStartsInWindow(expandedWindow, maxRowsToRead)
    }

    private fun charIndexAtOrBeforeBytePosition(window: DecodedTextWindow, bytePosition: Long): Int {
        if (bytePosition <= window.byteRange.start) {
            return 0
        }
        if (bytePosition >= window.byteRange.safeEndExclusive) {
            return window.text.length
        }
        var low = 0
        var high = window.text.length
        while (low < high) {
            val mid = (low + high + 1) / 2
            if (window.bytePositionAtCharIndex(mid) <= bytePosition) {
                low = mid
            } else {
                high = mid - 1
            }
        }
        return alignCharIndexToCharacterBoundary(window.text, low)
    }

    private fun charIndexAtOrAfterBytePosition(window: DecodedTextWindow, bytePosition: Long): Int {
        if (bytePosition <= window.byteRange.start) {
            return 0
        }
        if (bytePosition >= window.byteRange.safeEndExclusive) {
            return window.text.length
        }

        val index = charIndexAtOrBeforeBytePosition(window, bytePosition)
        if (window.bytePositionAtCharIndex(index) >= bytePosition) {
            return index
        }
        return nextCharIndex(window.text, index)
    }

    private fun isGraphemeBoundary(text: CharSequence, index: Int): Boolean {
        return GraphemeClusters.boundaryAtOrBefore(text, index) == index
    }

    private fun isValidSearchMatch(text: CharSequence, match: MatchResult): Boolean {
        return isGraphemeBoundary(text, match.range.first) &&
            isGraphemeBoundary(text, match.range.safeEndExclusive)
    }

    private fun nextCharIndex(text: CharSequence, index: Int): Int {
        return GraphemeClusters.nextBoundary(text, index)
    }

    private fun charBefore(bytePosition: Long): CharacterBefore? {
        if (bytePosition <= fileReader.contentStartBytePosition) {
            return null
        }
        val readStart = (bytePosition - fileReader.resolvedTextEncoding.lookBehindBytes - fileReader.resolvedTextEncoding.maxBytesPerCharacter)
            .coerceAtLeast(fileReader.contentStartBytePosition)
        val window = fileReader.readText(readStart, (bytePosition - readStart).toInt())
        val endIndex = charIndexAtOrBeforeBytePosition(window, bytePosition)
        if (endIndex <= 0) {
            return null
        }
        val charStartIndex = GraphemeClusters.previousBoundary(window.text, endIndex)
        return CharacterBefore(
            text = window.text.subSequence(charStartIndex, endIndex),
            startBytePosition = window.bytePositionAtCharIndex(charStartIndex),
            endBytePosition = window.bytePositionAtCharIndex(endIndex),
        )
    }

    private fun lineEndBefore(bytePosition: Long): Long {
        var end = bytePosition.coerceIn(fileReader.contentStartBytePosition, fileLength)
        val lastChar = charBefore(end)
        if (lastChar?.text?.endsWith("\n") == true) {
            end = lastChar.startBytePosition
            val beforeLf = charBefore(end)
            if (beforeLf?.text?.singleOrNull() == '\r') {
                end = beforeLf.startBytePosition
            }
        }
        return end
    }

    private fun findPreviousRowStartBounded(startBytePosition: Long): Long {
        var rowEndBytePosition = lineEndBefore(startBytePosition)
        if (rowEndBytePosition <= fileReader.contentStartBytePosition) {
            return fileReader.contentStartBytePosition
        }

        val numCharsToRead = maxNumOfCharInARow().toLong().coerceAtLeast(1L) + READ_WINDOW_EXTRA_CHARS
        val maxBytesPerRead = readByteWindowSize(numCharsToRead)
        val expandedMaxBytesPerRead = expandedGraphemeReadByteWindowSize(numCharsToRead)
        var accumulatedWidth = 0f
        var hasCharInRow = false

        while (rowEndBytePosition > fileReader.contentStartBytePosition) {
            val readStart = (rowEndBytePosition - maxBytesPerRead).coerceAtLeast(fileReader.contentStartBytePosition)
            val normalScan = scanPreviousRowStartInWindow(
                readStart = readStart,
                rowEndBytePosition = rowEndBytePosition,
                initialAccumulatedWidth = accumulatedWidth,
                initialHasCharInRow = hasCharInRow,
            )
            normalScan.foundBytePosition?.let {
                return it
            }

            if (normalScan.scannedStartBytePosition <= fileReader.contentStartBytePosition) {
                return fileReader.contentStartBytePosition
            }

            val expandedReadStart = (rowEndBytePosition - expandedMaxBytesPerRead)
                .coerceAtLeast(fileReader.contentStartBytePosition)
            if (expandedReadStart < readStart) {
                val expandedScan = scanPreviousRowStartInWindow(
                    readStart = expandedReadStart,
                    rowEndBytePosition = rowEndBytePosition,
                    initialAccumulatedWidth = accumulatedWidth,
                    initialHasCharInRow = hasCharInRow,
                )
                expandedScan.foundBytePosition?.let {
                    return it
                }
                accumulatedWidth = expandedScan.accumulatedWidth
                hasCharInRow = expandedScan.hasCharInRow
                rowEndBytePosition = expandedScan.scannedStartBytePosition
            } else {
                accumulatedWidth = normalScan.accumulatedWidth
                hasCharInRow = normalScan.hasCharInRow
                rowEndBytePosition = normalScan.scannedStartBytePosition
            }
        }
        return fileReader.contentStartBytePosition
    }

    private fun scanPreviousRowStartInWindow(
        readStart: Long,
        rowEndBytePosition: Long,
        initialAccumulatedWidth: Float,
        initialHasCharInRow: Boolean,
    ): ReverseRowScanResult {
        val window = fileReader.readText(readStart, (rowEndBytePosition - readStart).toInt())
        val index = charIndexAtOrBeforeBytePosition(window, rowEndBytePosition)
        if (index <= 0) {
            return ReverseRowScanResult(
                foundBytePosition = null,
                accumulatedWidth = initialAccumulatedWidth,
                hasCharInRow = initialHasCharInRow,
                scannedStartBytePosition = fileReader.contentStartBytePosition,
            )
        }

        var accumulatedWidth = initialAccumulatedWidth
        var hasCharInRow = initialHasCharInRow
        var foundBytePosition: Long? = null
        GraphemeClusters.forEachReversedUntil(window.text, index) { charStartIndex, charEndIndex ->
            val charText = window.text.subSequence(charStartIndex, charEndIndex)
            if (charText.endsWith("\n")) {
                foundBytePosition = window.bytePositionAtCharIndex(charEndIndex)
                return@forEachReversedUntil false
            }

            val charWidth = textLayouter.measureCharWidth(charText)
            if (hasCharInRow && accumulatedWidth + charWidth > viewport.width) {
                foundBytePosition = window.bytePositionAtCharIndex(charEndIndex)
                return@forEachReversedUntil false
            }
            accumulatedWidth += charWidth
            hasCharInRow = true
            true
        }

        return ReverseRowScanResult(
            foundBytePosition = foundBytePosition,
            accumulatedWidth = accumulatedWidth,
            hasCharInRow = hasCharInRow,
            scannedStartBytePosition = window.byteRange.start,
        )
    }

    private fun findLineStartAtOrBeforeBounded(bytePosition: Long): LineStart? {
        var searchEnd = bytePosition.coerceIn(fileReader.contentStartBytePosition, fileLength)
        var scannedBytes = 0L
        val windowSize = readByteWindowSize(numOfCharsInViewport().coerceAtLeast(1))
        while (searchEnd > fileReader.contentStartBytePosition && scannedBytes < MAX_EXACT_BACKWARD_ROW_RECONSTRUCTION_BYTES) {
            val remainingBudget = MAX_EXACT_BACKWARD_ROW_RECONSTRUCTION_BYTES - scannedBytes
            val readSize = minOf(windowSize.toLong(), searchEnd - fileReader.contentStartBytePosition, remainingBudget).toInt()
            val readStart = searchEnd - readSize
            val window = fileReader.readText(readStart, readSize)
            scannedBytes += searchEnd - readStart

            val endIndex = charIndexAtOrBeforeBytePosition(window, searchEnd)
            val text = window.text.subSequence(0, endIndex)
            var lastLineSeparator: MatchResult? = null
            lineSeparatorRegex.findAll(text).forEach {
                lastLineSeparator = it
            }
            lastLineSeparator?.let {
                val lineStartBytePosition = window.bytePositionAtCharIndex(it.range.safeEndExclusive)
                val previousLineEndBytePosition = if (
                    it.range.first == 0 &&
                    window.text.getOrNull(0) == '\n' &&
                    charBefore(window.byteRange.start)?.text?.singleOrNull() == '\r'
                ) {
                    charBefore(window.byteRange.start)?.startBytePosition ?: window.byteRange.start
                } else {
                    window.bytePositionAtCharIndex(it.range.first)
                }
                return LineStart(
                    bytePosition = lineStartBytePosition,
                    previousLineEndBytePosition = previousLineEndBytePosition,
                )
            }

            if (window.byteRange.start <= fileReader.contentStartBytePosition) {
                return LineStart(fileReader.contentStartBytePosition, null)
            }
            searchEnd = window.byteRange.start
        }
        return null
    }

    private fun rowStartsInLineBefore(
        lineStartBytePosition: Long,
        lineEndBytePosition: Long,
        strictBeforeBytePosition: Long,
        maxRows: Long,
    ): List<Long> {
        if (maxRows <= 0L || lineStartBytePosition >= strictBeforeBytePosition) {
            return emptyList()
        }

        val rowStarts = ArrayDeque<Long>()

        fun addRowStart(bytePosition: Long) {
            if (bytePosition >= strictBeforeBytePosition) {
                return
            }
            rowStarts += bytePosition
            while (rowStarts.size.toLong() > maxRows) {
                rowStarts.removeFirst()
            }
        }

        addRowStart(lineStartBytePosition)
        if (lineEndBytePosition <= lineStartBytePosition) {
            return rowStarts.toList()
        }

            var currentBytePosition = lineStartBytePosition
            var firstRowOccupiedWidth = 0f
            val maxBytesPerRead = readByteWindowSize(numOfCharsInViewport().coerceAtLeast(1))
            while (currentBytePosition < lineEndBytePosition) {
            val readLength = (lineEndBytePosition - currentBytePosition).coerceAtMost(maxBytesPerRead.toLong()).toInt()
            val window = fileReader.readText(currentBytePosition, readLength)
            val endIndex = charIndexAtOrBeforeBytePosition(window, lineEndBytePosition)
            if (endIndex <= 0) {
                break
            }

            val text = window.text.subSequence(0, endIndex)
            val rowStartsInWindow = mutableListOf<Int>()
            // This caller must scan the whole bounded window: it keeps only the last `maxRows`
            // starts, so early stopping would return the wrong rows before `lineEndBytePosition`.
            val lastRowWidth = forEachRowStartInLine(
                line = text,
                contentWidth = viewport.width.toFloat(),
                firstRowOccupiedWidth = firstRowOccupiedWidth,
                offset = 0,
            ) {
                rowStartsInWindow += it
            }
            rowStartsInWindow.forEach {
                // Row starts are emitted by forEachRowStartInLine(), therefore they are already
                // grapheme boundaries in `text` and do not need another BreakIterator pass.
                val bytePosition = window.bytePositionAtCharIndex(it)
                if (bytePosition > currentBytePosition && bytePosition < lineEndBytePosition) {
                    addRowStart(bytePosition)
                }
            }
            firstRowOccupiedWidth = lastRowWidth

            val nextBytePosition = window.bytePositionAtCharIndex(endIndex)
            if (nextBytePosition <= currentBytePosition) {
                break
            }
            currentBytePosition = nextBytePosition
        }
        return rowStarts.toList()
    }

    private fun searchWindowConfig(searchPredicate: Regex): SearchWindowConfig {
        val pageBytes = searchReadByteWindowSize(numOfCharsInViewport().coerceAtLeast(1))
        val readBytes = (pageBytes.toLong() * SEARCH_WINDOW_PAGE_COUNT)
            .coerceAtMost(MAX_REGEX_SEARCH_WINDOW_BYTES.toLong())
            .toInt()
            .coerceAtLeast(1)
        val overlapBytes = fileReader.encodedLength(searchPredicate.pattern)
            .coerceAtLeast(fileReader.resolvedTextEncoding.maxBytesPerCharacter.toLong())
            .coerceAtMost((readBytes - 1).coerceAtLeast(0).toLong())
            .toInt()
        return SearchWindowConfig(
            overlapBytes = overlapBytes,
            readBytes = readBytes,
            stepBytes = (readBytes - overlapBytes).coerceAtLeast(1),
        )
    }

    private fun rebuildCacheIfInvalid() {
        if (isViewportCacheInvalid()) {
            lock.write {
                if (!isViewportCacheInvalid()) {
                    return
                }
                val maxNumOfCharInViewport = numOfCharsInViewport()
                val (viewportText, rowBytePositions) = if (maxNumOfCharInViewport > 0) {
                    layoutRowsFromAdaptiveWindow(
                        startBytePosition = viewportStartBytePosition,
                        numOfCharsToRead = maxNumOfCharInViewport,
                        maxRows = numOfRowsInViewport + 1L,
                    )
                } else {
                    emptyList<String>() to emptyList<Long>()
                }
                textInViewport = viewportText
                startBytePositionsInViewport = rowBytePositions
                viewportCacheKey = ViewportCacheKey(viewport, viewportStartBytePosition)
            }
        }
    }

    fun moveToNextRow() {
        moveToNextRow(1L)
    }

    fun moveToNextPage() {
        moveToNextRow(numOfRowsForPageMovement().coerceAtLeast(1L))
    }

    fun moveToNextRow(numOfRowsToMove: Long) {
        require(numOfRowsToMove >= 0L)
        if (numOfRowsToMove == 0L) {
            return
        }
        lock.write {
            val numOfRowsToRead = numOfRowsForPageMovement()
                .coerceAtLeast(numOfRowsToMove)
                .coerceAtLeast(numOfRowsToMove saturatedAdd numOfRowsInViewport saturatedAdd 1L)
                .coerceAtLeast(1L)
            val maxNumOfCharInViewport = maxNumOfCharInARow().toLong() saturatedMultiply numOfRowsToRead
            val maxRowsToRead = numOfRowsToRead saturatedAdd 1L
            val (window, rowStarts) = rowStartsFromAdaptiveWindow(
                startBytePosition = viewportStartBytePosition,
                numOfCharsToRead = maxNumOfCharInViewport,
                maxRowsToRead = maxRowsToRead,
                requiredRowIndex = (numOfRowsToMove - 1L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            )
            val nextPageStart = rowStarts.getOrNull((numOfRowsToMove - 1L).coerceAtMost(rowStarts.lastIndex.toLong()).toInt())
            if (nextPageStart != null) {
                viewportStartBytePosition = window.bytePositionAtCharIndex(nextPageStart)
                viewportStartCharPosition += nextPageStart // FIXME UTF-8 offset diff in manyText
                rebuildCacheIfInvalid()
            }
        }
    }

    /**
     * Note: The caller must acquire a lock before calling this function.
     *
     * @param  isStartKnownRowBoundary  True only when startBytePosition is already a known visual row boundary, such as the
     *         current viewport start. Arbitrary byte positions need the conservative line-aware path
     *         below because stepping from the middle of a row can return the wrong previous row.
     */
    private fun findBytePositionOfPrevRow(
        numOfRowsToMove: Long,
        startBytePosition: Long,
        /*
        True only when startBytePosition is already a known visual row boundary, such as the
        current viewport start. Arbitrary byte positions need the conservative line-aware path
        below because stepping from the middle of a row can return the wrong previous row.
        */
        isStartKnownRowBoundary: Boolean = false,
    ): Long {
        require(numOfRowsToMove > 0L)
//        lock.read {
            // Most UI scroll events move a small number of visual rows. Step those rows directly
            // before looking for the physical line start: on a huge no-newline line, line-start search
            // scans up to several MiB just to discover that exact reconstruction is not possible.
            if (isStartKnownRowBoundary && numOfRowsToMove <= MAX_DIRECT_BACKWARD_ROW_STEPS) {
                findPreviousRowStartByStepping(numOfRowsToMove, startBytePosition)?.let {
                    return it
                }
            }

            var remainingRows = numOfRowsToMove
            var lineEndBytePosition = startBytePosition.coerceIn(fileReader.contentStartBytePosition, fileLength)
            while (lineEndBytePosition > fileReader.contentStartBytePosition) {
                val lineStart = findLineStartAtOrBeforeBounded(lineEndBytePosition)
                if (lineStart == null) {
                    var currentBytePosition = lineEndBytePosition
                    var rowsToMoveWithinLine = remainingRows
                    while (rowsToMoveWithinLine > 0L) {
                        val previousBytePosition = findPreviousRowStartBounded(currentBytePosition)
                        if (previousBytePosition >= currentBytePosition) {
                            return fileReader.contentStartBytePosition
                        }
                        currentBytePosition = previousBytePosition
                        --rowsToMoveWithinLine
                    }
                    return currentBytePosition
                }

                val rowStarts = rowStartsInLineBefore(
                    lineStart.bytePosition,
                    lineEndBytePosition,
                    startBytePosition,
                    remainingRows,
                )
                if (rowStarts.size.toLong() >= remainingRows) {
                    return rowStarts[(rowStarts.size.toLong() - remainingRows).toInt()]
                }
                remainingRows -= rowStarts.size.toLong()
                lineEndBytePosition = lineStart.previousLineEndBytePosition ?: return fileReader.contentStartBytePosition
            }
            return fileReader.contentStartBytePosition
//        }
    }

    /**
     * Note: This method only works and returns non-null value if previous row start belongs to the same line as `startBytePosition`.
     *
     * @return byte position of previous row start, if valid; otherwise null
     */
    private fun findPreviousRowStartByStepping(numOfRowsToMove: Long, startBytePosition: Long): Long? {
        var currentBytePosition = startBytePosition.coerceIn(fileReader.contentStartBytePosition, fileLength)
        var remainingRows = numOfRowsToMove
        while (remainingRows > 0L && currentBytePosition > fileReader.contentStartBytePosition) {
            if (isImmediatelyAfterLineSeparator(currentBytePosition)) {
                return null
            }
            val previousBytePosition = findPreviousRowStartBounded(currentBytePosition)
            if (previousBytePosition >= currentBytePosition) {
                return null
            }
            currentBytePosition = previousBytePosition
            --remainingRows
        }
        return currentBytePosition
    }

    private fun isImmediatelyAfterLineSeparator(bytePosition: Long): Boolean {
        val previousChar = charBefore(bytePosition)
        return previousChar?.text?.endsWith("\n") == true
    }

    protected fun findBytePositionOfStartOfRow(bytePosition: Long): Long {
        val start = (bytePosition - fileReader.resolvedTextEncoding.lookBehindBytes - fileReader.resolvedTextEncoding.maxBytesPerCharacter)
            .coerceAtLeast(fileReader.contentStartBytePosition)
        val window = fileReader.readText(start, (bytePosition - start).toInt().coerceAtLeast(1))
        val charIndex = charIndexAtOrBeforeBytePosition(window, bytePosition)
        val lastChar = window.text.getOrNull(charIndex - 1)
        return if (lastChar == '\n') {
            bytePosition
        } else {
            findBytePositionOfPrevRow(1, bytePosition)
        }
    }

    internal fun moveToPrev(numOfRowsToMove: Long, startBytePosition: Long = viewportStartBytePosition) {
        require(numOfRowsToMove >= 0L)
        if (numOfRowsToMove == 0L) {
            return
        }
        lock.write {
            viewportStartBytePosition = findBytePositionOfPrevRow(
                numOfRowsToMove = numOfRowsToMove,
                startBytePosition = startBytePosition,
                isStartKnownRowBoundary = true,
            )
            rebuildCacheIfInvalid()
        }
    }

    fun moveToPrevPage() {
        moveToPrev(numOfRowsForPageMovement().coerceAtLeast(1L))
    }

    fun moveToPrevRow(rows: Long = 1L) {
        require(rows >= 0L)
        if (rows == 0L) {
            return
        }
        moveToPrev(rows)
    }

    fun moveToTheLastRow() {
        val fileLength: Long = fileLength
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
            viewportStartBytePosition = fileReader.contentStartBytePosition
            rebuildCacheIfInvalid()
        }
    }

    internal fun moveToBytePosition(bytePosition: Long) {
        lock.write {
            viewportStartBytePosition = bytePosition.coerceAtLeast(fileReader.contentStartBytePosition)
            rebuildCacheIfInvalid()
        }
    }

    fun moveToRowOfBytePosition(bytePosition: Long) {
        lock.write {
            viewportStartBytePosition = findBytePositionOfStartOfRow(bytePosition)
            rebuildCacheIfInvalid()
        }
    }

    fun searchBackward(startBytePosition: Long, searchPredicate: Regex, isBypass: Boolean = false): LongRange {
        require(startBytePosition >= 0) { "startBytePosition should not be negative" }

        val searchWindowConfig = searchWindowConfig(searchPredicate)
        val searchPattern = searchPredicate
        val readSize = searchWindowConfig.readBytes.toLong()
        val overlapSize = searchWindowConfig.overlapBytes.toLong()
        val stepSize = searchWindowConfig.stepBytes.toLong()

        lock.write {
            var readByteStart: Long = (startBytePosition - stepSize).coerceAtLeast(fileReader.contentStartBytePosition)
            var readByteEndExclusive: Long = (readByteStart + readSize).coerceAtMost(startBytePosition + overlapSize)
            var lastBytePosition = startBytePosition
            while (true) {
                val window = fileReader.readText(
                    readByteStart,
                    (readByteEndExclusive - readByteStart).toInt()
                )
                val byteRangeWithExtra = window.byteRange
                val trimEndIndex = charIndexAtOrBeforeBytePosition(window, lastBytePosition + overlapSize)

                if (trimEndIndex <= 0) {
                    // has reached the start of file
                    return NOT_FOUND
                }

                val manyText = window.text.substring(0, trimEndIndex)
                var lastMatchBeforeStart: MatchResult? = null
                searchPattern.findAll(manyText).forEach {
                    val bytePositionStart = window.bytePositionAtCharIndex(it.range.first)
                    if (bytePositionStart < startBytePosition && isValidSearchMatch(manyText, it)) {
                        lastMatchBeforeStart = it
                    }
                }

                lastMatchBeforeStart?.let {
                    val bytePositionStart = window.bytePositionAtCharIndex(it.range.first)
                    val bytePositionEndExclusive = window.bytePositionAtCharIndex(it.range.safeEndExclusive)
                    return bytePositionStart ..< bytePositionEndExclusive
                }

                lastBytePosition = byteRangeWithExtra.start
                if (lastBytePosition <= fileReader.contentStartBytePosition) {
                    return NOT_FOUND
                }
                val lastReadStart: Long = readByteStart
                readByteStart = (readByteStart - stepSize).coerceAtLeast(fileReader.contentStartBytePosition)
                readByteEndExclusive = (readByteStart + readSize).coerceAtMost(lastReadStart + overlapSize)
            }
        }
    }

    fun searchAtAndForward(startBytePosition: Long, searchPredicate: Regex, isBypass: Boolean = false): LongRange {
        require(startBytePosition >= 0) { "startBytePosition should not be negative" }

        val fileLength = fileLength
        val searchWindowConfig = searchWindowConfig(searchPredicate)
        val searchPattern = searchPredicate
        val readSize = searchWindowConfig.readBytes.toLong()
        val stepSize = searchWindowConfig.stepBytes.toLong()

        lock.write {
            var readByteStart: Long = startBytePosition.coerceAtLeast(fileReader.contentStartBytePosition)
            var readByteEndExclusive: Long = (readByteStart + readSize).coerceAtMost(fileLength)
            var lastBytePosition = startBytePosition
            while (true) {
                val window = fileReader.readText(
                    readByteStart,
                    (readByteEndExclusive - readByteStart).toInt()
                )
                val byteRangeWithExtra = window.byteRange

                val searchStartCharPosition = charIndexAtOrAfterBytePosition(window, readByteStart)
                val searchResult = generateSequence(searchPattern.find(window.text, searchStartCharPosition)) {
                    it.next()
                }.firstOrNull {
                    window.bytePositionAtCharIndex(it.range.first) >= startBytePosition &&
                        isValidSearchMatch(window.text, it)
                }

                searchResult?.let {
                    val bytePositionStart = window.bytePositionAtCharIndex(it.range.first)
                    val bytePositionEndExclusive = window.bytePositionAtCharIndex(it.range.safeEndExclusive)
                    return bytePositionStart ..< bytePositionEndExclusive
                }

                lastBytePosition = byteRangeWithExtra.start
                val lastReadStart: Long = readByteStart
                readByteStart = (readByteStart + stepSize).coerceAtMost(fileLength)
                readByteEndExclusive = (readByteStart + readSize).coerceAtMost(fileLength)
                if (lastBytePosition >= fileLength || readByteStart >= fileLength) {
                    return NOT_FOUND
                }
            }
        }
    }

    private data class CharacterBefore(
        val text: CharSequence,
        val startBytePosition: Long,
        val endBytePosition: Long,
    )

    private data class LineStart(val bytePosition: Long, val previousLineEndBytePosition: Long?)

    private data class SearchWindowConfig(
        val overlapBytes: Int,
        val readBytes: Int,
        val stepBytes: Int,
    )

    private data class ReverseRowScanResult(
        val foundBytePosition: Long?,
        val accumulatedWidth: Float,
        val hasCharInRow: Boolean,
        val scannedStartBytePosition: Long,
    )

    private data class ViewportCacheKey(val viewport: Viewport, val viewportStartBytePosition: Long)

    companion object {
        val NOT_FOUND: LongRange = -1L .. -2L
        private const val MAX_EXACT_BACKWARD_ROW_RECONSTRUCTION_MIB: Long = 4L
        private const val MAX_REGEX_SEARCH_WINDOW_MIB: Int = 4
        private const val MAX_EXACT_BACKWARD_ROW_RECONSTRUCTION_BYTES: Long = MAX_EXACT_BACKWARD_ROW_RECONSTRUCTION_MIB * BYTES_PER_MIB
        private const val SEARCH_WINDOW_PAGE_COUNT: Long = 4L
        private const val MAX_INITIAL_ROW_LIST_CAPACITY: Int = 1024
        private const val MAX_DIRECT_BACKWARD_ROW_STEPS: Long = 128L
        private const val MAX_REGEX_SEARCH_WINDOW_BYTES: Int = MAX_REGEX_SEARCH_WINDOW_MIB * BYTES_PER_MIB
        private const val MAX_BYTES_PER_GRAPHEME_CLUSTER_ESTIMATE: Int = 32
        private const val READ_WINDOW_EXTRA_CHARS: Int = 4
        private const val ROW_LIST_EXTRA_ROWS: Long = 2L
    }
}

data class Viewport(val width: Int, val height: Int, val density: Float) {
    init {
        require(width >= 0)
        require(height >= 0)
        require(density > 0)
    }
}
