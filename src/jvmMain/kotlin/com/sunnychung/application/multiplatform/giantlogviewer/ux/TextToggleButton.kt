package com.sunnychung.application.multiplatform.giantlogviewer.ux

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sunnychung.application.multiplatform.giantlogviewer.ux.local.LocalFont
import com.sunnychung.lib.multiplatform.bigtext.extension.runIf

@Composable
fun TextToggleButton(
    modifier: Modifier = Modifier,
    text: String,
    isSelected: Boolean,
    isEnabled: Boolean = true,
    onToggle: (Boolean) -> Unit,
) {
//    val colours = LocalColor.current
    BasicText(
        text = text,
        style = TextStyle(
            fontFamily = LocalFont.current.normalFontFamily,
            color = if (isEnabled) Color.White else Color(.6f, .6f, .6f),
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        ),
        modifier = modifier
            .background(if (isSelected && isEnabled) Color(.2f, .2f, .7f) else Color(0f, 0f, .3f))
            .runIf(isEnabled) {
                clickable { onToggle(!isSelected) }
            }
            .padding(horizontal = 2.dp, vertical = 4.dp)
    )
}
