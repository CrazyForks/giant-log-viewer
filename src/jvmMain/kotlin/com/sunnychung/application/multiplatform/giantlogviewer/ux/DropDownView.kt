package com.sunnychung.application.multiplatform.giantlogviewer.ux

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.sunnychung.application.giantlogviewer.generated.resources.Res
import com.sunnychung.application.giantlogviewer.generated.resources.dropdown_arrow
import com.sunnychung.application.giantlogviewer.generated.resources.error_cross
import com.sunnychung.application.multiplatform.giantlogviewer.ux.local.LocalColor
import com.sunnychung.application.multiplatform.giantlogviewer.ux.local.LocalFont
import com.sunnychung.lib.multiplatform.bigtext.ux.ContextMenuItemEntry

@Composable
fun DropDownView(
    modifier: Modifier = Modifier,
    selected: String,
//    onSelect: (String) -> Unit,
    entries: List<ContextMenuItemEntry>,
    testTag: String = "",
) {
    val color = LocalColor.current
    var isDropdownVisible by remember { mutableStateOf(false) }

    ContextMenuView(
        isVisible = isDropdownVisible,
        onDismiss = { isDropdownVisible = false },
        entries = entries,
        testTag = "",
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .border(1.dp, color.background)
            .clickable {
                isDropdownVisible = !isDropdownVisible
            }
            .padding(4.dp)
            .padding(start = 4.dp) // extra padding
    ) {
        BasicText(
            text = selected,
            style = TextStyle(
                color = color.dialogPrimary,
                fontFamily = LocalFont.current.normalFontFamily,
            ),
            modifier = Modifier
        )
        AppImage(
            resource = Res.drawable.dropdown_arrow,
            size = 16.dp,
            color = color.contextMenuText,
        )
    }
}
