package com.sunnychung.application.multiplatform.giantlogviewer.io

import com.sunnychung.application.multiplatform.giantlogviewer.layout.BidirectionalTextLayouter
import com.sunnychung.lib.multiplatform.bigtext.annotation.TemporaryBigTextApi
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

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
                val rowHeight = rowHeight()
                numOfRowsInViewport = ceil(value.height / rowHeight).roundToInt()
                field = value
                rebuildCacheIfInvalid()
            }
        }

    val isSoftWrapEnabled: Boolean = true

    var numOfRowsInViewport = 0
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

    private fun maxNumOfCharInARow(): Int {
//        val charMeasurer = textLayouter.charMeasurer
        return ceil(viewport.width / textLayouter.measureCharWidth("I")).roundToInt()
    }

    private fun readByteWindowSize(numOfChars: Int): Int {
        val minSize = fileReader.resolvedTextEncoding.maxBytesPerCharacter + fileReader.resolvedTextEncoding.lookAheadBytes
        val requestedSize = (numOfChars.coerceAtLeast(1) + 4) * fileReader.resolvedTextEncoding.maxBytesPerCharacter +
            fileReader.resolvedTextEncoding.lookAheadBytes
        return requestedSize.coerceAtLeast(minSize)
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

    private fun layoutRows(window: DecodedTextWindow, maxRows: Int = Int.MAX_VALUE): Pair<List<String>, List<Long>> {
        val manyText = window.text
        val rows: MutableList<String> = ArrayList(maxRows.coerceAtMost(numOfRowsInViewport + 2).coerceAtLeast(1))
        val rowBytePositions: MutableList<Long> = ArrayList(maxRows.coerceAtMost(numOfRowsInViewport + 2).coerceAtLeast(1))
        var lastStart = 0

        fun addRow(rowStart: Int, rowEnd: Int) {
            if (rows.size >= maxRows) {
                return
            }
            val safeRowStart = alignCharIndexToCharacterBoundary(manyText, rowStart)
            val safeRowEnd = alignCharIndexToCharacterBoundary(manyText, rowEnd).coerceAtLeast(safeRowStart)
            rows += manyText.substring(safeRowStart..<safeRowEnd)
            rowBytePositions += window.bytePositionAtCharIndex(safeRowStart)
        }

        fun layoutLine(lineStart: Int, lineEnd: Int) {
            if (rows.size >= maxRows) {
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
                if (rows.size >= maxRows) {
                    return
                }
                val rowStart = alignCharIndexToCharacterBoundary(line, lastRowStart)
                val rowEnd = alignCharIndexToCharacterBoundary(line, it).coerceAtLeast(rowStart)
                addRow(lineStart + rowStart, lineStart + rowEnd)
                lastRowStart = rowEnd
            }
            if (lastRowStart < line.length && rows.size < maxRows) {
                addRow(lineStart + lastRowStart, lineEnd)
            }
        }

        lineSeparatorRegex.findAll(manyText).forEach {
            if (rows.size >= maxRows) {
                return@forEach
            }
            layoutLine(lastStart, it.range.first)
            lastStart = it.range.endExclusive
        }
        if (lastStart < manyText.length && rows.size < maxRows) {
            layoutLine(lastStart, manyText.length)
        }
        return rows to rowBytePositions
    }

    private fun charIndexAtOrBeforeBytePosition(window: DecodedTextWindow, bytePosition: Long): Int {
        if (bytePosition <= window.byteRange.start) {
            return 0
        }
        if (bytePosition >= window.byteRange.endExclusive) {
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

        val maxBytesPerRead = readByteWindowSize(maxNumOfCharInARow().coerceAtLeast(1) + 4)
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
        val windowSize = readByteWindowSize(maxNumOfCharInARow().coerceAtLeast(1) * numOfRowsInViewport.coerceAtLeast(1))
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
                val lineStartBytePosition = window.bytePositionAtCharIndex(it.range.endExclusive)
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
        maxRows: Int,
    ): List<Long> {
        if (maxRows <= 0 || lineStartBytePosition >= strictBeforeBytePosition) {
            return emptyList()
        }

        val rowStarts = ArrayDeque<Long>()

        fun addRowStart(bytePosition: Long) {
            if (bytePosition >= strictBeforeBytePosition) {
                return
            }
            rowStarts += bytePosition
            while (rowStarts.size > maxRows) {
                rowStarts.removeFirst()
            }
        }

        addRowStart(lineStartBytePosition)
        if (lineEndBytePosition <= lineStartBytePosition) {
            return rowStarts.toList()
        }

        var currentBytePosition = lineStartBytePosition
        var firstRowOccupiedWidth = 0f
        val maxBytesPerRead = readByteWindowSize(maxNumOfCharInARow().coerceAtLeast(1) * numOfRowsInViewport.coerceAtLeast(1))
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

    private fun searchWindowConfig(searchPredicate: Regex, isBypass: Boolean): SearchWindowConfig {
        val maxMatchBytes = maxRegexMatchChars(searchPredicate.pattern)?.let {
            (it.toLong() * fileReader.resolvedTextEncoding.maxBytesPerCharacter)
                .coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt()
                .coerceAtLeast(1)
        } ?: if (isBypass) {
            MAX_BYPASS_REGEX_SEARCH_WINDOW_BYTES
        } else {
            throw IllegalArgumentException(
                "Regex search requires a bounded maximum match length. " +
                    "Unbounded patterns such as `.*`, `.+`, and `{n,}` cannot be searched with bounded memory.",
            )
        }

        val readBytes = maxOf(fileReader.blockSize.toLong(), maxMatchBytes.toLong() + 1L)
            .coerceAtMost(MAX_BYPASS_REGEX_SEARCH_WINDOW_BYTES.toLong())
            .toInt()
            .coerceAtLeast(1)
        val overlapBytes = maxMatchBytes.coerceAtMost(readBytes - 1).coerceAtLeast(0)
        return SearchWindowConfig(
            overlapBytes = overlapBytes,
            readBytes = readBytes,
            stepBytes = (readBytes - overlapBytes).coerceAtLeast(1),
        )
    }

    private fun maxRegexMatchChars(pattern: String): Int? {
        var index = 0
        var total = 0
        while (index < pattern.length) {
            val atomLength = when (pattern[index]) {
                '\\' -> {
                    index += 2
                    1
                }
                '[' -> {
                    index = findCharacterClassEnd(pattern, index) ?: return null
                    1
                }
                '(', ')', '|' -> return null
                '*', '+', '?', '{' -> return null
                else -> {
                    ++index
                    1
                }
            }
            val quantifier = readBoundedQuantifier(pattern, index)
            if (quantifier.maxCount < 0) {
                return null
            }
            index = quantifier.nextIndex
            total += atomLength * quantifier.maxCount
        }
        return total
    }

    private fun findCharacterClassEnd(pattern: String, startIndex: Int): Int? {
        var index = startIndex + 1
        while (index < pattern.length) {
            if (pattern[index] == '\\') {
                index += 2
            } else if (pattern[index] == ']') {
                return index + 1
            } else {
                ++index
            }
        }
        return null
    }

    private fun readBoundedQuantifier(pattern: String, startIndex: Int): Quantifier {
        if (startIndex >= pattern.length) {
            return Quantifier(maxCount = 1, nextIndex = startIndex)
        }
        return when (pattern[startIndex]) {
            '?' -> Quantifier(maxCount = 1, nextIndex = startIndex + 1)
            '*' -> Quantifier(maxCount = -1, nextIndex = startIndex)
            '+' -> Quantifier(maxCount = -1, nextIndex = startIndex)
            '{' -> readBraceQuantifier(pattern, startIndex)
            else -> Quantifier(maxCount = 1, nextIndex = startIndex)
        }
    }

    private fun readBraceQuantifier(pattern: String, startIndex: Int): Quantifier {
        val endIndex = pattern.indexOf('}', startIndex + 1)
        if (endIndex < 0) {
            return Quantifier(maxCount = 1, nextIndex = startIndex)
        }
        val parts = pattern.substring(startIndex + 1, endIndex).split(',')
        val maxCount = when (parts.size) {
            1 -> parts[0].toIntOrNull() ?: return Quantifier(maxCount = 1, nextIndex = startIndex)
            2 -> {
                if (parts[1].isEmpty()) {
                    return Quantifier(maxCount = -1, nextIndex = startIndex)
                }
                parts[1].toIntOrNull() ?: return Quantifier(maxCount = 1, nextIndex = startIndex)
            }
            else -> return Quantifier(maxCount = 1, nextIndex = startIndex)
        }
        return Quantifier(maxCount = maxCount, nextIndex = endIndex + 1)
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
                    val window = fileReader.readText(viewportStartBytePosition, readByteWindowSize(maxNumOfCharInViewport))
                    layoutRows(window, numOfRowsInViewport + 1)
                } else {
                    emptyList<String>() to emptyList<Long>()
                }
                textInViewport = viewportText
                startBytePositionsInViewport = rowBytePositions
                viewportCacheKey = ViewportCacheKey(viewport, viewportStartBytePosition)
            }
        }
    }

    fun moveToNextRow() { // TODO refactor to keep only one implementation of moveToNext* functions
        lock.write {
            val numOfRowsInViewport = numOfRowsInViewport
            if (numOfRowsInViewport == 0) {
                return
            }
            val maxNumOfCharInARow = maxNumOfCharInARow()
            val maxNumOfCharInViewport = maxNumOfCharInARow * numOfRowsInViewport
            val window = fileReader.readText(viewportStartBytePosition, readByteWindowSize(maxNumOfCharInViewport))
            val manyText = window.text
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
                viewportStartBytePosition = window.bytePositionAtCharIndex(nextRowStart)
                viewportStartCharPosition += nextRowStart /*-
                        manyText.subSequence(0 ..< nextRowStart)
                            .count { it.isLowSurrogate() }*/
                rebuildCacheIfInvalid()
            }
        }
    }

    fun moveToNextPage() {
        val numOfRowsInViewport = floor(viewport.height / rowHeight()).roundToInt()
        if (numOfRowsInViewport == 0) {
            return
        }
        moveToNextRow(numOfRowsInViewport)
    }

    fun moveToNextRow(numOfRowsToMove: Int) {
        require(numOfRowsToMove > 0)
        lock.write {
            val numOfRowsInViewport = floor(viewport.height / rowHeight()).roundToInt()
            if (numOfRowsInViewport == 0) {
                return
            }
            val maxNumOfCharInARow = maxNumOfCharInARow()
            val maxNumOfCharInViewport = maxNumOfCharInARow * numOfRowsInViewport
            val window = fileReader.readText(viewportStartBytePosition, readByteWindowSize(maxNumOfCharInViewport))
            val manyText = window.text
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
            val nextPageStart = rowStarts.getOrNull((numOfRowsToMove - 1).coerceAtMost(rowStarts.lastIndex))
            if (nextPageStart != null) {
                viewportStartBytePosition = window.bytePositionAtCharIndex(nextPageStart)
                viewportStartCharPosition += nextPageStart // FIXME UTF-8 offset diff in manyText
                rebuildCacheIfInvalid()
            }
        }
    }

    private fun findBytePositionOfPrevRow(numOfRowsToMove: Int, startBytePosition: Long): Long {
        require(numOfRowsToMove > 0)
//        lock.read {
            val numOfRowsInViewport = floor(viewport.height / rowHeight()).roundToInt()
            if (numOfRowsInViewport == 0) {
                return startBytePosition // a dummy value to prevent changes
            }
            var remainingRows = numOfRowsToMove
            var lineEndBytePosition = startBytePosition.coerceIn(fileReader.contentStartBytePosition, fileLength)
            while (lineEndBytePosition > fileReader.contentStartBytePosition) {
                val lineStart = findLineStartAtOrBeforeBounded(lineEndBytePosition)
                if (lineStart == null) {
                    var currentBytePosition = lineEndBytePosition
                    repeat(remainingRows) {
                        val previousBytePosition = findPreviousRowStartBounded(currentBytePosition)
                        if (previousBytePosition >= currentBytePosition) {
                            return fileReader.contentStartBytePosition
                        }
                        currentBytePosition = previousBytePosition
                    }
                    return currentBytePosition
                }

                val rowStarts = rowStartsInLineBefore(
                    lineStart.bytePosition,
                    lineEndBytePosition,
                    startBytePosition,
                    remainingRows,
                )
                if (rowStarts.size >= remainingRows) {
                    return rowStarts[rowStarts.size - remainingRows]
                }
                remainingRows -= rowStarts.size
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

    internal fun moveToPrev(numOfRowsToMove: Int, startBytePosition: Long = viewportStartBytePosition) {
        lock.write {
            viewportStartBytePosition = findBytePositionOfPrevRow(numOfRowsToMove, startBytePosition)
            rebuildCacheIfInvalid()
        }
    }

    fun moveToPrevPage() {
        val numOfRowsInViewport = floor(viewport.height / rowHeight()).roundToInt()
        moveToPrev(numOfRowsInViewport)
    }

    fun moveToPrevRow(rows: Int = 1) {
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

        val searchWindowConfig = searchWindowConfig(searchPredicate, isBypass)
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
                    val bytePositionEndExclusive = window.bytePositionAtCharIndex(it.range.endExclusive)
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
        val searchWindowConfig = searchWindowConfig(searchPredicate, isBypass)
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

                val searchStartCharPosition = if (byteRangeWithExtra.start < readByteStart) 1 else 0
                val searchResult = searchPattern.find(window.text, searchStartCharPosition)

                searchResult?.let {
                    val bytePositionStart = window.bytePositionAtCharIndex(it.range.first)
                    assert(bytePositionStart >= startBytePosition)
                    val bytePositionEndExclusive = window.bytePositionAtCharIndex(it.range.endExclusive)
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

    private data class Quantifier(val maxCount: Int, val nextIndex: Int)

    private data class SearchWindowConfig(
        val overlapBytes: Int,
        val readBytes: Int,
        val stepBytes: Int,
    )

    private data class ViewportCacheKey(val viewport: Viewport, val viewportStartBytePosition: Long)

    companion object {
        val NOT_FOUND: LongRange = -1L .. -2L
        private const val MAX_EXACT_BACKWARD_ROW_RECONSTRUCTION_BYTES: Long = 4L * 1024L * 1024L
        private const val MAX_BYPASS_REGEX_SEARCH_WINDOW_BYTES: Int = 4 * 1024 * 1024
    }
}

data class Viewport(val width: Int, val height: Int, val density: Float) {
    init {
        require(width >= 0)
        require(height >= 0)
        require(density > 0)
    }
}
