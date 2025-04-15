package com.sunnychung.application.multiplatform.giantlogviewer.repository

import com.sunnychung.application.multiplatform.giantlogviewer.document.ThemeDI
import com.sunnychung.application.multiplatform.giantlogviewer.document.ThemeDocument
import com.sunnychung.application.multiplatform.giantlogviewer.document.ThemePreference
import kotlinx.serialization.serializer

class ThemePreferenceRepository : BaseRepository<ThemeDocument, ThemeDI>(serializer()) {
    override fun relativeFilePath(id: ThemeDI): String = "ThemePreference.json"

    override fun default(): ThemeDocument = ThemeDocument(
        themes = ThemePreference(
            selectedThemeType = null,
        )
    )
}
