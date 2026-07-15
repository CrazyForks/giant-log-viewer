package com.sunnychung.application.multiplatform.giantlogviewer.io

import com.sunnychung.application.multiplatform.giantlogviewer.extension.toClampedInt
import java.nio.charset.StandardCharsets

internal interface TextFileCodec {
    val encoding: ResolvedTextEncoding
    val lineFeedByteLength: Long
    val minBytesPerCharacter: Long

    /**
     * Decodes a byte window beginning at `startBytePosition`. `minBytes` is the requested minimum
     * before character-boundary adjustment, but file bounds can make the returned range shorter.
     */
    fun readText(
        startBytePosition: Long,
        minBytes: Int,
        fileLength: Long,
        readBytes: (Long, Int) -> Pair<ByteArray, LongRange>,
    ): DecodedTextWindow

    fun encodedLength(text: CharSequence): Long

    fun rawLineScanReadLength(requestedLength: Int): Int

    fun findFirstLineFeedBytePosition(bytes: ByteArray, rangeStart: Long): Long?

    fun findLastLineFeedBytePosition(
        bytes: ByteArray,
        rangeStart: Long,
        strictBeforeBytePosition: Long,
    ): Long?
}

internal class Utf8TextFileCodec(
    override val encoding: ResolvedTextEncoding,
) : TextFileCodec {
    override val lineFeedByteLength: Long = 1L
    override val minBytesPerCharacter: Long = 1L

    override fun readText(
        startBytePosition: Long,
        minBytes: Int,
        fileLength: Long,
        readBytes: (Long, Int) -> Pair<ByteArray, LongRange>,
    ): DecodedTextWindow {
        if (minBytes <= 0 || fileLength <= encoding.contentStartBytePosition) {
            return Utf8DecodedTextWindow("", startBytePosition..<startBytePosition)
        }

        val requestedStart = startBytePosition
            .coerceAtLeast(encoding.contentStartBytePosition)
            .coerceAtMost(fileLength)
        val rawStart = (requestedStart - encoding.lookBehindBytes)
            .coerceAtLeast(encoding.contentStartBytePosition)
        val rawEndExclusive = (requestedStart + minBytes.toLong() + encoding.lookAheadBytes.toLong())
            .coerceAtMost(fileLength)
        val (bytes, byteRange) = readBytes(rawStart, (rawEndExclusive - rawStart).toClampedInt())
        if (bytes.isEmpty()) {
            return Utf8DecodedTextWindow("", requestedStart..<requestedStart)
        }

        val requestedStartIndex = (requestedStart - byteRange.start).toInt().coerceIn(0, bytes.size)
        val requestedEndIndex = (requestedStart + minBytes.toLong() - byteRange.start).toClampedInt().coerceIn(0, bytes.size)
        val decodeStartIndex = findSequenceStart(bytes, requestedStartIndex)
        val decodeEndIndex = findSequenceEnd(bytes, decodeStartIndex, requestedEndIndex)
            .coerceAtLeast(decodeStartIndex)
            .coerceAtMost(bytes.size)

        val text = String(bytes, decodeStartIndex, decodeEndIndex - decodeStartIndex, StandardCharsets.UTF_8)
        val decodedStart = byteRange.start + decodeStartIndex.toLong()
        val decodedEnd = byteRange.start + decodeEndIndex.toLong()
        return Utf8DecodedTextWindow(text, decodedStart..<decodedEnd)
    }

    override fun encodedLength(text: CharSequence): Long {
        var bytes = 0L
        var i = 0
        while (i < text.length) {
            val char = text[i]
            if (char.isHighSurrogate() && i + 1 < text.length && text[i + 1].isLowSurrogate()) {
                bytes += UTF8_MAX_BYTES_PER_CODE_POINT
                i += KOTLIN_CHARS_PER_SURROGATE_PAIR
            } else {
                bytes += when {
                    char.code <= 0x7F -> 1
                    char.code <= 0x7FF -> 2
                    else -> 3
                }
                ++i
            }
        }
        return bytes
    }

    override fun rawLineScanReadLength(requestedLength: Int): Int = requestedLength.coerceAtLeast(1)

    override fun findFirstLineFeedBytePosition(bytes: ByteArray, rangeStart: Long): Long? {
        return bytes.indexOfFirst { it == LF_BYTE }
            .takeIf { it >= 0 }
            ?.let { rangeStart + it.toLong() }
    }

    override fun findLastLineFeedBytePosition(
        bytes: ByteArray,
        rangeStart: Long,
        strictBeforeBytePosition: Long,
    ): Long? {
        var index = (strictBeforeBytePosition - rangeStart - 1L)
            .coerceAtMost((bytes.size - 1).toLong())
            .toInt()
        while (index >= 0) {
            if (bytes[index] == LF_BYTE) {
                return rangeStart + index.toLong()
            }
            --index
        }
        return null
    }

    private fun findSequenceStart(bytes: ByteArray, index: Int): Int {
        var i = index.coerceIn(0, bytes.size)
        var lookBehind = 0
        while (i in 1..<bytes.size && lookBehind < UTF8_MAX_CONTINUATION_BYTES && bytes[i].isContinuationByte()) {
            --i
            ++lookBehind
        }
        return i
    }

    private fun findSequenceEnd(bytes: ByteArray, startIndex: Int, endIndex: Int): Int {
        if (endIndex >= bytes.size) {
            return bytes.size
        }
        var i = startIndex
        while (i < endIndex && i < bytes.size) {
            val sequenceLength = bytes[i].sequenceLengthRepresentedByHeaderByte()
            if (i + sequenceLength > endIndex) {
                return i + sequenceLength
            }
            i += sequenceLength
        }
        return endIndex
    }

    private fun Byte.isContinuationByte(): Boolean = toUByte() in 0x80u..0xBFu

    private fun Byte.sequenceLengthRepresentedByHeaderByte(): Int {
        return when (toUByte()) {
            in 0xC2u..0xDFu -> 2
            in 0xE0u..0xEFu -> 3
            in 0xF0u..0xF4u -> UTF8_MAX_BYTES_PER_CODE_POINT
            else -> 1
        }
    }
}

