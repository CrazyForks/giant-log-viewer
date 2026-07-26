package com.sunnychung.application.multiplatform.giantlogviewer.ux

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.unit.dp
import com.sunnychung.lib.multiplatform.kdatetime.KInstant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TOAST_DURATION_MILLIS = 3_000L
private const val TOAST_FADE_IN_MILLIS = 200
private const val TOAST_FADE_OUT_MILLIS = 280L
private const val TOAST_RESHOW_FADE_OUT_MILLIS = TOAST_FADE_OUT_MILLIS / 5L

class ToastManager {
    private val _message = mutableStateOf<String?>(null)
    private val _messageLastUpdate = mutableStateOf<KInstant?>(null)
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
    val state = rememberToastOverlayState(toastManager)
    val toastAlpha by animateFloatAsState(
        targetValue = if (state.isToastVisible) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (state.isToastVisible) {
                TOAST_FADE_IN_MILLIS
            } else {
                state.fadeOutDuration.toInt()
            },
        ),
    )

    DisposableEffect(state) {
        onDispose { state.cancel() }
    }

    LaunchedEffect(toastManager.messageLastUpdate.value, toastManager.message.value) {
        toastManager.message.value?.let(state::show)
    }

    Box(modifier) {
        state.displayedMessage?.let { message ->
            ToastMessage(
                message = message,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 48.dp)
                    .graphicsLayer { alpha = toastAlpha }
                    .focusProperties { canFocus = false }
                    .clickable { state.dismiss(message) },
            )
        }
    }
}

@Composable
private fun rememberToastOverlayState(toastManager: ToastManager): ToastOverlayState {
    val coroutineScope = rememberCoroutineScope()
    return remember(toastManager) {
        ToastOverlayState(
            toastManager = toastManager,
            coroutineScope = coroutineScope,
        )
    }
}

private class ToastOverlayState(
    private val toastManager: ToastManager,
    private val coroutineScope: CoroutineScope,
) {
    var displayedMessage by mutableStateOf<String?>(null)
        private set
    var isToastVisible by mutableStateOf(false)
        private set
    var fadeOutDuration by mutableStateOf(TOAST_FADE_OUT_MILLIS)
        private set

    private var toastJob: Job? = null

    fun show(message: String) = replaceToastJob {
        if (isToastVisible || displayedMessage != null) {
            fadeOutDuration = TOAST_RESHOW_FADE_OUT_MILLIS
            isToastVisible = false
            delay(TOAST_RESHOW_FADE_OUT_MILLIS)
        }
        displayedMessage = message
        fadeOutDuration = TOAST_FADE_OUT_MILLIS
        isToastVisible = true
        delay(TOAST_DURATION_MILLIS)
        isToastVisible = false
        delay(TOAST_FADE_OUT_MILLIS)
        consumeDisplayedToast(message)
    }

    fun dismiss(message: String) = replaceToastJob {
        fadeOutDuration = TOAST_FADE_OUT_MILLIS
        isToastVisible = false
        delay(TOAST_FADE_OUT_MILLIS)
        consumeDisplayedToast(message)
    }

    fun cancel() {
        toastJob?.cancel()
        toastJob = null
    }

    private fun replaceToastJob(block: suspend () -> Unit) {
        toastJob?.cancel()
        toastJob = coroutineScope.launch { block() }
    }

    private fun consumeDisplayedToast(message: String) {
        if (displayedMessage == message) {
            displayedMessage = null
            toastManager.consume(message)
        }
    }
}
