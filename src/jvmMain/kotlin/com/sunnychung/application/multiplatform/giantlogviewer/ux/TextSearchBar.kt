package com.sunnychung.application.multiplatform.giantlogviewer.ux

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.sunnychung.application.multiplatform.giantlogviewer.model.SearchOptions
import com.sunnychung.application.multiplatform.giantlogviewer.ux.local.LocalFont

private val SEARCH_OPTION_BUTTON_WIDTH = 24.dp

@Composable
fun TextSearchBar(
    modifier: Modifier = Modifier,
    key: String,
    text: String,
    onTextChange: (String) -> Unit,
//    statusText: String,
    searchOptions: SearchOptions,
    isSearchBackwardDefault: Boolean,
    onToggleRegex: (Boolean) -> Unit,
    onToggleCaseSensitive: (Boolean) -> Unit,
    onToggleWholeWord: (Boolean) -> Unit,
    onClickPrev: () -> Unit,
    onClickNext: () -> Unit,
) {
    Row(
        modifier = modifier.onPreviewKeyEvent { e ->
            if (e.type == KeyEventType.KeyDown && e.key == Key.Enter) {
                var isBackward = isSearchBackwardDefault
                if (e.isShiftPressed) {
                    isBackward = !isBackward
                }
                if (isBackward) {
                    onClickPrev()
                } else {
                    onClickNext()
                }
                true
            } else {
                false
            }
        }, verticalAlignment = Alignment.CenterVertically
    ) {
        AppTextField(
            key = "$key/SearchText",
            value = text,
            onValueChange = onTextChange,
            placeholder = {
                BasicText(
                    text = "Text/Pattern to Search for",
                    style = TextStyle(
                        color = Color.Gray,
                        fontFamily = LocalFont.current.normalFontFamily,
                    )
                )
            },
            textStyle = TextStyle(
                fontFamily = LocalFont.current.normalFontFamily,
            ),
            maxLines = 1,
            singleLine = true, // not allow '\n'
            contentPadding = PaddingValues(4.dp),
            modifier = Modifier.weight(1f),
        )
        TextToggleButton(
            text = ".*",
            isSelected = searchOptions.isRegex,
            onToggle = onToggleRegex,
            modifier = Modifier.width(SEARCH_OPTION_BUTTON_WIDTH).focusProperties { canFocus = false },
        )
        TextToggleButton(
            text = "Aa",
            isSelected = searchOptions.isCaseSensitive,
            onToggle = onToggleCaseSensitive,
            modifier = Modifier.width(SEARCH_OPTION_BUTTON_WIDTH).focusProperties { canFocus = false },
        )
        TextToggleButton(
            text = "W",
            isSelected = searchOptions.isWholeWord,
            isEnabled = !searchOptions.isRegex,
            onToggle = onToggleWholeWord,
            modifier = Modifier.width(SEARCH_OPTION_BUTTON_WIDTH).focusProperties { canFocus = false },
        )
        TextToggleButton(
            text = "↑",
            isSelected = isSearchBackwardDefault,
            onToggle = { onClickPrev() },
            modifier = Modifier.width(SEARCH_OPTION_BUTTON_WIDTH).focusProperties { canFocus = false },
        )
        TextToggleButton(
            text = "↓",
            isSelected = !isSearchBackwardDefault,
            onToggle = { onClickNext() },
            modifier = Modifier.width(SEARCH_OPTION_BUTTON_WIDTH).focusProperties { canFocus = false },
        )
    }
}
