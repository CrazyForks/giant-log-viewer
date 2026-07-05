package com.sunnychung.application.multiplatform.giantlogviewer.ux

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.onDrag
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sunnychung.application.multiplatform.giantlogviewer.io.ComposeGiantFileTextPager
import com.sunnychung.application.multiplatform.giantlogviewer.io.GiantFileReader
import com.sunnychung.application.multiplatform.giantlogviewer.io.GiantFileTextPager
import com.sunnychung.application.multiplatform.giantlogviewer.io.ResolvedTextEncoding
import com.sunnychung.application.multiplatform.giantlogviewer.io.TextEncoding
import com.sunnychung.application.multiplatform.giantlogviewer.io.Viewport
import com.sunnychung.application.multiplatform.giantlogviewer.io.displayName
import com.sunnychung.application.multiplatform.giantlogviewer.io.selectableTextEncodings
import com.sunnychung.application.multiplatform.giantlogviewer.layout.MonospaceBidirectionalTextLayouter
import com.sunnychung.application.multiplatform.giantlogviewer.model.SearchMode
import com.sunnychung.application.multiplatform.giantlogviewer.ux.local.AppFont
import com.sunnychung.application.multiplatform.giantlogviewer.ux.local.LocalColor
import com.sunnychung.application.multiplatform.giantlogviewer.ux.local.LocalFont
import com.sunnychung.application.multiplatform.giantlogviewer.viewstate.FileViewState
import com.sunnychung.lib.multiplatform.bigtext.annotation.TemporaryBigTextApi
import com.sunnychung.lib.multiplatform.bigtext.compose.ComposeUnicodeCharMeasurer
import com.sunnychung.lib.multiplatform.bigtext.extension.isCtrlOrCmdPressed
import com.sunnychung.lib.multiplatform.bigtext.ux.ContextMenuItemEntry
import com.sunnychung.lib.multiplatform.bigtext.util.debouncedStateOf
import com.sunnychung.lib.multiplatform.bigtext.util.string
import com.sunnychung.lib.multiplatform.kdatetime.KInstant
import com.sunnychung.lib.multiplatform.kdatetime.extension.milliseconds
import com.sunnychung.lib.multiplatform.kdatetime.extension.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor

