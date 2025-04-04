package com.sunnychung.application.multiplatform.giantlogviewer.ux

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.DragData
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.onExternalDrag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sunnychung.application.giantlogviewer.generated.resources.Res
import com.sunnychung.application.giantlogviewer.generated.resources.help
import com.sunnychung.application.giantlogviewer.generated.resources.info
import com.sunnychung.application.multiplatform.giantlogviewer.ux.local.LocalFont
import java.io.File
import java.net.URI

@Composable
fun App() {
    var selectedFileName by remember { mutableStateOf("") }
    var isShowHelpWindow by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(30.dp).background(Color(red = 0.4f, green = 0.4f, blue = 0.4f)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppImage(
                resource = Res.drawable.info,
                size = 20.dp,
                color = Color.White,
                modifier = Modifier.padding(4.dp)
            )
            BasicText(
                text = selectedFileName,
                style = TextStyle(
                    color = Color.White,
                    fontFamily = LocalFont.current.normalFontFamily,
                    textAlign = TextAlign.Center,
                ),
                softWrap = false,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
            )
            AppImage(
                resource = Res.drawable.help,
                size = 20.dp,
                color = Color.White,
                modifier = Modifier.padding(4.dp)
                    .clickable {
                        isShowHelpWindow = true
                    }
            )
        }

        AppMainContent(onSelectFile = { selectedFileName = it?.name ?: "" })

        HelpWindow(isVisible = isShowHelpWindow, onClose = { isShowHelpWindow = false })
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun AppMainContent(modifier: Modifier = Modifier, onSelectFile: (File?) -> Unit) {
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
            onSelectFile(null)
            return@Box
        }

        val file = File(selectedFilePath)
        if (!file.exists()) {
            ErrorView(message = "The selected object no longer exists")
            onSelectFile(null)
            return@Box
        }
        if (!file.isFile) {
            ErrorView(message = "The selected object is not a file")
            onSelectFile(null)
            return@Box
        }
        if (!file.canRead()) {
            ErrorView(message = "The selected file is not readable")
            onSelectFile(null)
            return@Box
        }

        onSelectFile(file)

        GiantTextViewer(filePath = selectedFilePath, modifier = Modifier.fillMaxSize())
    }
}
