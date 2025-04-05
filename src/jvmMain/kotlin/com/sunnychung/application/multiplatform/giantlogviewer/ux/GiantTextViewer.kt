package com.sunnychung.application.multiplatform.giantlogviewer.ux

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.platform.LocalDensity
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
import com.sunnychung.application.multiplatform.giantlogviewer.ux.local.LocalFont
import com.sunnychung.lib.multiplatform.bigtext.compose.ComposeUnicodeCharMeasurer
import com.sunnychung.lib.multiplatform.bigtext.extension.isCtrlOrCmdPressed
import com.sunnychung.lib.multiplatform.bigtext.util.annotatedString
import com.sunnychung.lib.multiplatform.bigtext.util.buildAnnotatedStringPatched
import com.sunnychung.lib.multiplatform.bigtext.util.debouncedStateOf
import com.sunnychung.lib.multiplatform.bigtext.util.string
import com.sunnychung.lib.multiplatform.kdatetime.KInstant
import com.sunnychung.lib.multiplatform.kdatetime.extension.milliseconds
import java.io.File

// TODO onPagerReady is an anti-pattern -- reverse of data flow. refactor it.
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun GiantTextViewer(
    modifier: Modifier,
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
    val textMeasurer = rememberTextMeasurer()
    val textStyle = TextStyle(fontFamily = LocalFont.current.monospaceFontFamily)
    val charMeasurer = remember(density) { ComposeUnicodeCharMeasurer(textMeasurer, textStyle) }
    val textLayouter = remember(charMeasurer) { MonospaceBidirectionalTextLayouter(charMeasurer) }

    val fileReader = remember(filePath, refreshKey) {
        GiantFileReader(filePath)
    }
    val filePager: GiantFileTextPager = remember(fileReader) { ComposeGiantFileTextPager(fileReader, textLayouter) }

    val focusRequester = remember { FocusRequester() }

    val (contentWidth, isContentWidthLatest) = debouncedStateOf(200.milliseconds(), tolerateCount = 1, filePager) {
        contentComponentWidth
    }

    if (isContentWidthLatest) {
        remember(filePager, contentWidth, contentComponentHeight, density) {
            filePager.viewport = Viewport(contentWidth, contentComponentHeight, density.density)
        }
    }

    LaunchedEffect(filePager) {
        onPagerReady(filePager)
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
        // not sure which Compose bug leading to require implementing "click to focus" manually
        .onPointerEvent(eventType = PointerEventType.Press) {
            focusRequester.requestFocus()
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
        ) {
            val textToDisplay: List<CharSequence> = filePager.textInViewport
            val bytePositionsOfDisplay: List<Long> = filePager.startBytePositionsInViewport
//        println("textToDisplay:\n$textToDisplay")
            val lineHeight = (textLayouter.charMeasurer as ComposeUnicodeCharMeasurer).getRowHeight()
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
                        val charWidth = textLayouter.measureCharWidth(charAnnotated)
                        val charYOffset = textLayouter.measureCharYOffset(charAnnotated)

                        if (bytePosition in highlightByteRange) {
                            drawRect(
                                color = Color(red = 0.85f, green = 0.6f, blue = 0f),
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

                        accumulateXOffset += charWidth
                        bytePosition += charAnnotated.string().toByteArray(Charsets.UTF_8).size
                    }
                }
//                }
            }
        }

        VerticalIndicatorView(
            value = (filePager.viewportStartBytePosition.toDouble() / fileReader.lengthInBytes().toDouble()).toFloat(),
            modifier = Modifier.width(20.dp).fillMaxHeight()
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
