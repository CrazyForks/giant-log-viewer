package com.sunnychung.application.multiplatform.giantlogviewer.manager

class AppContext {
    val ResourceManager = ResourceManager()

    companion object {
        internal var instance = AppContext()
    }
}