// TODO onPagerReady is an anti-pattern -- reverse of data flow. refactor it.
@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class, TemporaryBigTextApi::class)
@Composable
fun GiantTextViewer(
    modifier: Modifier,
    fileViewState: FileViewState,
    filePath: String,
    refreshKey: Int = 0,
    highlightByteRange: LongRange,
    onPagerReady: (GiantFileTextPager?) -> Unit,
    onNavigate: (bytePosition: Long) -> Unit,
    onDocumentContentChanged: () -> Unit,
    onSearchRequest: (SearchMode) -> Unit,
    bottomContent: @Composable () -> Unit = {},
    shouldRequestFocus: Boolean = true,
) {
    val file = File(filePath)
    if (!file.isFile) {
        println("File is not a file")
        return
    }

    println("recompose $filePath $refreshKey")

    var contentComponentWidth by remember { mutableIntStateOf(0) }
    var contentComponentHeight by remember { mutableIntStateOf(0) }

    val density = LocalDensity.current
    val font = LocalFont.current
    val colors = LocalColor.current

    val isNavigationLocked = fileViewState.isFollowing

    val textMeasurer = rememberTextMeasurer(0)
    val textStyle = remember(font, colors) {
        TextStyle(
            fontFamily = font.monospaceFontFamily,
            color = colors.fileBodyTheme.plainText,
        )
    }
    val charMeasurer = remember(density, textStyle) { ComposeUnicodeCharMeasurer(textMeasurer, textStyle) }
    val textLayouter = remember(charMeasurer) { MonospaceBidirectionalTextLayouter(charMeasurer) }

    var selectedTextEncoding by remember(filePath) { mutableStateOf(TextEncoding.Auto) }
    var encodingReloadKey by remember(filePath, refreshKey) { mutableIntStateOf(0) }
    var lastModifiedMillis by remember(filePath, refreshKey, encodingReloadKey) {
        mutableLongStateOf(file.lastModified())
    }

    fun reloadFileForEncoding(encoding: TextEncoding) {
        selectedTextEncoding = encoding
        fileViewState.isFollowing = false
        fileViewState.fileLength = file.length()
        lastModifiedMillis = file.lastModified()
        encodingReloadKey++
        onDocumentContentChanged()
        onNavigate(0L)
    }

    val fileReader = remember(filePath, refreshKey, selectedTextEncoding, encodingReloadKey) {
        GiantFileReader(filePath, initialFileLength = fileViewState.fileLength, textEncoding = selectedTextEncoding)
    }
    val filePager: GiantFileTextPager = remember(fileReader, textLayouter) {
        ComposeGiantFileTextPager(fileReader, textLayouter, fileViewState.fileLength)
    }
    val fileLength = fileViewState.fileLength
    fileReader.fileLength = fileLength
    filePager.fileLength = fileLength

    val clipboardManager = LocalClipboardManager.current

    val focusRequester = remember { FocusRequester() }

    var draggedPoint by remember { mutableStateOf<Offset>(Offset.Zero) }
    var dragStartBytePosition by remember(filePath, refreshKey, encodingReloadKey) { mutableLongStateOf(0L) }
    var dragEndBytePosition by remember(filePath, refreshKey, encodingReloadKey) { mutableLongStateOf(0L) }

    val (contentWidth, isContentWidthLatest) = debouncedStateOf(200.milliseconds(), tolerateCount = 1, filePager) {
        contentComponentWidth
    }

    if (isContentWidthLatest) {
        remember(filePager, contentWidth, contentComponentHeight, density) {
            filePager.viewport = Viewport(contentWidth, contentComponentHeight, density.density)
        }
    }

    val selection = minOf(dragStartBytePosition, dragEndBytePosition) ..<
        maxOf(dragStartBytePosition, dragEndBytePosition)

    var scrollY by remember(filePager.viewportStartBytePosition) {
        mutableStateOf(0f)
    }
    val scrollState = rememberScrollableState { delta ->
        if (isNavigationLocked) {
            return@rememberScrollableState 0f
        }

        val reversedDelta = -delta
        if (reversedDelta < 0 && filePager.viewportStartBytePosition <= 0) {
            return@rememberScrollableState 0f
        }
        if (reversedDelta > 0 && filePager.viewportStartBytePosition >= fileLength) {
            return@rememberScrollableState 0f
        }
        scrollY += reversedDelta
        val rowHeight = charMeasurer.getRowHeight()
        if (scrollY >= rowHeight) {
            val numOfRowsToScroll = (scrollY / rowHeight).toLong()
            scrollY -= rowHeight * numOfRowsToScroll.toFloat()
            filePager.moveToNextRow(numOfRowsToScroll)
            onNavigate(filePager.viewportStartBytePosition)
        } else if (scrollY <= -rowHeight) {
            val numOfRowsToScroll = (abs(scrollY) / rowHeight).toLong()
            scrollY += rowHeight * numOfRowsToScroll.toFloat()
            filePager.moveToPrevRow(numOfRowsToScroll)
            onNavigate(filePager.viewportStartBytePosition)
        }
        delta
    }

    LaunchedEffect(filePager) {
        onPagerReady(filePager)
    }

    fun copySelection() {
        if (!selection.isEmpty()) {
            val length = (selection.endExclusive - selection.start)
                .coerceAtMost(fileReader.blockSize.toLong()) // TODO support copying across blocks
                .toInt()
            val (text, _) = fileReader.readString(selection.start, length)
            clipboardManager.setText(AnnotatedString(text = text))
        }
    }

    fun navigate(action: GiantFileTextPager.() -> Unit): Boolean {
        if (isNavigationLocked) return false
        filePager.action()
        onNavigate(filePager.viewportStartBytePosition)
        return true
    }

    Column(modifier
//        .onKeyEvent { e ->
        .onPreviewKeyEvent { e ->
            println("onKeyEvent ${e.key}")
            val startTime = KInstant.now()
            if (e.type == KeyEventType.KeyDown) {
                when {
                    e.key == Key.F && e.isCtrlOrCmdPressed() -> onSearchRequest(SearchMode.Forward)

                    e.key == Key.F && e.isShiftPressed -> {
                        fileViewState.isFollowing = true
                        return@onPreviewKeyEvent true
                    }

                    e.key == Key.F -> return@onPreviewKeyEvent navigate { moveToNextPage() }
                    e.key == Key.B -> return@onPreviewKeyEvent navigate { moveToPrevPage() }
                    e.key == Key.DirectionUp && e.isAltPressed -> return@onPreviewKeyEvent navigate { moveToPrevPage() }
                    e.key == Key.DirectionDown && e.isAltPressed -> return@onPreviewKeyEvent navigate { moveToNextPage() }

                    e.key == Key.G && e.isShiftPressed -> return@onPreviewKeyEvent navigate { moveToTheLastRow() }
                    e.key == Key.DirectionDown && e.isCtrlOrCmdPressed() -> return@onPreviewKeyEvent navigate { moveToTheLastRow() }
                    e.key == Key.G -> return@onPreviewKeyEvent navigate { moveToTheFirstRow() }
                    e.key == Key.DirectionUp && e.isCtrlOrCmdPressed() -> return@onPreviewKeyEvent navigate { moveToTheFirstRow() }

                    e.key == Key.DirectionUp -> return@onPreviewKeyEvent navigate { moveToPrevRow() }
                    e.key == Key.DirectionDown -> return@onPreviewKeyEvent navigate { moveToNextRow() }

                    e.key == Key.Slash && e.isShiftPressed -> onSearchRequest(SearchMode.Backward)
                    e.key == Key.Slash -> onSearchRequest(SearchMode.Forward)

                    e.key == Key.Escape -> {
                        if (fileViewState.isFollowing) {
                            fileViewState.isFollowing = false
                            return@onPreviewKeyEvent true
                        }
                        onSearchRequest(SearchMode.None)
                    }

                    e.key == Key.C && e.isCtrlPressed -> {
                        if (fileViewState.isFollowing) {
                            fileViewState.isFollowing = false
                            // continue to remaining actions
                        }
                    }

                    e.key == Key.C && e.isCtrlOrCmdPressed() -> copySelection()

                    else -> {
//                    return@onKeyEvent false
                        return@onPreviewKeyEvent false
                    }
                }
                println("onKeyEvent handled in ${KInstant.now() - startTime}")
                return@onPreviewKeyEvent true
            }
            false
        }
        .focusRequester(focusRequester)
        .focusable()
    ) {
        Row(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .onPointerEvent(eventType = PointerEventType.Press) {
                    // not sure which Compose bug leading to require implementing "click to focus" manually
                    focusRequester.requestFocus()

                    // clear selection
                    dragStartBytePosition = 0L
                    dragEndBytePosition = 0L
                }
        ) {
            val startTime = KInstant.now()
            Box(modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .onGloballyPositioned {
                    contentComponentWidth = it.size.width
                    contentComponentHeight = it.size.height
                }
                .onDrag(
                    onDragStart = {
                        draggedPoint = it
                        dragStartBytePosition = findBytePositionByCoordinatePx(filePager, it)
                    },
                    onDrag = {
                        draggedPoint += it
                        dragEndBytePosition = findBytePositionByCoordinatePx(filePager, draggedPoint)
                    },
                    onDragEnd = {
                        draggedPoint = Offset.Zero
                    }
                )
                .scrollable(scrollState, Orientation.Vertical)
            ) {
                val textToDisplay: List<CharSequence> = filePager.textInViewport
                val bytePositionsOfDisplay: List<Long> = filePager.startBytePositionsInViewport
//        println("textToDisplay:\n$textToDisplay")
                val lineHeight = charMeasurer.getRowHeight()
                Canvas(modifier = Modifier.matchParentSize()) {
//                with(density) {
                    textToDisplay.forEachIndexed { rowRelativeIndex, row ->
                        var unicodeSequence: CharSequence? = null
                        val rowYOffset = rowRelativeIndex * lineHeight
                        val globalXOffset = 0f
                        var accumulateXOffset = 0f
                        var bytePosition = bytePositionsOfDisplay[rowRelativeIndex]
                        row.indices.forEach { i ->
                            var charAnnotated = row.subSequence(i, i + 1)
                            val char = charAnnotated.first()
                            if (char.isHighSurrogate()) {
                                unicodeSequence = charAnnotated
                                return@forEach
                            } else if (char.isLowSurrogate() && unicodeSequence != null) {
                                charAnnotated = buildString {
                                    append(unicodeSequence)
                                    append(charAnnotated)
                                }
                            }
                            val textLayoutResult = charMeasurer.getTextLayoutResult(charAnnotated, null)
                            val charWidth = textLayouter.measureCharWidth(charAnnotated)
                            val charYOffset = textLayouter.measureCharYOffset(charAnnotated)

                            if (bytePosition in highlightByteRange) {
                                drawRect(
                                    color = colors.fileBodyTheme.searchMatchBackground,
                                    topLeft = Offset(globalXOffset + accumulateXOffset, rowYOffset + charYOffset),
                                    size = Size(charWidth, lineHeight),
                                )
                            }

                            if (bytePosition in selection) {
                                drawRect(
                                    color = colors.fileBodyTheme.selectionBackground,
                                    topLeft = Offset(globalXOffset + accumulateXOffset, rowYOffset + charYOffset),
                                    size = Size(charWidth, lineHeight),
                                )
                            }

//                        BasicText(
//                            charAnnotated.annotatedString(),
//                            style = textStyle,
//                            maxLines = 1,
//                            softWrap = false,
//                            modifier = Modifier
//                                .offset((globalXOffset + accumulateXOffset).toDp(), (rowYOffset + charYOffset).toDp())
//                        )

                            if (textLayoutResult != null) { // use cache to avoid object allocations and interop calls
                                drawText(
                                    textLayoutResult = textLayoutResult,
                                    topLeft = Offset(globalXOffset + accumulateXOffset, rowYOffset + charYOffset),
                                )
                            } else {
                                drawText(
                                    textMeasurer = textMeasurer,
                                    text = charAnnotated.string(),
                                    topLeft = Offset(globalXOffset + accumulateXOffset, rowYOffset + charYOffset),
                                    size = Size(charWidth, lineHeight),
                                    style = textStyle,
                                    overflow = TextOverflow.Visible,
                                    softWrap = false,
                                    maxLines = 1,
                                )
                            }

                            accumulateXOffset += charWidth
                            bytePosition += filePager.encodedLengthOfText(charAnnotated.string())
                        }
                    }
//                }
                }
            }

            var dragY by remember { mutableStateOf(0f) }

            fun moveToPositionByDragY(dragY: Float) {
                val confinedDragY = dragY.coerceIn(0f .. contentComponentHeight.toFloat())
                val desiredPosition = (fileViewState.fileLength * (confinedDragY.toDouble() / contentComponentHeight.toDouble())).toLong()
                filePager.moveToRowOfBytePosition(desiredPosition)
            }

            VerticalIndicatorView(
                value = if (fileViewState.fileLength > 0) {
                    (filePager.viewportStartBytePosition.toDouble() / fileViewState.fileLength.toDouble()).toFloat()
                } else {
                    1f
                },
                modifier = Modifier.width(20.dp).fillMaxHeight()
                    .onPointerEvent(eventType = PointerEventType.Press) {
                        dragY = it.changes.first().position.y
                        moveToPositionByDragY(dragY)
                    }
                    .onDrag {
                        dragY += it.y
                        moveToPositionByDragY(dragY)
                    }
            )

            println("prepare rendering in ${KInstant.now() - startTime}")
        }

        bottomContent()

        GiantTextViewerStatusBar(
            filePager = filePager,
            fileLength = fileLength,
            lastModifiedMillis = lastModifiedMillis,
            selectedTextEncoding = selectedTextEncoding,
            resolvedTextEncoding = fileReader.resolvedTextEncoding,
            onSelectTextEncoding = {
                if (it != selectedTextEncoding || it == TextEncoding.Auto) {
                    reloadFileForEncoding(it)
                }
            },
        )
    }

    LaunchedEffect(filePath, refreshKey, encodingReloadKey, shouldRequestFocus) {
        if (shouldRequestFocus) {
            focusRequester.requestFocus()
        }
    }

    LaunchedEffect(filePath, fileViewState.isFollowing) {
        if (fileViewState.isFollowing) {
            launch {
                while (fileViewState.isFollowing) {
                    val currentFileLength = fileViewState.file.length()
                    val currentLastModifiedMillis = fileViewState.file.lastModified()
                    val isFileContentChanged = currentFileLength != fileViewState.fileLength ||
                        currentLastModifiedMillis != lastModifiedMillis

                    fileViewState.fileLength = currentFileLength
                    lastModifiedMillis = currentLastModifiedMillis
                    filePager.moveToTheLastRow()
                    filePager.moveToPrevRow(rows = (filePager.numOfRowsInViewport - 3L).coerceAtLeast(0L))
                    onNavigate(filePager.viewportStartBytePosition)
                    if (isFileContentChanged) {
                        onDocumentContentChanged()
                    }
                    delay(1.seconds().millis)
                }
            }
        }
    }

    DisposableEffect(fileReader) {
        onDispose {
            println("Disposing ${fileReader.filePath}")
            fileReader.close()
            println("Disposed ${fileReader.filePath}")
        }
    }
}

