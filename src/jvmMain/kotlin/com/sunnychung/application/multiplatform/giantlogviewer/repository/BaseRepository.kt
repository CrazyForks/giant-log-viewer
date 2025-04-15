package com.sunnychung.application.multiplatform.giantlogviewer.repository

import com.sunnychung.application.multiplatform.giantlogviewer.document.Document
import com.sunnychung.application.multiplatform.giantlogviewer.document.DocumentIdentifier
import com.sunnychung.application.multiplatform.giantlogviewer.manager.AppContext
import com.sunnychung.application.multiplatform.giantlogviewer.util.chunkedLatest
import com.sunnychung.lib.multiplatform.kdatetime.extension.milliseconds
import com.sunnychung.lib.multiplatform.kdatetime.extension.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer
import kotlin.random.Random

/**
 * This persistence does not embrace immutability.
 *
 * Read and change the entity, then call `update(ID)` to update a record.
 */
abstract class BaseRepository<T : Document<ID>, ID : DocumentIdentifier>(private val serializer: KSerializer<T>) {
    private val persistenceManager by lazy { AppContext.instance.PersistenceManager }
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private val name = javaClass.simpleName

    // the second Long is used to force update composable state where the same entity instance is used
    private val updateFlow = MutableSharedFlow<Pair<ID, Long>>(
        replay = 1,
        extraBufferCapacity = 1, // TODO this small capacity breaks UI updates if there are multiple IDs
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    init {
        updateFlow
            .chunkedLatest(400.milliseconds())
            .onEach {
                try {
                    writeUpdate(it.first)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    println("Encountered exception in $name: ${e.message}\n${e.stackTraceToString()}")
                }
            }
            .launchIn(coroutineScope)
    }

    open suspend fun read(identifier: ID, isKeepInCache: Boolean = true): T? {
        return withLock(identifier) {
//            log.d { "readWithoutLock $identifier" }
            readWithoutLock(identifier, isKeepInCache).also {
//                log.d { "readWithoutLock $identifier done" }
            }
        }
    }

    open fun readCache(identifier: ID): T? {
        return persistenceManager.documentCaches[identifier] as T?
    }

    private suspend fun readWithoutLock(identifier: ID, isKeepInCache: Boolean): T? {
        return with(persistenceManager) {
            val cache = documentCaches[identifier]
            if (cache != null) return cache as T
            val persisted: T = readFile(relativeFilePath(id = identifier), serializer) ?: return null
            if (isKeepInCache) {
                documentCaches[identifier] = persisted
                buildIndex(persisted)
            }
            persisted
        }
    }

    open suspend fun readOrCreate(identifier: ID, documentSupplier: (ID) -> T): T {
        return withLock(identifier) {
            val record = readWithoutLock(identifier, isKeepInCache = true)
            if (record != null) return@withLock record
            with(persistenceManager) {
                val document = documentSupplier(identifier)
                val identifier = document.id
                documentCaches[identifier] = document
                buildIndex(document)
                writeToFile(relativeFilePath(id = identifier), serializer, document)
                document
            }
        }
    }

    open suspend fun readOrReturn(identifier: ID, documentSupplier: (ID) -> T): T {
        return withLock(identifier) {
            val record = readWithoutLock(identifier, isKeepInCache = true)
            if (record != null) return@withLock record
            with(persistenceManager) {
                val document = documentSupplier(identifier)
                val identifier = document.id
                documentCaches[identifier] = document
                buildIndex(document)
                document
            }
        }
    }

    suspend fun readOrDefault(identifier: ID) = readOrReturn(identifier) { default() }

    fun subscribeToEntity(identifier: ID): Flow<Pair<T, Long>> = updateFlow
//        .onSubscription { emit(identifier) }
        .filter { it.first == identifier }
        .map { (readOrDefault(identifier) to it.second).also { println("sub map $it") } }

    open fun update(identifier: ID) {
        updateFlow.tryEmit(identifier to Random.nextLong())
    }

    private suspend fun writeUpdate(identifier: ID) {
        withLock(identifier) {
            with(persistenceManager) {
                val document = documentCaches[identifier]
                    ?: throw IllegalStateException("Cache miss. This should not happen.")
                writeToFile(relativeFilePath(id = identifier), serializer, document as T)
            }
        }
    }

    /**
     * To ensure only one I/O concurrent process per DocumentIdentifier.
     *
     * TODO: propagate exceptions back to UI
     */
    protected suspend fun <R> withLock(identifier: DocumentIdentifier, operation: suspend () -> R): R {
//        if (log.config.minSeverity <= Severity.Verbose && identifier is ResponsesDI) {
//            log.v(Throwable()) { "getLock $identifier" }
//        } else {
//            log.d { "getLock $identifier" }
//        }
        return persistenceManager.documentLocks.getOrPut(identifier) { Mutex() }
//            .also { log.d { "obtained lock $identifier $it" } }
            .withLock {
//                log.d { "accquired lock $identifier" }
                operation()
//                    .also { log.d { "release lock $identifier" } }
            }
    }

    protected fun buildIndex(document: T) {}
    protected fun removeIndex(document: T) {}

    protected abstract fun relativeFilePath(id: ID): String

    protected open fun default(): T = throw NotImplementedError()
}
