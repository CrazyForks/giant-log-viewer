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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.onExternalDrag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sunnychung.application.giantlogviewer.generated.resources.Res
import com.sunnychung.application.giantlogviewer.generated.resources.help
import com.sunnychung.application.giantlogviewer.generated.resources.info
import com.sunnychung.application.multiplatform.giantlogviewer.io.GiantFileTextPager
import com.sunnychung.application.multiplatform.giantlogviewer.model.SearchMode
import com.sunnychung.application.multiplatform.giantlogviewer.model.SearchOptions
import com.sunnychung.application.multiplatform.giantlogviewer.ux.local.LocalFont
import java.io.File
import java.net.URI
import java.util.regex.Pattern

@Composable
fun App() {
    var selectedFileName by remember { mutableStateOf("") }
    var isShowHelpWindow by remember { mutableStateOf(false) }
    var isShowAboutWindow by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(30.dp).background(Color(red = 0.4f, green = 0.4f, blue = 0.4f)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppImage(
                resource = Res.drawable.info,
                size = 20.dp,
                color = Color.White,
                modifier = Modifier.padding(5.dp)
                    .clickable {
                        isShowAboutWindow = true
                    }
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
                modifier = Modifier.padding(5.dp)
                    .clickable {
                        isShowHelpWindow = true
                    }
            )
        }

        AppMainContent(onSelectFile = { selectedFileName = it?.name ?: "" })

        HelpWindow(isVisible = isShowHelpWindow, onClose = { isShowHelpWindow = false })
        AboutWindow(isVisible = isShowAboutWindow, onClose = { isShowAboutWindow = false })
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun AppMainContent(modifier: Modifier = Modifier, onSelectFile: (File?) -> Unit) {
    var selectedFilePath by remember { mutableStateOf("") }

    val viewerFocusRequester = remember { FocusRequester() }
    var filePager: GiantFileTextPager? by remember { mutableStateOf(null) }

    var isSearchBarVisible by remember { mutableStateOf(false) }
    var searchEntry by remember { mutableStateOf("") }
    var searchOptions by remember { mutableStateOf(
        SearchOptions(
            isRegex = false,
            isCaseSensitive = true,
            isWholeWord = false
        )
    ) }
    var isSearchBackwardDefault by remember { mutableStateOf(true) }

    var searchCursor by remember(filePager) { mutableStateOf(0L) }
    var highlightByteRange by remember(filePager) { mutableStateOf(0L .. -1L) }

    fun currentSearchRegex(): Regex? {
        if (searchEntry.isEmpty()) {
            return null
        }
        val regexOption = if (searchOptions.isCaseSensitive) setOf() else setOf(RegexOption.IGNORE_CASE)
        try {
            val pattern = if (searchOptions.isRegex) {
                searchEntry.toRegex(regexOption)
            } else if (searchOptions.isWholeWord) {
                "\\b${Pattern.quote(searchEntry)}\\b".toRegex(regexOption)
            } else {
                Pattern.quote(searchEntry).toRegex(regexOption)
            }
            return pattern
        } catch (_: Throwable) {}
        return null
    }

    Column(modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
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

            GiantTextViewer(
                filePath = selectedFilePath,
                highlightByteRange = highlightByteRange,
                onPagerReady = { filePager = it },
                onNavigate = { searchCursor = it },
                onSearchRequest = {
                    if (it == SearchMode.None) {
                        isSearchBarVisible = false
                    } else {
                        isSearchBarVisible = true
                        isSearchBackwardDefault = (it == SearchMode.Backward)
                    }
                },
                modifier = Modifier.matchParentSize()
                    .focusRequester(viewerFocusRequester)
            )
        }

        if (isSearchBarVisible) {
            TextSearchBar(
                key = selectedFilePath.replace("/", "\\/"),
                text = searchEntry,
                onTextChange = { searchEntry = it },
                searchOptions = searchOptions,
                isSearchBackwardDefault = isSearchBackwardDefault,
                onToggleRegex = {
                    searchOptions = searchOptions.copy(isRegex = it)
                },
                onToggleCaseSensitive = {
                    searchOptions = searchOptions.copy(isCaseSensitive = it)
                },
                onToggleWholeWord = {
                    searchOptions = searchOptions.copy(isWholeWord = it)
                },
                onClickPrev = {
                    val regex = currentSearchRegex() ?: return@TextSearchBar
                    val pager = filePager ?: return@TextSearchBar
                    val result = pager.searchBackward(searchCursor, regex)
                    if (!result.isEmpty()) {
                        searchCursor = result.start
                        println("search found at $result")
                        pager.moveToRowOfBytePosition(result.start)
                    } else {
//                        searchCursor = 0
                    }
                    highlightByteRange = result
                },
                onClickNext = {
                    val regex = currentSearchRegex() ?: return@TextSearchBar
                    val pager = filePager ?: return@TextSearchBar
                    if (pager.viewportStartBytePosition < pager.fileReader.lengthInBytes()) {
                        val result = pager.searchAtAndForward(searchCursor + 1, regex)
                        if (!result.isEmpty()) {
                            searchCursor = result.start
                            println("search found at $result")
                            pager.moveToRowOfBytePosition(result.start)
                        } else {
//                            searchCursor = pager.fileReader.lengthInBytes()
                        }
                        highlightByteRange = result
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(.45f, .45f, .45f))
                    .padding(2.dp)
                    .onKeyEvent { e ->
//                        println("search onKeyEvent ${e.key}")
                        if (e.type == KeyEventType.KeyDown && e.key == Key.Escape) {
                            isSearchBarVisible = false
                            viewerFocusRequester.requestFocus()
                            true
                        } else {
                            false
                        }
                    }
            )
        }
    }
}