@Composable
private fun GiantTextViewerStatusBar(
    filePager: GiantFileTextPager,
    fileLength: Long,
    lastModifiedMillis: Long,
    selectedTextEncoding: TextEncoding,
    resolvedTextEncoding: ResolvedTextEncoding,
    onSelectTextEncoding: (TextEncoding) -> Unit,
) {
    val colors = LocalColor.current
    val font = LocalFont.current
    val numberFormat = remember { NumberFormat.getIntegerInstance(Locale.US) }
    val statusTextMeasurer = rememberTextMeasurer(0)
    val density = LocalDensity.current
    val fontSize = 12.sp
    val textStyle = TextStyle(
        color = colors.dialogPrimary,
        fontFamily = font.normalFontFamily,
        fontSize = fontSize,
    )
    val monospaceStyle = textStyle.copy(fontFamily = font.monospaceFontFamily)
    val dropdownTextStyle = textStyle.copy(fontWeight = FontWeight.Bold)

    val (viewportStartBytePosition, viewportEndBytePosition) = currentViewportByteRange(filePager, fileLength) // 0-based
        .let { (it.first + 1).coerceAtMost(fileLength) to (it.second + 1).coerceAtMost(fileLength) } // map to 1-based
    val fullBytePositionText = "${numberFormat.format(viewportStartBytePosition)} ~ ${numberFormat.format(viewportEndBytePosition)} / ${numberFormat.format(fileLength)} B"
    val compactBytePositionText = "${numberFormat.format(viewportStartBytePosition)} / ${numberFormat.format(fileLength)}"
    val selectedEncodingLabel = if (selectedTextEncoding == TextEncoding.Auto) {
        "${selectedTextEncoding.displayName()} (${resolvedTextEncoding.displayName()})"
    } else {
        selectedTextEncoding.displayName()
    }
    val lastModifiedDateTime = formatLastModified(lastModifiedMillis)
    val lastModifiedTime = formatLastModified(lastModifiedMillis, "HH:mm:ss")
    val lastModifiedTimeWithoutSeconds = formatLastModified(lastModifiedMillis, "HH:mm")

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .background(colors.statusBarBackground)
            .padding(horizontal = 8.dp),
    ) {
        val availableWidthPx = with(density) { maxWidth.toPx() }
        val spacingPx = with(density) { 8.dp.toPx() }
        val dropdownWidthPx = statusTextMeasurer.measure(
            text = selectedEncodingLabel,
            style = dropdownTextStyle,
            maxLines = 1,
        ).size.width + with(density) { 38.dp.toPx() }

        val statusVariant = listOf(
            StatusBarTextVariant(
                bytePositionText = fullBytePositionText,
                lastModifiedText = annotatedLastModifiedText(font, "Last modified: ", lastModifiedDateTime),
            ),
            StatusBarTextVariant(
                bytePositionText = compactBytePositionText,
                lastModifiedText = annotatedLastModifiedText(font, "Last modified: ", lastModifiedDateTime),
            ),
            StatusBarTextVariant(
                bytePositionText = compactBytePositionText,
                lastModifiedText = annotatedLastModifiedText(font, "", lastModifiedDateTime),
            ),
            StatusBarTextVariant(
                bytePositionText = compactBytePositionText,
                lastModifiedText = annotatedLastModifiedText(font, "", lastModifiedTime),
            ),
            StatusBarTextVariant(
                bytePositionText = compactBytePositionText,
                lastModifiedText = annotatedLastModifiedText(font, "", lastModifiedTimeWithoutSeconds),
            ),
            StatusBarTextVariant(
                bytePositionText = compactBytePositionText,
                lastModifiedText = null,
            ),
        ).firstOrNull {
            val bytePositionWidthPx = statusTextMeasurer.measure(
                text = it.bytePositionText,
                style = monospaceStyle,
                maxLines = 1,
            ).size.width
            val lastModifiedWidthPx = it.lastModifiedText?.let { text ->
                statusTextMeasurer.measure(
                    text = text,
                    style = textStyle,
                    maxLines = 1,
                ).size.width
            } ?: 0
            bytePositionWidthPx + lastModifiedWidthPx + dropdownWidthPx + spacingPx * 2 <= availableWidthPx
        } ?: StatusBarTextVariant(
            bytePositionText = compactBytePositionText,
            lastModifiedText = null,
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                text = statusVariant.bytePositionText,
                style = monospaceStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier,
            )

            statusVariant.lastModifiedText?.let {
                BasicText(
                    text = it,
                    style = textStyle.copy(textAlign = TextAlign.Center),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            } ?: Box(Modifier.weight(1f))

            DropDownView(
                selected = selectedEncodingLabel,
                entries = selectableTextEncodings.map {
                    ContextMenuItemEntry(
                        type = ContextMenuItemEntry.Type.Button,
                        displayText = it.displayName(),
                        isEnabled = true,
                        testTag = it.name,
                        action = { onSelectTextEncoding(it) },
                    )
                },
                textStyleModifier = { it.copy(fontSize = fontSize, fontWeight = FontWeight.Bold) },
                modifier = Modifier.wrapContentSize()
            )
        }
    }
}

