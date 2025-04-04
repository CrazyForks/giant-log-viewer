package com.sunnychung.application.multiplatform.giantlogviewer.ux

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.DragData
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.onExternalDrag
import java.io.File
import java.net.URI

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun App() {
    var selectedFilePath by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onExternalDrag(
                onDragStart = { drag ->
                    println("drag: $drag | ${drag.dragData}")
                },
                onDrop = { drop ->
                    println("drop: $drop | ${drop.dragData}")
                    if (drop.dragData is DragData.FilesList) {
                        println("drop files: ${(drop.dragData as DragData.FilesList).readFiles()}")
                        val uri = URI((drop.dragData as DragData.FilesList).readFiles().first())

                        println("f: ${uri.scheme} ${File(uri).absolutePath}")
                        selectedFilePath = File(uri).absolutePath
                    }
                }
            )
            .background(Color.Cyan)
    ) {
        if (selectedFilePath.isEmpty()) {
            EmptyFileView()
            return@Box
        }

        val file = File(selectedFilePath)
        if (!file.exists()) {
            ErrorView(message = "The selected object no longer exists")
            return@Box
        }
        if (!file.isFile) {
            ErrorView(message = "The selected object is not a file")
            return@Box
        }
        if (!file.canRead()) {
            ErrorView(message = "The selected file is not readable")
            return@Box
        }

        GiantTextViewer(filePath = selectedFilePath, modifier = Modifier.fillMaxSize())
    }
}
