package com.sunnychung.application.multiplatform.giantlogviewer.io

interface DecodedTextWindow {
    val text: String
    val byteRange: LongRange

    fun bytePositionAtCharIndex(charIndex: Int): Long
}

internal class Utf8DecodedTextWindow(
    override val text: String,
    override val byteRange: LongRange,
) : DecodedTextWindow {
    private var cachedCharIndex: Int = 0
    private var cachedByteOffset: Long = 0L

    override fun bytePositionAtCharIndex(charIndex: Int): Long {
        val target = normalizeCharIndex(charIndex.coerceIn(0, text.length))
        if (target < cachedCharIndex) {
            cachedCharIndex = 0
            cachedByteOffset = 0L
        }

        var i = cachedCharIndex
        var byteOffset = cachedByteOffset
        while (i < target) {
            val char = text[i]
            if (char.isHighSurrogate() && i + 1 < text.length && text[i + 1].isLowSurrogate()) {
                if (i + KOTLIN_CHARS_PER_SURROGATE_PAIR > target) {
                    break
                }
                byteOffset += UTF8_MAX_BYTES_PER_CODE_POINT
                i += KOTLIN_CHARS_PER_SURROGATE_PAIR
            } else {
                byteOffset += char.utf8ByteLength()
                ++i
            }
        }

        cachedCharIndex = i
        cachedByteOffset = byteOffset
        return byteRange.start + byteOffset
    }

    private fun normalizeCharIndex(index: Int): Int {
        return if (index in 1..<text.length && text[index].isLowSurrogate() && text[index - 1].isHighSurrogate()) {
            index - 1
        } else {
            index
        }
    }

    private fun Char.utf8ByteLength(): Int {
        return when {
            code <= 0x7F -> 1
            code <= 0x7FF -> 2
            else -> 3
        }
    }
}

internal class Utf16DecodedTextWindow(
    override val text: String,
    override val byteRange: LongRange,
) : DecodedTextWindow {
    override fun bytePositionAtCharIndex(charIndex: Int): Long {
        val target = normalizeCharIndex(charIndex.coerceIn(0, text.length))
        return byteRange.start + target * UTF16_CODE_UNIT_BYTES.toLong()
    }

    private fun normalizeCharIndex(index: Int): Int {
        return if (index in 1..<text.length && text[index].isLowSurrogate() && text[index - 1].isHighSurrogate()) {
            index - 1
        } else {
            index
        }
    }
}
