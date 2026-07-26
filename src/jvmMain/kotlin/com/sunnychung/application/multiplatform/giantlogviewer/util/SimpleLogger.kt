package com.sunnychung.application.multiplatform.giantlogviewer.util

import com.sunnychung.lib.multiplatform.kdatetime.KInstant

class SimpleLogger(var logLevel: LogLevel) {

    fun v(message: String) {
        if (logLevel > LogLevel.VERBOSE) return
        log(message)
    }

    fun d(message: String) {
        if (logLevel > LogLevel.DEBUG) return
        log(message)
    }

    fun i(message: String) {
        if (logLevel > LogLevel.INFO) return
        log(message)
    }

    fun w(message: String) {
        if (logLevel > LogLevel.WARN) return
        log(message)
    }

    fun e(message: String) {
        if (logLevel > LogLevel.ERROR) return
        log(message)
    }

    private fun log(message: String) {
        println("${KInstant.now()} | $message")
    }
}

enum class LogLevel {
    VERBOSE, DEBUG, INFO, WARN, ERROR;

    companion object {
        fun parseFrom(value: String): LogLevel? = when (value.trim().uppercase()) {
            "VERBOSE" -> VERBOSE
            "DEBUG" -> DEBUG
            "INFO" -> INFO
            "WARN" -> WARN
            "ERROR" -> ERROR
            else -> null
        }
    }
}

val log = SimpleLogger(LogLevel.VERBOSE)
