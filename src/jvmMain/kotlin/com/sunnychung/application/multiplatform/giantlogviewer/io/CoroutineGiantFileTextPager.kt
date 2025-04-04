package com.sunnychung.application.multiplatform.giantlogviewer.io

import com.sunnychung.application.multiplatform.giantlogviewer.layout.BidirectionalTextLayouter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.concurrent.read
import kotlin.concurrent.write

class CoroutineGiantFileTextPager(
    fileReader: GiantFileReader,
    textLayouter: BidirectionalTextLayouter
) : GiantFileTextPager(fileReader, textLayouter) {
    override var textInViewport: List<CharSequence>
        get() = lock.read { viewportTextMutableStateFlow.value }
        set(value) {
            lock.write {
                viewportTextMutableStateFlow.value = value
            }
        }

    private val viewportTextMutableStateFlow: MutableStateFlow<List<CharSequence>> = MutableStateFlow(emptyList())

    val textInViewportStateFlow: StateFlow<List<CharSequence>> = viewportTextMutableStateFlow

}
