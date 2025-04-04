package com.sunnychung.application.multiplatform.giantlogviewer.ux.local

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import com.sunnychung.application.multiplatform.giantlogviewer.manager.AppContext
import com.sunnychung.application.multiplatform.giantlogviewer.manager.AppRes

class AppFont(
    val normalFontFamily: FontFamily,
    val monospaceFontFamily: FontFamily,
)

internal val LocalFont = compositionLocalOf {
    AppFont(
        normalFontFamily = FontFamily(
            Font(
                identity = "Raleway-Regular",
                data = AppContext.instance.ResourceManager.getResource(AppRes.Font.RalewayRegular),
                weight = FontWeight.Normal,
            ),
            Font(
                identity = "Raleway-Medium",
                data = AppContext.instance.ResourceManager.getResource(AppRes.Font.RalewayMedium),
                weight = FontWeight.Medium,
            ),
        ),
        monospaceFontFamily = FontFamily(
            Font(
                identity = "PitagonSansMono-Regular",
                data = AppContext.instance.ResourceManager.getResource(AppRes.Font.PitagonSansMonoRegular),
                weight = FontWeight.Normal,
            ),
            Font(
                identity = "PitagonSansMono-Bold",
                data = AppContext.instance.ResourceManager.getResource(AppRes.Font.PitagonSansMonoBold),
                weight = FontWeight.Bold,
            ),
        ),
    )
}
