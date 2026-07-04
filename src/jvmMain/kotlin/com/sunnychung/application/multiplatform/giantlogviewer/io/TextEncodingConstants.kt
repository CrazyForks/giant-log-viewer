package com.sunnychung.application.multiplatform.giantlogviewer.io

internal const val BYTES_PER_KIB: Int = 1024
internal const val BYTES_PER_MIB: Int = BYTES_PER_KIB * BYTES_PER_KIB
internal const val DEFAULT_FILE_BLOCK_SIZE_BYTES: Int = BYTES_PER_MIB

// Probe the widest common Unicode BOM prefix; supported UTF-8/UTF-16 BOMs fit within this window.
internal const val TEXT_ENCODING_PROBE_BYTES: Int = 4

internal const val UTF8_BOM_BYTE_COUNT: Int = 3
internal const val UTF16_BOM_BYTE_COUNT: Int = 2

// A valid UTF-8 code point is at most four bytes: one leading byte plus up to three continuation bytes.
internal const val UTF8_MAX_CONTINUATION_BYTES: Int = 3
internal const val UTF8_MAX_BYTES_PER_CODE_POINT: Int = 4

// UTF-16LE/BE encodes one Kotlin Char / UTF-16 code unit as two bytes.
internal const val UTF16_CODE_UNIT_BYTES: Int = 2
internal const val KOTLIN_CHARS_PER_SURROGATE_PAIR: Int = 2
internal const val UTF16_SURROGATE_PAIR_BYTES: Int = UTF16_CODE_UNIT_BYTES * 2