private data class StatusBarTextVariant(
    val bytePositionText: String,
    val lastModifiedText: AnnotatedString?,
)

private fun annotatedLastModifiedText(
    font: AppFont,
    label: String,
    dateTimeText: String,
): AnnotatedString = buildAnnotatedString {
    append(label)
    withStyle(SpanStyle(fontFamily = font.monospaceFontFamily)) {
        append(dateTimeText)
    }
}
private fun currentViewportByteRange(filePager: GiantFileTextPager, fileLength: Long): Pair<Long, Long> {
    val viewportStart = filePager.viewportStartBytePosition.coerceIn(0L, fileLength)
    val rowHeight = filePager.rowHeight()
    val visibleRowCount = if (filePager.viewport.height <= 0 || !java.lang.Float.isFinite(rowHeight) || rowHeight <= 0f) {
        0
    } else {
        floor(filePager.viewport.height.toDouble() / rowHeight.toDouble())
            .toInt()
            .coerceAtLeast(0)
    }
    val visibleRows = filePager.textInViewport.take(visibleRowCount)
    val rowStartBytePositions = filePager.startBytePositionsInViewport

    val viewportEnd = when {
        visibleRows.isEmpty() -> viewportStart
        rowStartBytePositions.size > visibleRows.size -> rowStartBytePositions[visibleRows.size]
        rowStartBytePositions.isNotEmpty() -> rowStartBytePositions.last() + filePager.encodedLengthOfText(visibleRows.last().string())
        else -> viewportStart
    }.coerceIn(viewportStart, fileLength)

    return viewportStart to viewportEnd
}

