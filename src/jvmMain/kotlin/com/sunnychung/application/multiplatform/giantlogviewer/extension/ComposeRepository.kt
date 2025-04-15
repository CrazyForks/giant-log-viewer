package com.sunnychung.application.multiplatform.giantlogviewer.extension

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import com.sunnychung.application.multiplatform.giantlogviewer.document.Document
import com.sunnychung.application.multiplatform.giantlogviewer.document.DocumentIdentifier
import com.sunnychung.application.multiplatform.giantlogviewer.repository.BaseRepository
import kotlinx.coroutines.runBlocking

@Composable
fun <T : Document<ID>, ID : DocumentIdentifier> BaseRepository<T, ID>.subscribeStateToEntity(identifier: ID): T {
    return subscribeToEntity(identifier)
        .collectAsState(null)
        .value
        .also {
            println("collectAsState $it")
        }
        ?.first
        ?: runBlocking {
            readOrDefault(identifier)
        }
}
