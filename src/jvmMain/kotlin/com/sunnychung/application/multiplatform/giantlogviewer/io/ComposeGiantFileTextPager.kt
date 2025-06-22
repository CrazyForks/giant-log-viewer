package com.sunnychung.application.multiplatform.giantlogviewer.io

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sunnychung.application.multiplatform.giantlogviewer.layout.BidirectionalTextLayouter

class ComposeGiantFileTextPager(
    fileReader: GiantFileReader,
    textLayouter: BidirectionalTextLayouter,
    initialFileLength: Long,
) : GiantFileTextPager(fileReader, textLayouter, initialFileLength) {
    override var textInViewport: List<CharSequence> by mutableStateOf(emptyList())
    override var startBytePositionsInViewport: List<Long> by mutableStateOf(emptyList())
}
