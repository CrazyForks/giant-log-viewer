package com.sunnychung.application.multiplatform.giantlogviewer.io

import java.nio.charset.Charset

enum class TextEncoding {
    Auto,
    Utf8,
    Utf16LE,
    Utf16BE,
    Utf8WithoutBom,
    Utf8WithBom,
    Utf16LEWithoutBom,
    Utf16LEWithBom,
    Utf16BEWithoutBom,
    Utf16BEWithBom,
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

val selectableTextEncodings: List<TextEncoding> = listOf(
    TextEncoding.Auto,
    TextEncoding.Utf8WithoutBom,
    TextEncoding.Utf8WithBom,
    TextEncoding.Utf16LEWithoutBom,
    TextEncoding.Utf16LEWithBom,
    TextEncoding.Utf16BEWithoutBom,
    TextEncoding.Utf16BEWithBom,
)

fun TextEncoding.displayName(): String = when (this) {
    TextEncoding.Auto -> "Auto"
    TextEncoding.Utf8,
    TextEncoding.Utf8WithoutBom -> "UTF-8 without BOM"
    TextEncoding.Utf8WithBom -> "UTF-8 with BOM"
    TextEncoding.Utf16LE,
    TextEncoding.Utf16LEWithoutBom -> "UTF-16 LE without BOM"
    TextEncoding.Utf16LEWithBom -> "UTF-16 LE with BOM"
    TextEncoding.Utf16BE,
    TextEncoding.Utf16BEWithoutBom -> "UTF-16 BE without BOM"
    TextEncoding.Utf16BEWithBom -> "UTF-16 BE with BOM"
}

fun ResolvedTextEncoding.displayName(): String {
    val charsetName = when (kind) {
        TextEncodingKind.Utf8 -> "UTF-8"
        TextEncodingKind.Utf16LE -> "UTF-16 LE"
        TextEncodingKind.Utf16BE -> "UTF-16 BE"
    }
    val bomName = if (bomLength > 0) "with BOM" else "without BOM"
    return "$charsetName $bomName"
}
