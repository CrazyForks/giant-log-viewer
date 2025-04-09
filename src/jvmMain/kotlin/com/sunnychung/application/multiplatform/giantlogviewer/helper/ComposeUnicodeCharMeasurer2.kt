package com.sunnychung.application.multiplatform.giantlogviewer.helper

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import com.sunnychung.lib.multiplatform.bigtext.compose.ComposeUnicodeCharMeasurer
import com.sunnychung.lib.multiplatform.bigtext.compose.ComposeUnicodeCharMeasurer.CacheKey
import com.sunnychung.lib.multiplatform.bigtext.core.layout.CharMeasurer
import com.sunnychung.lib.multiplatform.bigtext.util.isSurrogatePairFirst
import com.sunnychung.lib.multiplatform.bigtext.util.string
import java.util.concurrent.ConcurrentHashMap

// TODO: Remove all the duplicate code and merge into ComposeUnicodeCharMeasurer
class ComposeUnicodeCharMeasurer2(
    private val measurer: TextMeasurer,
    private val style: TextStyle,
    private val composeCharMeasurer: ComposeUnicodeCharMeasurer = ComposeUnicodeCharMeasurer(
        measurer,
        style
    )
) : CharMeasurer<TextStyle> by composeCharMeasurer {

    private val textLayoutResults: MutableMap<CacheKey, TextLayoutResult> = ConcurrentHashMap(127) //LinkedHashMap<String, Float>(256)

    fun getTextLayoutResult(char: CharSequence, style: TextStyle?): TextLayoutResult? {
        if (char.length == 1 && char[0].code < 127) {
            val style = mergeStyles(style, char as? AnnotatedString)
            val spanStyle = style?.toSpanStyle()
            return textLayoutResults.getOrPut(cacheKeyOf(char, spanStyle)) {
                if (char is AnnotatedString) {
                    measurer.measure(
                        text = char,
                        style = style ?: this.style,
                        overflow = TextOverflow.Visible,
                        softWrap = false,
                        maxLines = 1,
                        skipCache = true,
                    )
                } else {
                    measurer.measure(
                        text = char.string(),
                        style = style ?: this.style,
                        overflow = TextOverflow.Visible,
                        softWrap = false,
                        maxLines = 1,
                        skipCache = true,
                    )
                }
            }
        } else {
            return null
        }
    }

    fun getRowHeight() = composeCharMeasurer.getRowHeight()

    private fun mergeStyles(overrideStyle: TextStyle?, annotatedString: AnnotatedString?): TextStyle? {
        if (annotatedString == null || annotatedString.spanStyles.isEmpty()) {
            return overrideStyle
        }
        val baseStyle = overrideStyle ?: style
        var style = baseStyle
        annotatedString.spanStyles.forEach {
            style += it.item // assume all span styles apply to the full string
        }
        return style
    }

    private inline fun cacheKeyOf(char: CharSequence, style: SpanStyle?): CacheKey {
        return CacheKey(char.string(), style)
    }

    private data class CacheKey(val char: String, val style: SpanStyle?)
}
