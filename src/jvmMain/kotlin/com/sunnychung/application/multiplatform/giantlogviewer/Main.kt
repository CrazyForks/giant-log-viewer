package com.sunnychung.application.multiplatform.giantlogviewer

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.sunnychung.application.multiplatform.giantlogviewer.ux.App

fun main(args: Array<String>) {
    System.setProperty("apple.awt.application.appearance", "system")

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Giant Log Viewer",
        ) {
            App()
        }
    }
}
