package com.sunnychung.application.multiplatform.giantlogviewer.ux

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.CursorDropdownMenu
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.sunnychung.application.multiplatform.giantlogviewer.ux.local.LocalFont
import com.sunnychung.lib.multiplatform.bigtext.ux.ContextMenuItemEntry

@Composable
fun ContextMenuView(
    modifier: Modifier = Modifier,
    isVisible: Boolean,
    onDismiss: () -> Unit,
    entries: List<ContextMenuItemEntry>,
    testTag: String,
) {
    CursorDropdownMenu(
        expanded = isVisible,
        onDismissRequest = onDismiss,
        modifier = modifier.background(Color.Cyan)
    ) {
        entries.forEach {
            when (it.type) {
                ContextMenuItemEntry.Type.Button ->
                    BasicText(
                        text = it.displayText,
                        style = TextStyle(
                            color = if (it.isEnabled) Color.Black else Color.LightGray,
                            fontFamily = LocalFont.current.normalFontFamily,
                        ),
                        modifier = Modifier
                            .clickable {
                                if (it.isEnabled) {
                                    onDismiss()
                                    it.action()
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .fillMaxWidth()
                    )

                ContextMenuItemEntry.Type.Divider -> Column(
                    modifier = Modifier
                        .padding(vertical = 6.dp, horizontal = 6.dp)
                        .background(Color.Gray)
                        .fillMaxWidth()
                        .height(1.dp)
                ) {}
            }
        }
    }
}

val AppBigTextFieldContextMenu =
    @Composable { isVisible: Boolean, onDismiss: () -> Unit, entries: List<ContextMenuItemEntry>, testTag: String ->
        ContextMenuView(
            isVisible = isVisible,
            onDismiss = onDismiss,
            entries = entries,
            testTag = testTag,
        )
    }
