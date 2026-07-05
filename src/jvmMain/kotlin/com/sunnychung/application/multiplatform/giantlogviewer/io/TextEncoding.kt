package com.sunnychung.application.multiplatform.giantlogviewer.io

import java.nio.charset.Charset

enum class TextEncoding {
    Auto,
    Utf8,
    Utf16LE,
    Utf16BE,
}

enum class TextEncodingKind {
    Utf8,
    Utf16LE,
    Utf16BE,
}

data class ResolvedTextEncoding(
    val kind: TextEncodingKind,
    val charset: Charset,
    val bomLength: Int,
    val contentStartBytePosition: Long,
    val lookBehindBytes: Int,
    val lookAheadBytes: Int,
    val maxBytesPerCharacter: Int,
)
