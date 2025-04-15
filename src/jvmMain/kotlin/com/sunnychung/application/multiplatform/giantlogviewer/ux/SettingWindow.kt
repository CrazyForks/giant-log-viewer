package com.sunnychung.application.multiplatform.giantlogviewer.ux

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import com.sunnychung.application.giantlogviewer.generated.resources.Res
import com.sunnychung.application.giantlogviewer.generated.resources.appicon
import com.sunnychung.application.multiplatform.giantlogviewer.document.ThemeDI
import com.sunnychung.application.multiplatform.giantlogviewer.document.ThemeType
import com.sunnychung.application.multiplatform.giantlogviewer.extension.setMinimumSize
import com.sunnychung.application.multiplatform.giantlogviewer.extension.subscribeStateToEntity
import com.sunnychung.application.multiplatform.giantlogviewer.manager.AppContext
import com.sunnychung.application.multiplatform.giantlogviewer.ux.local.LocalColor
import com.sunnychung.application.multiplatform.giantlogviewer.ux.local.LocalFont
import com.sunnychung.lib.android.composabletable.ux.Table
import com.sunnychung.lib.multiplatform.bigtext.ux.ContextMenuItemEntry
import com.sunnychung.lib.multiplatform.kdatetime.extension.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import java.awt.Desktop
import java.io.File
import kotlin.random.Random

@Composable
fun SettingWindow(isVisible: Boolean, onClose: () -> Unit) {
    Window(
        visible = isVisible,
        onCloseRequest = onClose,
        title = "Settings",
        icon = painterResource(Res.drawable.appicon),
        state = WindowState(
            position = WindowPosition.Aligned(Alignment.Center),
            width = 300.dp,
            height = 150.dp,
        ),
        onKeyEvent = { e ->
            if (e.type == KeyEventType.KeyDown && e.key == Key.Escape) {
                onClose()
                true
            } else {
                false
            }
        },
    ) {
        setMinimumSize(width = 300.dp, 150.dp)

        val colors = LocalColor.current

        Box(Modifier.fillMaxSize().background(colors.dialogBackground)) {
            SettingView()
        }
    }
}

private val SettingLabels = listOf("Color Theme")

@Composable
fun SettingView() {
    val color = LocalColor.current
//    Table(
//        rowCount = 1,
//        columnCount = 2,
//        modifier = Modifier.fillMaxSize().padding(4.dp)
//    ) { row, col ->
//        if (col == 0) {
//            BasicText(
//                text = SettingLabels[row],
//                style = TextStyle(
//                    color = color.dialogPrimary,
//                    fontFamily = LocalFont.current.normalFontFamily,
//                ),
//                modifier = Modifier.padding(4.dp)
//            )
//        } else {
//            Row(Modifier.fillMaxWidth().padding(4.dp)) {
//                when (row) {
//                    0 -> {
//                        DropDownView(
//                            selected = "Dark",
////                        onSelect = {},
//                            entries = ThemeType.entries.map {
//                                ContextMenuItemEntry(
//                                    type = ContextMenuItemEntry.Type.Button,
//                                    displayText = it.name,
//                                    isEnabled = true,
//                                    testTag = it.name,
//                                    action = {
//
//                                    }
//                                )
//                            }
//                        )
//                    }
//                }
//            }
//
//        }
//    }

    val themePreferenceRepo = AppContext.instance.ThemePreferenceRepository
    val themePreference = themePreferenceRepo
        .subscribeStateToEntity(ThemeDI)
        .themes

    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize().padding(4.dp)) {
        val coroutineScope = rememberCoroutineScope()
        var forceRecompose by remember { mutableLongStateOf(0L) }
        val appDataDir = AppContext.instance.dataDir
        forceRecompose // force update the evaluation of `appDataDir.exists()`
        TextToggleButton(
            text = "Open Setting Directory",
            isEnabled = appDataDir.exists(),
            isSelected = false,
            onToggle = {
                Desktop.getDesktop().open(appDataDir)
            },
            innerPadding = PaddingValues(6.dp),
            modifier = Modifier.padding(4.dp)
        )

        SettingRow(label = "Color Theme") {
            DropDownView(
                selected = themePreference.selectedThemeType?.name ?: "Default",
//                        onSelect = {},
                entries = (listOf(null) + ThemeType.entries).map {
                    ContextMenuItemEntry(
                        type = ContextMenuItemEntry.Type.Button,
                        displayText = it?.name ?: "Default",
                        isEnabled = true,
                        testTag = it?.name ?: "",
                        action = {
                            val isForceRecompose = !appDataDir.exists()

                            themePreference.selectedThemeType = it
                            themePreferenceRepo.update(ThemeDI)

                            if (isForceRecompose) {
                                coroutineScope.launch {
                                    do {
                                        delay(200.milliseconds().millis)
                                        forceRecompose = Random.nextLong()
                                    } while (!appDataDir.exists())
                                }
                            }
                        }
                    )
                },
                modifier = Modifier.padding(4.dp)
            )
        }
    }
}

@Composable
fun SettingRow(modifier: Modifier = Modifier, label: String, content: @Composable () -> Unit) {
    val color = LocalColor.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        BasicText(
            text = label,
            style = TextStyle(
                color = color.dialogPrimary,
                fontFamily = LocalFont.current.normalFontFamily,
            ),
            modifier = Modifier.width(140.dp).padding(4.dp)
        )
        content()
    }
}
