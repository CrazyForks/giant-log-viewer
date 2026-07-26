package com.sunnychung.application.multiplatform.giantlogviewer.util

class SimpleLogger(var logLevel: LogLevel) {

    fun v(message: String) {
        if (logLevel > LogLevel.VERBOSE) return
        println(message)
    }

    fun d(message: String) {
        if (logLevel > LogLevel.DEBUG) return
        println(message)
    }

    fun i(message: String) {
        if (logLevel > LogLevel.INFO) return
        println(message)
    }

    fun w(message: String) {
        if (logLevel > LogLevel.WARN) return
        println(message)
    }

    fun e(message: String) {
        if (logLevel > LogLevel.ERROR) return
        println(message)
    }
}

enum class LogLevel {
    VERBOSE, DEBUG, INFO, WARN, ERROR
}

val log = SimpleLogger(LogLevel.DEBUG)
