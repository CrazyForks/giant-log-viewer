package com.sunnychung.application.multiplatform.giantlogviewer.ux

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import com.sunnychung.application.multiplatform.giantlogviewer.extension.setMinimumSize
import com.sunnychung.application.multiplatform.giantlogviewer.ux.local.LocalFont
import com.sunnychung.lib.android.composabletable.ux.Table

@Composable
fun HelpWindow(isVisible: Boolean, onClose: () -> Unit) {
    Window(
        visible = isVisible,
        onCloseRequest = onClose,
        title = "Help",
        state = WindowState(
            position = WindowPosition.Aligned(Alignment.Center),
            width = 600.dp,
            height = 350.dp,
        ),
    ) {
        setMinimumSize(width = 600.dp, 250.dp)

        Row(Modifier.background(Color.Cyan)) {
            KeyBindingTable(
                title = buildAnnotatedString {
                    append("The ")
                    withStyle(SpanStyle(fontFamily = LocalFont.current.monospaceFontFamily)) {
                        append("less")
                    }
                    append(" style")
                },
                keyBindings = listOf(
                    KeyBinding("↓", "Next row"),
                    KeyBinding("↑", "Previous row"),
                    KeyBinding("f", "One window forward"),
                    KeyBinding("b", "One window backward"),
                    KeyBinding("⇧G", "End of file"),
                    KeyBinding("g", "Start of file"),
                    KeyBinding("⇧/", "Search backward"),
                    KeyBinding("/", "Search forward"),
                    KeyBinding("Esc", "Exit search"),
                ),
                modifier = Modifier.weight(.42f).fillMaxHeight()
            )
            KeyBindingTable(
                title = AnnotatedString("The memory-less style"),
                keyBindings = listOf(
                    KeyBinding("↓", "Next row"),
                    KeyBinding("↑", "Previous row"),
                    KeyBinding("Alt/Option-↓", "One window forward"),
                    KeyBinding("Alt/Option-↑", "One window backward"),
                    KeyBinding("Ctrl/Command-↓", "End of file"),
                    KeyBinding("Ctrl/Command-↑", "Start of file"),
                    KeyBinding("Ctrl/Command-F", "Search forward"),
                    KeyBinding("Esc", "Exit search"),
                    KeyBinding("Enter", "Search next"),
                    KeyBinding("Shift-Enter", "Search previous"),
                ),
                modifier = Modifier.weight(.58f).fillMaxHeight()
            )
        }
    }
}

@Composable
private fun KeyBindingTable(modifier: Modifier = Modifier, title: AnnotatedString, keyBindings: List<KeyBinding>) {
    val primaryColor = Color(red = 0f, green = 0f, blue = 0.19f)
    val density = LocalDensity.current

    var componentWidth by remember { mutableIntStateOf(0) }
    var componentHeight by remember { mutableIntStateOf(0) }

    with (density) {
        val textMeasurer = rememberTextMeasurer()
        val titleTextStyle = TextStyle(
            fontSize = 16.sp,
            color = primaryColor,
            fontFamily = LocalFont.current.normalFontFamily
        )
        val titleSize = textMeasurer.measure(title, titleTextStyle).size
        val titleHorizontalPadding = 12.dp.toPx()

        Column(modifier
            .background(Color.Cyan)
            .onGloballyPositioned {
                componentWidth = it.size.width
                componentHeight = it.size.height
            }
            .drawBehind {
                drawRect(
                    color = primaryColor,
                    style = Stroke(),
                    topLeft = Offset(15.dp.toPx(), 15.dp.toPx()),
                    size = Size(componentWidth - 30.dp.toPx(), componentHeight - 30.dp.toPx()),
                )
                drawRect(
                    color = Color.Cyan,
                    topLeft = Offset(
                        x = (componentWidth - titleSize.width) / 2f - titleHorizontalPadding,
                        y = 0f,
                    ),
                    size = Size(
                        width = titleSize.width + titleHorizontalPadding * 2,
                        height = 31.dp.toPx(),
                    )
                )
                drawText(
                    text = title,
                    textMeasurer = textMeasurer,
                    style = titleTextStyle,
                    topLeft = Offset(
                        x = (componentWidth - titleSize.width) / 2f,
                        y = (31.dp.toPx() - titleSize.height) / 2,
                    ),
                )
            }
            .padding(31.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            Table(
                rowCount = keyBindings.size,
                columnCount = 2,
            ) { rowIndex, columnIndex ->
                BasicText(
                    text = if (columnIndex == 0) keyBindings[rowIndex].keys else keyBindings[rowIndex].description,
                    style = TextStyle(
                        color = primaryColor,
                        fontFamily = LocalFont.current.normalFontFamily
                    ),
                    modifier = Modifier.padding(4.dp)
                )
            }
        }
    }
}

private data class KeyBinding(val keys: String, val description: String)
