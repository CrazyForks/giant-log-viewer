package com.sunnychung.application.multiplatform.giantlogviewer.document

import com.sunnychung.application.multiplatform.giantlogviewer.annotation.Persisted
import kotlinx.serialization.Serializable

interface Document<ID : DocumentIdentifier> {
    val id: ID
}

interface Identifiable {
    val id: String
}

@Serializable
@Persisted
sealed class DocumentIdentifier//(val type: PersistenceDocumentType)

enum class PersistenceDocumentType {
    Theme
}
