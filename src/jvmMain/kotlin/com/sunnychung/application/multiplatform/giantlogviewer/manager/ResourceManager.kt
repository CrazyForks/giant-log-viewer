package com.sunnychung.application.multiplatform.giantlogviewer.manager

import com.sunnychung.application.giantlogviewer.generated.resources.Res
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.ExperimentalResourceApi
import java.util.concurrent.ConcurrentHashMap

class ResourceManager {

    private val cache = ConcurrentHashMap<String, ByteArray>()

    @OptIn(ExperimentalResourceApi::class)
    suspend fun loadAllResources() {
        val resources = listOf(AppRes.Font.values().toList()).flatten()
        withContext(Dispatchers.IO) {
            resources.forEach {
                launch {
                    cache[it.key] = Res.readBytes("files/${it.path}")
//                    cache[it.key] = javaClass.classLoader.getResourceAsStream(it.path).use { it.readBytes() }
                }
            }
        }
    }

    fun getResource(resource: AppRes.Resource): ByteArray = cache[resource.key]!!
}

object AppRes {

    sealed interface Resource {
        val key: String
    }

    enum class Font(override val key: String, val path: String) : Resource {
        RalewayRegular("font:RalewayRegular", "font/Raleway/Raleway-Regular.ttf"),
        RalewayMedium("font:RalewayMedium", "font/Raleway/Raleway-Medium.ttf"),
        PitagonSansMonoRegular("font:PitagonSansMonoRegular", "font/pitagon_sans_mono/PitagonSansMono-Regular.ttf"),
        PitagonSansMonoBold("font:PitagonSansMonoBold", "font/pitagon_sans_mono/PitagonSansMono-Bold.ttf"),
    }

}
