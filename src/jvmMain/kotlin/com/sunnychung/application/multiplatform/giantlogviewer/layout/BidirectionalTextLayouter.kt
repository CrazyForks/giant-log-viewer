package com.sunnychung.application.multiplatform.giantlogviewer.layout

import com.sunnychung.lib.multiplatform.bigtext.core.layout.CharMeasurer
import com.sunnychung.lib.multiplatform.bigtext.core.layout.TextLayouter

interface BidirectionalTextLayouter : TextLayouter {
    val charMeasurer: CharMeasurer<*>

    fun layoutOneLineBackwards(line: CharSequence, contentWidth: Float, firstRowOccupiedWidth: Float, offset: Int): Pair<List<Int>, Float>
}
