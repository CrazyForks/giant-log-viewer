package com.sunnychung.application.multiplatform.giantlogviewer.manager

class AppContext {
    val ResourceManager = ResourceManager()
    val MetadataManager = MetadataManager()

    companion object {
        internal var instance = AppContext()
    }
}
