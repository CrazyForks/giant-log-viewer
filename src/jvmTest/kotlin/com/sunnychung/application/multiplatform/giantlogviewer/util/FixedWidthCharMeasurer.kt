package com.sunnychung.application.multiplatform.giantlogviewer.util

import com.sunnychung.lib.multiplatform.bigtext.annotation.TemporaryBigTextApi
import com.sunnychung.lib.multiplatform.bigtext.core.layout.CharMeasurer

class FixedWidthCharMeasurer(private val charWidth: Float) : CharMeasurer<Unit> {
    @TemporaryBigTextApi
    override fun getRowHeight(): Float = 12f

    override fun measureFullText(text: CharSequence) {
        // Nothing
    }

    override fun findCharWidth(char: CharSequence, style: Unit?): Float = charWidth

    override fun findCharYOffset(char: CharSequence, style: Unit?): Float = throw NotImplementedError()
}
