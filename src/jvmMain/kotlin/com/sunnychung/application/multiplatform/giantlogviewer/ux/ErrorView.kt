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
import com.sunnychung.application.giantlogviewer.generated.resources.error_cross
import com.sunnychung.application.multiplatform.giantlogviewer.ux.local.LocalFont

@Composable
fun ErrorView(modifier: Modifier = Modifier, message: String) {
    Box(modifier.fillMaxSize().padding(32.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.align(Alignment.Center)) {
            AppImage(
                resource = Res.drawable.error_cross,
                size = 96.dp,
                color = Color(red = 0.7f, green = 0.4f, blue = 0.4f),
            )
            Spacer(Modifier.height(32.dp))
            BasicText(
                text = message,
                style = TextStyle(
                    color = Color(red = 0.7f, green = 0.4f, blue = 0.4f),
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
