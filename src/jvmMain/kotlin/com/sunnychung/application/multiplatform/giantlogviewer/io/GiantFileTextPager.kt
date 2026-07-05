package com.sunnychung.application.multiplatform.giantlogviewer.io

import com.sunnychung.application.multiplatform.giantlogviewer.extension.coerceToLong
import com.sunnychung.application.multiplatform.giantlogviewer.extension.safeEndExclusive
import com.sunnychung.application.multiplatform.giantlogviewer.extension.toClampedInt
import com.sunnychung.application.multiplatform.giantlogviewer.layout.BidirectionalTextLayouter
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

    private fun numOfCharsInViewport(): Long = maxNumOfCharInARow().toLong() * numOfRowsInViewport

    private fun readByteWindowSize(numOfChars: Long): Int {
        val encoding = fileReader.resolvedTextEncoding
        val minSize = encoding.maxBytesPerCharacter.toLong() + encoding.lookAheadBytes.toLong()
        val requestedSize = (numOfChars.coerceAtLeast(1L) + READ_WINDOW_EXTRA_CHARS) * encoding.maxBytesPerCharacter.toLong() +
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
        return rowStartBytePosition + fileReader.encodedLength(rowText.subSequence(0, charIndex.coerceIn(0, rowText.length)))
    }

    private fun alignCharIndexToCharacterBoundary(text: CharSequence, index: Int): Int {
        val clampedIndex = index.coerceIn(0, text.length)
        return if (clampedIndex in 1..<text.length && text[clampedIndex].isLowSurrogate() && text[clampedIndex - 1].isHighSurrogate()) {
            clampedIndex - 1
        } else {
            clampedIndex
        }
    }

    private fun layoutRows(window: DecodedTextWindow, maxRows: Long = Long.MAX_VALUE): Pair<List<String>, List<Long>> {
        val manyText = window.text
        val rowCapacity = rowListInitialCapacity(maxRows)
        val rows: MutableList<String> = ArrayList(rowCapacity)
        val rowBytePositions: MutableList<Long> = ArrayList(rowCapacity)
        var lastStart = 0

        fun addRow(rowStart: Int, rowEnd: Int) {
            if (rows.size.toLong() >= maxRows) {
                return
            }
            val safeRowStart = alignCharIndexToCharacterBoundary(manyText, rowStart)
            val safeRowEnd = alignCharIndexToCharacterBoundary(manyText, rowEnd).coerceAtLeast(safeRowStart)
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
            val (rowStarts, _) = textLayouter.layoutOneLine(line, viewport.width.toFloat(), 0f, 0)
            var lastRowStart = 0
            rowStarts.forEach {
                if (rows.size.toLong() >= maxRows) {
                    return
                }
                val rowStart = alignCharIndexToCharacterBoundary(line, lastRowStart)
                val rowEnd = alignCharIndexToCharacterBoundary(line, it).coerceAtLeast(rowStart)
                addRow(lineStart + rowStart, lineStart + rowEnd)
                lastRowStart = rowEnd
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

    private fun nextCharIndex(text: CharSequence, index: Int): Int {
        val safeIndex = alignCharIndexToCharacterBoundary(text, index)
        return if (
            safeIndex < text.length &&
            text[safeIndex].isHighSurrogate() &&
            safeIndex + 1 < text.length &&
            text[safeIndex + 1].isLowSurrogate()
        ) {
            safeIndex + KOTLIN_CHARS_PER_SURROGATE_PAIR
        } else {
            (safeIndex + 1).coerceAtMost(text.length)
        }
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
        val charStartIndex = if (
            endIndex >= 2 &&
            window.text[endIndex - 2].isHighSurrogate() &&
            window.text[endIndex - 1].isLowSurrogate()
        ) {
            endIndex - 2
        } else {
            endIndex - 1
        }
        return CharacterBefore(
            text = window.text.subSequence(charStartIndex, endIndex),
            startBytePosition = window.bytePositionAtCharIndex(charStartIndex),
            endBytePosition = window.bytePositionAtCharIndex(endIndex),
        )
    }

    private fun lineEndBefore(bytePosition: Long): Long {
        var end = bytePosition.coerceIn(fileReader.contentStartBytePosition, fileLength)
        val lastChar = charBefore(end)
        if (lastChar?.text?.singleOrNull() == '\n') {
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

        val maxBytesPerRead = readByteWindowSize(maxNumOfCharInARow().toLong().coerceAtLeast(1L) + READ_WINDOW_EXTRA_CHARS)
        var accumulatedWidth = 0f
        var hasCharInRow = false

        while (rowEndBytePosition > fileReader.contentStartBytePosition) {
            val readStart = (rowEndBytePosition - maxBytesPerRead).coerceAtLeast(fileReader.contentStartBytePosition)
            val window = fileReader.readText(readStart, (rowEndBytePosition - readStart).toInt())
            var index = charIndexAtOrBeforeBytePosition(window, rowEndBytePosition)
            if (index <= 0) {
                return fileReader.contentStartBytePosition
            }

            while (index > 0) {
                val charStartIndex = if (
                    index >= 2 &&
                    window.text[index - 2].isHighSurrogate() &&
                    window.text[index - 1].isLowSurrogate()
                ) {
                    index - 2
                } else {
                    index - 1
                }
                val charText = window.text.subSequence(charStartIndex, index)
                if (charText.singleOrNull() == '\n') {
                    return window.bytePositionAtCharIndex(index)
                }

                val charWidth = textLayouter.measureCharWidth(charText)
                if (hasCharInRow && accumulatedWidth + charWidth > viewport.width) {
                    return window.bytePositionAtCharIndex(index)
                }
                accumulatedWidth += charWidth
                hasCharInRow = true
                index = charStartIndex
            }
            rowEndBytePosition = window.byteRange.start
        }
        return fileReader.contentStartBytePosition
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
            val (rowStartsInWindow, lastRowWidth) = textLayouter.layoutOneLine(
                text,
                viewport.width.toFloat(),
                firstRowOccupiedWidth,
                0,
            )
            rowStartsInWindow.forEach {
                val bytePosition = window.bytePositionAtCharIndex(alignCharIndexToCharacterBoundary(text, it))
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
        val pageBytes = readByteWindowSize(numOfCharsInViewport().coerceAtLeast(1))
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
                    val window = fileReader.readText(viewportStartBytePosition, readByteWindowSize(maxNumOfCharInViewport))
                    layoutRows(window, numOfRowsInViewport + 1L)
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
                .coerceAtLeast(1L)
            val maxNumOfCharInViewport = maxNumOfCharInARow().toLong() * numOfRowsToRead
            val window = fileReader.readText(viewportStartBytePosition, readByteWindowSize(maxNumOfCharInViewport))
            val manyText = window.text
            val maxRowsToRead = numOfRowsToRead + 1L
            val lineSeparators = lineSeparatorRegex.findAll(manyText).take(maxRowsToRead.toClampedInt())
            var start = 0
            var rowStarts = lineSeparators.flatMap { // this works only if running sequential (NOT in parallel)
                val line = manyText.subSequence(start ..< it.range.first)
                val (rowStarts, lastRowWidth) = textLayouter.layoutOneLine(line, viewport.width.toFloat(), 0f, start)
                start = it.range.safeEndExclusive
                rowStarts + listOf(it.range.safeEndExclusive)
            }.toList()
            if (start < manyText.length && rowStarts.size.toLong() < maxRowsToRead) {
                val line = manyText.subSequence(start ..< manyText.length)
                val (endRowStarts, lastRowWidth) = textLayouter.layoutOneLine(line, viewport.width.toFloat(), 0f, start)
                rowStarts += endRowStarts
            }
            val nextPageStart = rowStarts.getOrNull((numOfRowsToMove - 1L).coerceAtMost(rowStarts.lastIndex.toLong()).toInt())
            if (nextPageStart != null) {
                viewportStartBytePosition = window.bytePositionAtCharIndex(nextPageStart)
                viewportStartCharPosition += nextPageStart // FIXME UTF-8 offset diff in manyText
                rebuildCacheIfInvalid()
            }
        }
    }

    private fun findBytePositionOfPrevRow(numOfRowsToMove: Long, startBytePosition: Long): Long {
        require(numOfRowsToMove > 0L)
//        lock.read {
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
            viewportStartBytePosition = findBytePositionOfPrevRow(numOfRowsToMove, startBytePosition)
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
                    if (bytePositionStart < startBytePosition) {
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
                    window.bytePositionAtCharIndex(it.range.first) >= startBytePosition
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

    private data class ViewportCacheKey(val viewport: Viewport, val viewportStartBytePosition: Long)

    companion object {
        val NOT_FOUND: LongRange = -1L .. -2L
        private const val MAX_EXACT_BACKWARD_ROW_RECONSTRUCTION_MIB: Long = 4L
        private const val MAX_REGEX_SEARCH_WINDOW_MIB: Int = 4
        private const val MAX_EXACT_BACKWARD_ROW_RECONSTRUCTION_BYTES: Long = MAX_EXACT_BACKWARD_ROW_RECONSTRUCTION_MIB * BYTES_PER_MIB
        private const val SEARCH_WINDOW_PAGE_COUNT: Long = 4L
        private const val MAX_INITIAL_ROW_LIST_CAPACITY: Int = 1024
        private const val MAX_REGEX_SEARCH_WINDOW_BYTES: Int = MAX_REGEX_SEARCH_WINDOW_MIB * BYTES_PER_MIB
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
