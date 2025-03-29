package com.sunnychung.application.multiplatform.giantlogviewer.util

import com.sunnychung.lib.multiplatform.bigtext.core.layout.CharMeasurer

class DivisibleWidthCharMeasurer(private val charWidth: Float) : CharMeasurer<Unit> {
    override fun measureFullText(text: CharSequence) {
        // Nothing
    }

    override fun findCharWidth(char: CharSequence, style: Unit?): Float =
        charWidth * when {
            char.isEmpty() -> 0
            char[0] == '\t' -> 1
            char[0].code < 32 -> 0
            char[0].code <= 126 -> 1
            else -> 2
        }

    override fun findCharYOffset(char: CharSequence, style: Unit?): Float = throw NotImplementedError()
}
