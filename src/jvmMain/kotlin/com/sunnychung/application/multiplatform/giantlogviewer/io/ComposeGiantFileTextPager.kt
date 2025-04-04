package com.sunnychung.application.multiplatform.giantlogviewer.io

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sunnychung.application.multiplatform.giantlogviewer.layout.BidirectionalTextLayouter

class ComposeGiantFileTextPager(
    fileReader: GiantFileReader,
    textLayouter: BidirectionalTextLayouter
) : GiantFileTextPager(fileReader, textLayouter) {
    override var textInViewport: List<CharSequence> by mutableStateOf(emptyList())
}
