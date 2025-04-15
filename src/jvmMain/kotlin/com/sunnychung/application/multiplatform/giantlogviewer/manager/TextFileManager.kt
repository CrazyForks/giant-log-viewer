package com.sunnychung.application.multiplatform.giantlogviewer.manager

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap

class TextFileManager {

    private val fileLocks = ConcurrentHashMap<String, Mutex>()
    private val encoding = Charsets.UTF_8


    suspend fun <R> withLock(file: File, operation: File.() -> R): R {
        val lock = fileLocks.getOrPut(file.absolutePath) { Mutex() }
        return lock.withLock { file.operation() }
    }

    suspend fun writeToFile(file: File, content: String) {
        withLock(file) {
            file.writeText(content, encoding)
        }
    }

    suspend fun writeToFile(file: File, writeOperation: (OutputStream) -> Unit) {
        withLock(file) {
            // commented because in Windows it cannot be locked by the same process twice
//            file.outputStream().use { fileOutputStream ->
//                val fileLock = fileOutputStream.channel.tryLock()
//                    ?: throw IOException("Cannot lock file ${file.absolutePath} for writing")
//
//                try {
                    file.outputStream().use { outputStream ->
                        writeOperation(outputStream)
                        outputStream.flush() // must
                    }
//                } finally {
//                    fileLock.release()
//                }
//            }
        }
    }

    suspend fun readFromFile(file: File): ByteArray {
        return withLock(file) {
            file.readBytes()
        }
    }

    suspend fun readStringFromFile(file: File): String {
        return withLock(file) {
            file.readText(encoding)
        }
    }
}
