package com.sunnychung.application.multiplatform.giantlogviewer.ux

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.sunnychung.application.multiplatform.giantlogviewer.ux.local.LocalColor
import com.sunnychung.application.multiplatform.giantlogviewer.ux.local.LocalFont

@Composable
fun VerticalIndicatorView(modifier: Modifier = Modifier, value: Float) {
    val colors = LocalColor.current
    var componentWidth by remember { mutableIntStateOf(0) }
    var componentHeight by remember { mutableIntStateOf(0) }

    Box(modifier.onGloballyPositioned {
        componentWidth = it.size.width
        componentHeight = it.size.height
    }) {
        Canvas(modifier = Modifier.matchParentSize()) {
            drawRect(
                color = colors.readPositionBarNegativeBackground,
                topLeft = Offset.Zero,
                size = Size(componentWidth.toFloat(), componentHeight.toFloat()),
            )
            drawRect(
                color = colors.readPositionBarPositiveBackground,
                topLeft = Offset.Zero,
                size = Size(componentWidth.toFloat(), componentHeight * value),
            )
        }
        BasicText(
            text = String.format("%.1f%%", value * 100),
            maxLines = 1,
            softWrap = false,
            style = TextStyle(
                fontSize = 12.sp,
                color = colors.readPositionBarText,
                fontFamily = LocalFont.current.normalFontFamily,
                textAlign = TextAlign.Center,
            ),
            overflow = TextOverflow.Visible, // the sizing is incorrect because of rotation
            modifier = Modifier.rotate(90f).align(Alignment.Center)
        )
    }
}
