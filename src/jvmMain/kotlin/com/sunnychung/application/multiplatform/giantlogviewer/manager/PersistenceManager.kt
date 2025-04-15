package com.sunnychung.application.multiplatform.giantlogviewer.manager

import com.sunnychung.application.multiplatform.giantlogviewer.document.DocumentIdentifier
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

class PersistenceManager {

    private val fileManager by lazy { AppContext.instance.FileManager }

    internal val documentCaches = ConcurrentHashMap<DocumentIdentifier, Any>()
    internal val documentLocks = ConcurrentHashMap<DocumentIdentifier, Mutex>()

    @OptIn(ExperimentalSerializationApi::class)
    private val codec = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        prettyPrint = true
        allowTrailingComma = true
    }
    private val codecCustomizedWriter = codec

    private fun dataDir() = AppContext.instance.dataDir

    private fun dataFile(relativePath: String): File {
        val path = dataDir().absolutePath + File.separator + relativePath.replace("/", File.separator)
        return File(path)
    }

    internal suspend inline fun <T> writeToFile(relativePath: String, serializer: KSerializer<T>, document: T) {
        val file = dataFile(relativePath)
        println("writeToFile -- ${file.absolutePath}")
        file.parentFile.mkdirs()
//        val bytes = codec.encodeToByteArray(serializer, document)
//        fileManager.writeToFile(
//            file = file,
//            content = bytes
//        )
        val tmpFile = File(file.parentFile, "${file.name}.tmp")
        if (tmpFile.exists() && !tmpFile.canWrite()) {
            throw IOException("File ${tmpFile.absolutePath} is not writeable")
        }
        fileManager.writeToFile(
            file = tmpFile,
        ) { outStream ->
            codecCustomizedWriter.encodeToStream(serializer, document, outStream)
        }
        if (file.exists() && !file.delete()) {
            throw IOException("File ${file.absolutePath} cannot be deleted for new content")
        }
        if (!tmpFile.renameTo(file)) {
            throw IOException("File ${tmpFile.absolutePath} cannot be renamed to ${file.name}")
        }
    }

    internal suspend inline fun <T> readFile(relativePath: String, serializer: KSerializer<T>): T? {
        val file = dataFile(relativePath)
        if (!file.isFile) return null
//        log.d { "read file: $relativePath" }
        val text = fileManager.readStringFromFile(file)
        return codec.decodeFromString(serializer, text)
    }

    internal fun deleteFile(relativePath: String) {
        val file = dataFile(relativePath)
        if (file.isFile) {
            file.delete()
        }
    }

    suspend fun initialize() {
        // clear cache
        documentCaches.clear()
        documentLocks.clear()

        // initialize cache
//        AppContext.ProjectCollectionRepository.readOrCreate(ProjectAndEnvironmentsDI()) { id ->
//            ProjectCollection(id = id, projects = mutableListOf())
//        }
//        AppContext.UserPreferenceRepository.readOrCreate(UserPreferenceDI()) { id ->
//            UserPreferenceDocument(id = id, preference = UserPreference(
//                colourTheme = ColourTheme.Dark
//            ))
//        }
//        AppContext.OperationalRepository.readOrCreate(OperationalDI()) { id ->
//            OperationalDocument(
//                id = id,
//                data = OperationalInfo(
//                    appVersion = AppContext.MetadataManager.version,
//                    installationId = uuidString(),
//                )
//            )
//        }
    }


}
