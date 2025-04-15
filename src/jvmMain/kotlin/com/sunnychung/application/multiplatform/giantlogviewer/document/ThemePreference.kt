package com.sunnychung.application.multiplatform.giantlogviewer.document

import com.sunnychung.application.multiplatform.giantlogviewer.annotation.DocumentRoot
import com.sunnychung.application.multiplatform.giantlogviewer.annotation.Persisted
import kotlinx.serialization.Serializable

@DocumentRoot
@Persisted
@Serializable
data class ThemeDocument(var themes: ThemePreference) : Document<ThemeDI> {
    override val id: ThemeDI = ThemeDI
}

@Persisted
@Serializable
data object ThemeDI : DocumentIdentifier(/*type = PersistenceDocumentType.Theme*/)

@Persisted
@Serializable
data class ThemePreference(
    var selectedThemeType: ThemeType?
)

enum class ThemeType {
    Light, Dark
}
