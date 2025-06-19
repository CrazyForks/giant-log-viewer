package com.sunnychung.application.multiplatform.giantlogviewer.ux

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.onDrag
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sunnychung.application.multiplatform.giantlogviewer.io.ComposeGiantFileTextPager
import com.sunnychung.application.multiplatform.giantlogviewer.io.GiantFileReader
import com.sunnychung.application.multiplatform.giantlogviewer.io.GiantFileTextPager
import com.sunnychung.application.multiplatform.giantlogviewer.io.Viewport
import com.sunnychung.application.multiplatform.giantlogviewer.layout.MonospaceBidirectionalTextLayouter
import com.sunnychung.application.multiplatform.giantlogviewer.model.SearchMode
import com.sunnychung.application.multiplatform.giantlogviewer.ux.local.LocalColor
import com.sunnychung.application.multiplatform.giantlogviewer.ux.local.LocalFont
import com.sunnychung.application.multiplatform.giantlogviewer.viewstate.FileViewState
import com.sunnychung.lib.multiplatform.bigtext.annotation.TemporaryBigTextApi
import com.sunnychung.lib.multiplatform.bigtext.compose.ComposeUnicodeCharMeasurer
import com.sunnychung.lib.multiplatform.bigtext.extension.isCtrlOrCmdPressed
import com.sunnychung.lib.multiplatform.bigtext.util.debouncedStateOf
import com.sunnychung.lib.multiplatform.bigtext.util.string
import com.sunnychung.lib.multiplatform.kdatetime.KInstant
import com.sunnychung.lib.multiplatform.kdatetime.extension.milliseconds
import java.io.File
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
    onSearchRequest: (SearchMode) -> Unit,
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
    val textMeasurer = rememberTextMeasurer(0)
    val textStyle = remember(font, colors) {
        TextStyle(
            fontFamily = font.monospaceFontFamily,
            color = colors.fileBodyTheme.plainText,
        )
    }
    val charMeasurer = remember(density, textStyle) { ComposeUnicodeCharMeasurer(textMeasurer, textStyle) }
    val textLayouter = remember(charMeasurer) { MonospaceBidirectionalTextLayouter(charMeasurer) }

    val fileReader = remember(filePath, refreshKey) {
        GiantFileReader(filePath, initialFileLength = fileViewState.fileLength)
    }
    val filePager: GiantFileTextPager = remember(fileReader, textLayouter) {
        ComposeGiantFileTextPager(fileReader, textLayouter, fileViewState.fileLength)
    }
    val fileLength = fileViewState.fileLength

    val clipboardManager = LocalClipboardManager.current

    val focusRequester = remember { FocusRequester() }

    var draggedPoint by remember { mutableStateOf<Offset>(Offset.Zero) }
    var dragStartBytePosition by remember(filePath, refreshKey) { mutableLongStateOf(0L) }
    var dragEndBytePosition by remember(filePath, refreshKey) { mutableLongStateOf(0L) }

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
            val numOfRowsToScroll = (scrollY / rowHeight).toInt()
            scrollY -= rowHeight * numOfRowsToScroll
            filePager.moveToNextRow(numOfRowsToScroll)
            onNavigate(filePager.viewportStartBytePosition)
        } else if (scrollY <= -rowHeight) {
            val numOfRowsToScroll = (abs(scrollY) / rowHeight).toInt()
            scrollY += rowHeight * numOfRowsToScroll
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

    Row(modifier
//        .onKeyEvent { e ->
        .onPreviewKeyEvent { e ->
            println("onKeyEvent ${e.key}")
            val startTime = KInstant.now()
            if (e.type == KeyEventType.KeyDown) {
                when {
                    e.key == Key.F && e.isCtrlOrCmdPressed() -> onSearchRequest(SearchMode.Forward)

                    e.key == Key.F -> { filePager.moveToNextPage(); onNavigate(filePager.viewportStartBytePosition) }
                    e.key == Key.B -> { filePager.moveToPrevPage(); onNavigate(filePager.viewportStartBytePosition) }
                    e.key == Key.DirectionUp && e.isAltPressed -> { filePager.moveToPrevPage(); onNavigate(filePager.viewportStartBytePosition) }
                    e.key == Key.DirectionDown && e.isAltPressed -> { filePager.moveToNextPage(); onNavigate(filePager.viewportStartBytePosition) }

                    e.key == Key.G && e.isShiftPressed -> { filePager.moveToTheLastRow(); onNavigate(filePager.viewportStartBytePosition) }
                    e.key == Key.DirectionDown && e.isCtrlOrCmdPressed() -> { filePager.moveToTheLastRow(); onNavigate(filePager.viewportStartBytePosition) }
                    e.key == Key.G -> { filePager.moveToTheFirstRow(); onNavigate(filePager.viewportStartBytePosition) }
                    e.key == Key.DirectionUp && e.isCtrlOrCmdPressed() -> { filePager.moveToTheFirstRow(); onNavigate(filePager.viewportStartBytePosition) }

                    e.key == Key.DirectionUp -> { filePager.moveToPrevRow(); onNavigate(filePager.viewportStartBytePosition) }
                    e.key == Key.DirectionDown -> { filePager.moveToNextRow(); onNavigate(filePager.viewportStartBytePosition) }

                    e.key == Key.Slash && e.isShiftPressed -> onSearchRequest(SearchMode.Backward)
                    e.key == Key.Slash -> onSearchRequest(SearchMode.Forward)
                    e.key == Key.Escape -> onSearchRequest(SearchMode.None)

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
        .onPointerEvent(eventType = PointerEventType.Press) {
            // not sure which Compose bug leading to require implementing "click to focus" manually
            focusRequester.requestFocus()

            // clear selection
            dragStartBytePosition = 0L
            dragEndBytePosition = 0L
        }
        .focusRequester(focusRequester)
        .focusable()
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
                        bytePosition += charAnnotated.string().toByteArray(Charsets.UTF_8).size
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

    LaunchedEffect(filePath, refreshKey) {
        focusRequester.requestFocus()
    }

    DisposableEffect(filePath, refreshKey) {
        onDispose {
            println("Disposing ${fileReader.filePath}")
            fileReader.close()
            println("Disposed ${fileReader.filePath}")
        }
    }
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
        return startBytePositions.last() + rowTexts.last().string().toByteArray(Charsets.UTF_8).size
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

        accumulatedBytes += fullChar.string().toByteArray(Charsets.UTF_8).size
        accumulatedPx += charWidth
    }
    // reached end of row
    return startBytePosition + accumulatedBytes
}
