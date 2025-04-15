package com.sunnychung.application.multiplatform.giantlogviewer.ux

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.sunnychung.application.giantlogviewer.generated.resources.Res
import com.sunnychung.application.giantlogviewer.generated.resources.move_to
import com.sunnychung.application.multiplatform.giantlogviewer.ux.local.LocalColor
import com.sunnychung.application.multiplatform.giantlogviewer.ux.local.LocalFont

@Composable
fun EmptyFileView(modifier: Modifier = Modifier) {
    val colors = LocalColor.current
    Box(modifier.fillMaxSize().padding(32.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.align(Alignment.Center)) {
            AppImage(
                resource = Res.drawable.move_to,
                size = 96.dp,
                color = colors.bigTextHint,
            )
            Spacer(Modifier.height(32.dp))
            BasicText(
                text = "Drop a UTF-8 or ASCII text file here\nto get started",
                style = TextStyle(
                    color = colors.bigTextHint,
                    fontSize = 24.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 1.5.em,
                    fontFamily = LocalFont.current.normalFontFamily,
                    fontWeight = FontWeight.Medium,
                ),
            )
        }
    }
}
