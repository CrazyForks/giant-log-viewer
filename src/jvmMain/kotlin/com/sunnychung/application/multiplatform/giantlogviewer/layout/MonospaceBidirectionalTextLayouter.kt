package com.sunnychung.application.multiplatform.giantlogviewer.layout

import com.sunnychung.lib.multiplatform.bigtext.core.layout.CharMeasurer
import com.sunnychung.lib.multiplatform.bigtext.core.layout.MonospaceTextLayouter
import com.sunnychung.lib.multiplatform.bigtext.core.layout.TextLayouter

class MonospaceBidirectionalTextLayouter<S>(override val charMeasurer: CharMeasurer<S>) : BidirectionalTextLayouter, TextLayouter by MonospaceTextLayouter<S>(charMeasurer) {

    override fun layoutOneLineBackwards(
        line: CharSequence,
        contentWidth: Float,
        firstRowOccupiedWidth: Float,
        offset: Int
    ): Pair<List<Int>, Float> {
        TODO("Not yet implemented")
    }
}