private fun formatLastModified(lastModifiedMillis: Long, pattern: String = "yyyy-MM-dd HH:mm:ss"): String {
    return KInstant(lastModifiedMillis).atLocalZoneOffset()
        .format(pattern)
}

private fun findBytePositionByCoordinatePx(filePager: GiantFileTextPager, point: Offset): Long {
    val startBytePositions = filePager.startBytePositionsInViewport
    val rowTexts = filePager.textInViewport
//    val charMeasurer = filePager.textLayouter.charMeasurer

    if (rowTexts.isEmpty()) {
        return filePager.viewportStartBytePosition
    }

    if (point.y <= 0) {
        return filePager.viewportStartBytePosition
    }

    // y-axis
    val rowFromTopLeft = floor(point.y / filePager.rowHeight()).toInt()
    if (rowFromTopLeft > rowTexts.lastIndex) {
        return startBytePositions.last() + filePager.encodedLengthOfText(rowTexts.last().string())
    }
    val startBytePosition = startBytePositions[rowFromTopLeft]

    // x-axis
    val rowText = rowTexts[rowFromTopLeft]
    var accumulatedPx = 0f
    var accumulatedBytes = 0L
    rowText.forEachIndexed { i, char ->
        val fullChar = if (char.isHighSurrogate()) {
            return@forEachIndexed
        } else if (char.isLowSurrogate()) {
            rowText.subSequence(i - 1, i + 1)
        } else {
            char.toString()
        }
        val charWidth = filePager.textLayouter.measureCharWidth(fullChar)
        if (point.x in accumulatedPx ..< accumulatedPx + charWidth) {
            return startBytePosition + accumulatedBytes
        }

        accumulatedBytes += filePager.encodedLengthOfText(fullChar.string())
        accumulatedPx += charWidth
    }
    // reached end of row
    return startBytePosition + accumulatedBytes
}
