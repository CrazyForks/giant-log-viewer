package com.sunnychung.application.multiplatform.giantlogviewer.io

import com.sun.jna.Native
import com.sun.jna.Platform
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.BaseTSD.SIZE_T
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val CF_UNICODETEXT = 13
private const val GMEM_MOVEABLE = 0x0002
private const val CLIPBOARD_OPEN_ATTEMPTS = 10
private const val CLIPBOARD_OPEN_RETRY_MILLIS = 10L
private const val TEXT_WRITE_BUFFER_CHARS = 8192

internal suspend fun setClipboardText(
    text: String,
    fallback: (String) -> Unit,
) {
    if (Platform.isWindows()) {
        withContext(Dispatchers.IO) {
            WindowsClipboard.setText(text)
        }
    } else {
        fallback(text)
    }
}

internal fun windowsClipboardCharacterCount(text: CharSequence): Long {
    var characterCount = text.length.toLong() + 1L
    text.forEachIndexed { index, character ->
        if (character == '\n' && (index == 0 || text[index - 1] != '\r')) {
            ++characterCount
        }
    }
    return characterCount
}

internal fun writeWindowsClipboardText(
    text: CharSequence,
    bufferSize: Int = TEXT_WRITE_BUFFER_CHARS,
    write: (buffer: CharArray, length: Int) -> Unit,
) {
    require(bufferSize >= 2)

    val buffer = CharArray(bufferSize)
    var bufferLength = 0

    fun flush() {
        if (bufferLength > 0) {
            write(buffer, bufferLength)
            bufferLength = 0
        }
    }

    fun append(character: Char) {
        if (bufferLength == buffer.size) {
            flush()
        }
        buffer[bufferLength++] = character
    }

    text.forEachIndexed { index, character ->
        if (character == '\n' && (index == 0 || text[index - 1] != '\r')) {
            append('\r')
        }
        append(character)
    }
    append('\u0000')
    flush()
}

private object WindowsClipboard {
    private val user32: WindowsUser32 by lazy {
        Native.load("user32", WindowsUser32::class.java, W32APIOptions.DEFAULT_OPTIONS)
    }
    private val kernel32: WindowsKernel32 by lazy {
        Native.load("kernel32", WindowsKernel32::class.java, W32APIOptions.DEFAULT_OPTIONS)
    }

    fun setText(text: String) {
        val byteLength = Math.multiplyExact(
            windowsClipboardCharacterCount(text),
            Native.WCHAR_SIZE.toLong(),
        )
        val memoryHandle = kernel32.GlobalAlloc(GMEM_MOVEABLE, SIZE_T(byteLength))
            ?: throw windowsClipboardFailure("GlobalAlloc")
        var ownershipTransferred = false

        try {
            val memory = kernel32.GlobalLock(memoryHandle)
                ?: throw windowsClipboardFailure("GlobalLock")
            try {
                var byteOffset = 0L
                writeWindowsClipboardText(text) { buffer, length ->
                    memory.write(byteOffset, buffer, 0, length)
                    byteOffset += length.toLong() * Native.WCHAR_SIZE
                }
                check(byteOffset == byteLength)
            } finally {
                kernel32.GlobalUnlock(memoryHandle)
            }

            openClipboard()
            try {
                if (!user32.EmptyClipboard()) {
                    throw windowsClipboardFailure("EmptyClipboard")
                }
                if (user32.SetClipboardData(CF_UNICODETEXT, memoryHandle) == null) {
                    throw windowsClipboardFailure("SetClipboardData")
                }
                ownershipTransferred = true
            } finally {
                user32.CloseClipboard()
            }
        } finally {
            if (!ownershipTransferred) {
                kernel32.GlobalFree(memoryHandle)
            }
        }
    }

    private fun openClipboard() {
        repeat(CLIPBOARD_OPEN_ATTEMPTS) { attempt ->
            if (user32.OpenClipboard(null)) {
                return
            }
            if (attempt < CLIPBOARD_OPEN_ATTEMPTS - 1) {
                Thread.sleep(CLIPBOARD_OPEN_RETRY_MILLIS)
            }
        }
        throw windowsClipboardFailure("OpenClipboard")
    }

    private fun windowsClipboardFailure(operation: String): IllegalStateException {
        return IllegalStateException("$operation failed with Windows error ${Native.getLastError()}")
    }
}

private interface WindowsUser32 : StdCallLibrary {
    fun OpenClipboard(window: Pointer?): Boolean
    fun CloseClipboard(): Boolean
    fun EmptyClipboard(): Boolean
    fun SetClipboardData(format: Int, memoryHandle: Pointer): Pointer?
}

private interface WindowsKernel32 : StdCallLibrary {
    fun GlobalAlloc(flags: Int, byteLength: SIZE_T): Pointer?
    fun GlobalLock(memoryHandle: Pointer): Pointer?
    fun GlobalUnlock(memoryHandle: Pointer): Boolean
    fun GlobalFree(memoryHandle: Pointer): Pointer?
}
