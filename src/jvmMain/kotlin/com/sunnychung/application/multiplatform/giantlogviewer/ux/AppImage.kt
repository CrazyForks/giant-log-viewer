package com.sunnychung.application.multiplatform.giantlogviewer.ux

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

@Composable
fun AppImage(
    modifier: Modifier = Modifier,
    resource: DrawableResource,
    size: Dp = 32.dp,
    color: Color,
    enabled: Boolean = true
) {
    val colorToUse = if (enabled) {
        color
    } else {
        color.copy(alpha = color.alpha / 2f)
    }
    var modifierToUse = modifier.size(size)
    Image(
        painter = painterResource(resource),
        colorFilter = ColorFilter.tint(colorToUse),
        contentDescription = null,
        modifier = modifierToUse
    )
}
