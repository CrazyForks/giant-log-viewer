package com.sunnychung.application.multiplatform.giantlogviewer

import com.sunnychung.application.multiplatform.giantlogviewer.io.windowsClipboardCharacterCount
import com.sunnychung.application.multiplatform.giantlogviewer.io.writeWindowsClipboardText
import kotlin.test.Test
import kotlin.test.assertEquals

class ClipboardTextTest {

    @Test
    fun writesCrLfLineEndingsAndNullTerminatorAcrossChunks() {
        val result = StringBuilder()

        writeWindowsClipboardText("a\nb\r\nc\rd", bufferSize = 3) { buffer, length ->
            result.append(buffer, 0, length)
        }

        assertEquals("a\r\nb\r\nc\rd\u0000", result.toString())
        assertEquals(
            result.length.toLong(),
            windowsClipboardCharacterCount("a\nb\r\nc\rd"),
        )
    }
}