internal abstract class Utf16TextFileCodec(
    override val encoding: ResolvedTextEncoding,
) : TextFileCodec {
    override val lineFeedByteLength: Long = UTF16_CODE_UNIT_BYTES.toLong()
    override val minBytesPerCharacter: Long = UTF16_CODE_UNIT_BYTES.toLong()

    override fun readText(
        startBytePosition: Long,
        minBytes: Int,
        fileLength: Long,
        readBytes: (Long, Int) -> Pair<ByteArray, LongRange>,
    ): DecodedTextWindow {
        if (minBytes <= 0 || fileLength <= encoding.contentStartBytePosition) {
            return Utf16DecodedTextWindow("", startBytePosition..<startBytePosition)
        }

        val requestedStart = startBytePosition
            .coerceAtLeast(encoding.contentStartBytePosition)
            .coerceAtMost(fileLength)
        var decodeStart = alignToCodeUnitStart(requestedStart)
        if (decodeStart >= encoding.contentStartBytePosition + UTF16_CODE_UNIT_BYTES && readCodeUnit(decodeStart, fileLength, readBytes)?.toChar()?.isLowSurrogate() == true) {
            decodeStart -= UTF16_CODE_UNIT_BYTES
        }

        val requestedEnd = (requestedStart + minBytes.toLong()).coerceAtMost(fileLength)
        var decodeEnd = alignToCodeUnitEnd(requestedEnd).coerceAtMost(fileLength)
        if (decodeEnd - UTF16_CODE_UNIT_BYTES >= decodeStart && readCodeUnit(decodeEnd - UTF16_CODE_UNIT_BYTES, fileLength, readBytes)?.toChar()?.isHighSurrogate() == true) {
            decodeEnd = (decodeEnd + UTF16_CODE_UNIT_BYTES).coerceAtMost(fileLength)
        }
        if ((decodeEnd - decodeStart) % UTF16_CODE_UNIT_BYTES != 0L) {
            --decodeEnd
        }
        if (decodeEnd <= decodeStart) {
            return Utf16DecodedTextWindow("", decodeStart..<decodeStart)
        }

        val (bytes, byteRange) = readBytes(decodeStart, (decodeEnd - decodeStart).toClampedInt())
        val usableLength = bytes.size - bytes.size % UTF16_CODE_UNIT_BYTES
        val text = String(bytes, 0, usableLength, encoding.charset)
        return Utf16DecodedTextWindow(text, byteRange.start..<(byteRange.start + usableLength.toLong()))
    }

    override fun encodedLength(text: CharSequence): Long = text.length * UTF16_CODE_UNIT_BYTES.toLong()

    override fun rawLineScanReadLength(requestedLength: Int): Int = (requestedLength + 1).coerceAtLeast(1)

    override fun findFirstLineFeedBytePosition(bytes: ByteArray, rangeStart: Long): Long? {
        var index = firstCodeUnitOffset(rangeStart)
        while (index + 1 < bytes.size) {
            if (isLineFeedCodeUnit(bytes, index)) {
                return rangeStart + index.toLong()
            }
            index += UTF16_CODE_UNIT_BYTES
        }
        return null
    }

    override fun findLastLineFeedBytePosition(
        bytes: ByteArray,
        rangeStart: Long,
        strictBeforeBytePosition: Long,
    ): Long? {
        var index = lastCodeUnitOffsetBefore(
            rangeStart = rangeStart,
            bytesSize = bytes.size,
            strictBeforeBytePosition = strictBeforeBytePosition,
        )
        while (index >= 0) {
            if (isLineFeedCodeUnit(bytes, index)) {
                return rangeStart + index.toLong()
            }
            index -= UTF16_CODE_UNIT_BYTES
        }
        return null
    }

    private fun alignToCodeUnitStart(bytePosition: Long): Long {
        val relative = bytePosition - encoding.contentStartBytePosition
        return if (relative % UTF16_CODE_UNIT_BYTES == 0L) {
            bytePosition
        } else {
            bytePosition - 1
        }
    }

    private fun alignToCodeUnitEnd(bytePosition: Long): Long {
        val relative = bytePosition - encoding.contentStartBytePosition
        return if (relative % UTF16_CODE_UNIT_BYTES == 0L) {
            bytePosition
        } else {
            bytePosition + 1L
        }
    }

    private fun firstCodeUnitOffset(rangeStart: Long): Int {
        val remainder = positiveMod(rangeStart - encoding.contentStartBytePosition, UTF16_CODE_UNIT_BYTES.toLong())
        return if (remainder == 0L) 0 else (UTF16_CODE_UNIT_BYTES - remainder).toInt()
    }

    private fun lastCodeUnitOffsetBefore(
        rangeStart: Long,
        bytesSize: Int,
        strictBeforeBytePosition: Long,
    ): Int {
        val lastPossibleAbsolutePosition = (strictBeforeBytePosition - UTF16_CODE_UNIT_BYTES)
            .coerceAtMost(rangeStart + bytesSize - UTF16_CODE_UNIT_BYTES)
        if (lastPossibleAbsolutePosition < rangeStart) {
            return -1
        }
        val alignedAbsolutePosition = lastPossibleAbsolutePosition -
            positiveMod(lastPossibleAbsolutePosition - encoding.contentStartBytePosition, UTF16_CODE_UNIT_BYTES.toLong())
        return (alignedAbsolutePosition - rangeStart).toInt()
    }

    protected abstract fun isLineFeedCodeUnit(bytes: ByteArray, index: Int): Boolean

    protected abstract fun readCodeUnit(firstByte: Int, secondByte: Int): Int

    private fun positiveMod(value: Long, modulus: Long): Long {
        return ((value % modulus) + modulus) % modulus
    }

    private fun readCodeUnit(
        bytePosition: Long,
        fileLength: Long,
        readBytes: (Long, Int) -> Pair<ByteArray, LongRange>,
    ): Int? {
        if (bytePosition < encoding.contentStartBytePosition || bytePosition + UTF16_CODE_UNIT_BYTES - 1L >= fileLength) {
            return null
        }
        val (bytes, _) = readBytes(bytePosition, UTF16_CODE_UNIT_BYTES)
        if (bytes.size < UTF16_CODE_UNIT_BYTES) {
            return null
        }
        val first = bytes[0].toInt() and 0xFF
        val second = bytes[1].toInt() and 0xFF
        return readCodeUnit(first, second)
    }
}

internal class Utf16LETextFileCodec(
    encoding: ResolvedTextEncoding,
) : Utf16TextFileCodec(encoding) {
    override fun isLineFeedCodeUnit(bytes: ByteArray, index: Int): Boolean {
        return index >= 0 &&
            index + 1 < bytes.size &&
            bytes[index] == LF_BYTE &&
            bytes[index + 1] == NUL_BYTE
    }

    override fun readCodeUnit(firstByte: Int, secondByte: Int): Int {
        return firstByte or (secondByte shl 8)
    }
}

internal class Utf16BETextFileCodec(
    encoding: ResolvedTextEncoding,
) : Utf16TextFileCodec(encoding) {
    override fun isLineFeedCodeUnit(bytes: ByteArray, index: Int): Boolean {
        return index >= 0 &&
            index + 1 < bytes.size &&
            bytes[index] == NUL_BYTE &&
            bytes[index + 1] == LF_BYTE
    }

    override fun readCodeUnit(firstByte: Int, secondByte: Int): Int {
        return (firstByte shl 8) or secondByte
    }
}

private const val LF_BYTE: Byte = 0x0A
private const val NUL_BYTE: Byte = 0x00
