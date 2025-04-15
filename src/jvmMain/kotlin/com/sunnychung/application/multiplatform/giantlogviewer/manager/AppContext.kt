package com.sunnychung.application.multiplatform.giantlogviewer.manager

import com.sunnychung.application.multiplatform.giantlogviewer.repository.ThemePreferenceRepository
import java.io.File

class AppContext {
    val ResourceManager = ResourceManager()
    val MetadataManager = MetadataManager()
    val FileManager = TextFileManager()
    val PersistenceManager = PersistenceManager()

    val ThemePreferenceRepository = ThemePreferenceRepository()

    lateinit var dataDir: File

    companion object {
        internal var instance = AppContext()
    }
}
