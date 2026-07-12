package com.sunnychung.application.multiplatform.giantlogviewer.io

import com.sunnychung.application.multiplatform.giantlogviewer.extension.toClampedInt
import java.nio.charset.StandardCharsets

internal interface TextFileCodec {
    val encoding: ResolvedTextEncoding

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
}

internal class Utf8TextFileCodec(
    override val encoding: ResolvedTextEncoding,
) : TextFileCodec {
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

internal class Utf16TextFileCodec(
    override val encoding: ResolvedTextEncoding,
) : TextFileCodec {
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
        return when (encoding.kind) {
            TextEncodingKind.Utf16LE -> first or (second shl 8)
            TextEncodingKind.Utf16BE -> (first shl 8) or second
            TextEncodingKind.Utf8 -> error("UTF-8 is not a UTF-16 encoding")
        }
    }
}
