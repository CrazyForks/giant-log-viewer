package com.sunnychung.application.multiplatform.giantlogviewer

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.sunnychung.application.multiplatform.giantlogviewer.manager.AppContext
import com.sunnychung.application.multiplatform.giantlogviewer.ux.App
import kotlinx.coroutines.runBlocking

fun main(args: Array<String>) {
    System.setProperty("apple.awt.application.appearance", "system")

    runBlocking {
        AppContext.instance.ResourceManager.loadAllResources()
    }

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Giant Log Viewer",
        ) {
            App()
        }
    }
}
