package com.sunnychung.application.multiplatform.giantlogviewer.ux

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sunnychung.application.multiplatform.giantlogviewer.io.ComposeGiantFileTextPager
import com.sunnychung.application.multiplatform.giantlogviewer.io.GiantFileReader
import com.sunnychung.application.multiplatform.giantlogviewer.io.GiantFileTextPager
import com.sunnychung.application.multiplatform.giantlogviewer.io.Viewport
import com.sunnychung.application.multiplatform.giantlogviewer.layout.BidirectionalTextLayouter
import com.sunnychung.application.multiplatform.giantlogviewer.layout.MonospaceBidirectionalTextLayouter
import com.sunnychung.lib.multiplatform.bigtext.compose.ComposeUnicodeCharMeasurer
import com.sunnychung.lib.multiplatform.bigtext.util.AnnotatedStringBuilder
import com.sunnychung.lib.multiplatform.bigtext.util.annotatedString
import com.sunnychung.lib.multiplatform.bigtext.util.buildAnnotatedStringPatched
import com.sunnychung.lib.multiplatform.kdatetime.KInstant
import kotlinx.coroutines.Dispatchers
import java.io.File

@Composable
fun GiantTextViewer(modifier: Modifier, filePath: String, refreshKey: Int = 0) {
    val file = File(filePath)
    if (!file.isFile) {
        println("File is not a file")
        return
    }

    println("recompose $filePath $refreshKey")

    var componentWidth by remember { mutableIntStateOf(0) }
    var componentHeight by remember { mutableIntStateOf(0) }

    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val textStyle = TextStyle(fontFamily = FontFamily.Monospace)
    val charMeasurer = remember(density) { ComposeUnicodeCharMeasurer(textMeasurer, textStyle) }
    val textLayouter = remember(charMeasurer) { MonospaceBidirectionalTextLayouter(charMeasurer) }

    val fileReader = remember(filePath, refreshKey) {
        GiantFileReader(filePath)
    }
    val filePager: GiantFileTextPager = remember(fileReader) { ComposeGiantFileTextPager(fileReader, textLayouter) }

    val focusRequester = remember { FocusRequester() }

    remember(componentWidth, componentHeight, density) {
        filePager.viewport = Viewport(componentWidth, componentHeight, density.density)
    }

    Box(modifier
        .onGloballyPositioned {
            componentWidth = it.size.width
            componentHeight = it.size.height
        }
//        .onKeyEvent { e ->
        .onPreviewKeyEvent { e ->
            println("onKeyEvent ${e.key}")
            val startTime = KInstant.now()
            if (e.type == KeyEventType.KeyDown) {
                when (e.key to e.isAltPressed) {
                    Key.DirectionUp to false -> filePager.moveToPrevRow()
                    Key.DirectionDown to false -> filePager.moveToNextRow()
                    Key.DirectionUp to true -> filePager.moveToPrevPage()
                    Key.DirectionDown to true -> filePager.moveToNextPage()
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
        val startTime = KInstant.now()
        val textToDisplay: List<CharSequence> = filePager.textInViewport
//        println("textToDisplay:\n$textToDisplay")
        val lineHeight = (textLayouter.charMeasurer as ComposeUnicodeCharMeasurer).getRowHeight()
        Canvas(modifier = Modifier.matchParentSize()) {
            with(density) {
                textToDisplay.forEachIndexed { rowRelativeIndex, row ->
                    var unicodeSequence: CharSequence? = null
                    val rowYOffset = rowRelativeIndex * lineHeight
                    val globalXOffset = 0f
                    var accumulateXOffset = 0f
                    row.indices.forEach { i ->
                        var charAnnotated = row.subSequence(i, i + 1)
                        val char = charAnnotated.first()
                        if (char.isHighSurrogate()) {
                            unicodeSequence = charAnnotated
                            return@forEach
                        } else if (char.isLowSurrogate() && unicodeSequence != null) {
                            charAnnotated = buildAnnotatedStringPatched {
                                append(unicodeSequence)
                                append(charAnnotated)
                            }
                        }
                        val charWidth = textLayouter.measureCharWidth(charAnnotated)
                        val charYOffset = textLayouter.measureCharYOffset(charAnnotated)
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
                            text = charAnnotated.annotatedString(),
                            topLeft = Offset(globalXOffset + accumulateXOffset, rowYOffset + charYOffset),
                            size = Size(charWidth, lineHeight),
                            style = textStyle,
                            overflow = TextOverflow.Visible,
                            softWrap = false,
                            maxLines = 1,
                        )

                        accumulateXOffset += charWidth
                    }
                }
            }
        }
        println("prepare rendering in ${KInstant.now() - startTime}")
    }

    LaunchedEffect(filePath, refreshKey) {
        focusRequester.requestFocus()
    }
}
