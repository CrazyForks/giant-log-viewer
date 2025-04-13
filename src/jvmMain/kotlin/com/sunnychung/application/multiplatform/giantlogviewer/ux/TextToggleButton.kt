package com.sunnychung.application.multiplatform.giantlogviewer.ux

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sunnychung.application.multiplatform.giantlogviewer.ux.local.LocalColor
import com.sunnychung.application.multiplatform.giantlogviewer.ux.local.LocalFont
import com.sunnychung.lib.multiplatform.bigtext.extension.runIf

@Composable
fun TextToggleButton(
    modifier: Modifier = Modifier,
    text: String,
    fontFamily: FontFamily = LocalFont.current.normalFontFamily,
    isSelected: Boolean,
    isEnabled: Boolean = true,
    onToggle: (Boolean) -> Unit,
) {
    val colors = LocalColor.current
    BasicText(
        text = text,
        style = TextStyle(
            fontFamily = fontFamily,
            color = if (isEnabled) {
                if (isSelected) {
                    colors.toggleButtonOnText
                } else {
                    colors.toggleButtonOffText
                }
            } else {
                colors.toggleButtonDisabledText
            },
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        ),
        modifier = modifier
            .background(if (isSelected && isEnabled) colors.toggleButtonOnBackground else colors.toggleButtonOffBackground)
            .runIf(isEnabled) {
                clickable { onToggle(!isSelected) }
            }
            .padding(horizontal = 2.dp, vertical = 4.dp)
    )
}
