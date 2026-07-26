package com.sunnychung.application.multiplatform.giantlogviewer

import com.sunnychung.application.multiplatform.giantlogviewer.util.LogLevel
import com.sunnychung.application.multiplatform.giantlogviewer.util.log
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MainTest {

    @Test
    fun parseLogLevelAcceptsEqualsArgument() {
        log.logLevel = LogLevel.VERBOSE

        val result = parseLogLevel(arrayOf("--logLevel=WARN"))

        assertNull(result)
        assertEquals(LogLevel.WARN, log.logLevel)
    }

    @Test
    fun parseLogLevelAcceptsSeparateValueArgument() {
        log.logLevel = LogLevel.VERBOSE

        val result = parseLogLevel(arrayOf("--logLevel", "ERROR"))

        assertNull(result)
        assertEquals(LogLevel.ERROR, log.logLevel)
    }

    @Test
    fun parseLogLevelKeepsCurrentLevelWhenValueIsInvalid() {
        log.logLevel = LogLevel.INFO

        val result = parseLogLevel(arrayOf("--logLevel=TRACE"))

        assertEquals(LogLevel.INFO, log.logLevel)
        assertTrue(result?.contains("Invalid log level: 'TRACE'") == true)
    }

    @Test
    fun parseInitialFilePathSkipsSeparateLogLevelValue() {
        val file = File.createTempFile("giant-log-viewer", ".log")
        try {
            val result = parseInitialFilePath(arrayOf("--logLevel", "WARN", file.absolutePath))

            assertEquals(file.absolutePath, result)
        } finally {
            file.delete()
        }
    }
}
