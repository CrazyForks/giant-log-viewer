package com.sunnychung.application.multiplatform.giantlogviewer

import com.sunnychung.application.multiplatform.giantlogviewer.io.copyFileByteRange
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContentEquals

class FileCopyTest {

    @Test
    fun copyFileByteRangeStopsWhenCancelled() {
        val directory = createTempDirectory("giant-log-viewer-file-copy-test").toFile()
        try {
            val source = File(directory, "source.log")
            val destination = File(directory, "destination.log")
            val firstChunk = ByteArray(1024 * 1024) { 1 }
            val secondChunk = ByteArray(1024 * 1024) { 2 }
            source.outputStream().use { output ->
                output.write(firstChunk)
                output.write(secondChunk)
            }
            var continuationChecks = 0

            copyFileByteRange(source, destination, 0L..<source.length()) {
                continuationChecks++ == 0
            }

            assertContentEquals(firstChunk, destination.readBytes())
        } finally {
            directory.deleteRecursively()
        }
    }
}
