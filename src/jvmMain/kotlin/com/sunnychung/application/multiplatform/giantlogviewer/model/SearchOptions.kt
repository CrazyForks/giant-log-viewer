package com.sunnychung.application.multiplatform.giantlogviewer.model

data class SearchOptions(
    val isRegex: Boolean,
    val isCaseSensitive: Boolean,
    val isWholeWord: Boolean, // ignore if isRegex is true
)
