package com.sunnychung.application.multiplatform.giantlogviewer.io

fun trimTextToByteLength(
    text: String,
    byteLength: Long,
    maxByteLength: Long,
    encodedLength: (CharSequence) -> Long,
): Pair<String, Long> {
    var actualText = text
    var actualByteLength = byteLength
    while (actualByteLength > maxByteLength && actualText.isNotEmpty()) {
        val removeStart = if (
            actualText.length >= 2 &&
            actualText[actualText.length - 2].isHighSurrogate() &&
            actualText[actualText.length - 1].isLowSurrogate()
        ) {
            actualText.length - 2
        } else {
            actualText.length - 1
        }
        val removedText = actualText.subSequence(removeStart, actualText.length)
        actualByteLength -= encodedLength(removedText)
        actualText = actualText.substring(0, removeStart)
    }
    return actualText to actualByteLength.coerceAtLeast(0L)
}
