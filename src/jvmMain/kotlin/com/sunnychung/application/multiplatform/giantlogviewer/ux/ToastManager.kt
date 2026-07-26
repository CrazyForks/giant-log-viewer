package com.sunnychung.application.multiplatform.giantlogviewer.ux

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sunnychung.application.multiplatform.giantlogviewer.util.log
import com.sunnychung.application.multiplatform.giantlogviewer.ux.local.LocalColor
import com.sunnychung.application.multiplatform.giantlogviewer.ux.local.LocalFont
import com.sunnychung.lib.multiplatform.kdatetime.KInstant
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TOAST_DURATION_MILLIS = 3_000L
private const val TOAST_FADE_OUT_MILLIS = 280L
private const val TOAST_RESHOW_FADE_OUT_MILLIS = TOAST_FADE_OUT_MILLIS / 5L

class ToastManager {
    private var _message = mutableStateOf<String?>(null)
    private var _messageLastUpdate = mutableStateOf<KInstant?>(null)
    val message: State<String?> get() = _message
    val messageLastUpdate: State<KInstant?> get() = _messageLastUpdate

    fun showToast(message: String) {
        _message.value = message
        _messageLastUpdate.value = KInstant.now()
    }

    fun consume(message: String) {
        if (_message.value == message) {
            _message.value = null
        }
    }
}

@Composable
fun AppToastOverlay(
    toastManager: ToastManager,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        val colors = LocalColor.current
        val font = LocalFont.current

        var displayedMessage by remember { mutableStateOf<String?>(null) }
        var isToastVisible by remember { mutableStateOf(false) }
        var fadeOutDuration by remember { mutableStateOf(TOAST_FADE_OUT_MILLIS) }
        val toastAlpha by animateFloatAsState(
            targetValue = if (isToastVisible) 1f else 0f,
            animationSpec = tween(durationMillis = if (isToastVisible) 200 else fadeOutDuration.toInt()),
        )
        log.v("isToastVisible = $isToastVisible, toastAlpha = $toastAlpha")
        val coroutine = rememberCoroutineScope()
        var showHideToastJob by remember { mutableStateOf<Job?>(null) }

        LaunchedEffect(toastManager.messageLastUpdate.value, toastManager.message.value) {
            val message = toastManager.message.value ?: return@LaunchedEffect
            log.d("Launch toast: $message")
            showHideToastJob?.cancel()
            showHideToastJob = coroutine.launch {
                if (isToastVisible || displayedMessage != null) {
                    log.d("hide and show toast")
                    fadeOutDuration = TOAST_RESHOW_FADE_OUT_MILLIS
                    isToastVisible = false
                    log.v("isToastVisible = $isToastVisible, fadeOutDuration = $fadeOutDuration, toastAlpha = $toastAlpha")
                    delay(TOAST_RESHOW_FADE_OUT_MILLIS)
                    log.v("after isToastVisible = $isToastVisible, fadeOutDuration = $fadeOutDuration, toastAlpha = $toastAlpha")
                }
                displayedMessage = message
                fadeOutDuration = TOAST_FADE_OUT_MILLIS
                isToastVisible = true
                delay(TOAST_DURATION_MILLIS)
                fadeOutDuration = TOAST_FADE_OUT_MILLIS
                isToastVisible = false
                delay(TOAST_FADE_OUT_MILLIS)
                if (displayedMessage == message) {
                    displayedMessage = null
                    toastManager.consume(message)
                }
            }
        }

        displayedMessage?.let { msg ->
            log.d("Display error prompt: $displayedMessage")
            ToastMessage(
                message = msg,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 48.dp)
                    .graphicsLayer { alpha = toastAlpha }
                    .focusProperties { canFocus = false }
                    .clickable { // click to dismiss
                        fadeOutDuration = TOAST_FADE_OUT_MILLIS
                        isToastVisible = false
                        showHideToastJob?.cancel()
                        showHideToastJob = coroutine.launch {
                            delay(TOAST_FADE_OUT_MILLIS)
                            if (displayedMessage == msg) {
                                displayedMessage = null
                                toastManager.consume(msg)
                            }
                        }
                    }
            )
        }
    }
}
