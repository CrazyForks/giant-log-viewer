package com.sunnychung.application.multiplatform.giantlogviewer.extension

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.FrameWindowScope
import java.awt.Dimension
import kotlin.math.roundToInt

@Composable
fun FrameWindowScope.setMinimumSize(
    width: Dp = Dp.Unspecified,
    height: Dp = Dp.Unspecified,
) {
    window.minimumSize = Dimension(width.value.roundToInt(), height.value.roundToInt())
}
