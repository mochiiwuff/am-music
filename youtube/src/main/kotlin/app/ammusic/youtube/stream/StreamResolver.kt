package app.ammusic.youtube.stream

import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException

enum class AudioQuality { Auto, High, Low }

class StreamInfo(
    val url: String,
    val headers: Map<String, String>,
    val expiresAtMs: Long,
    val source: String,
    val client: String,
    val chunkBytes: Long = 0L,
    val loudnessDb: Double? = null,
    val contentLength: Long? = null,
)

interface StreamSource {
    val name: String

    suspend fun resolve(videoId: String, quality: AudioQuality): StreamInfo

    suspend fun onRejected(videoId: String, stream: StreamInfo) {}
}

class StreamUnavailableException(val videoId: String, val causes: List<Throwable>) :
    Exception("Aucun flux lisible pour $videoId : " + causes.joinToString(" | ") { "${it::class.simpleName}: ${it.message}" })

class StreamResolver(
    private val sources: List<StreamSource>,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val cache = ConcurrentHashMap<String, StreamInfo>()

    suspend fun resolve(videoId: String, quality: AudioQuality = AudioQuality.Auto): StreamInfo {
        cache[videoId]?.takeIf { it.expiresAtMs - now() > 60_000L }?.let { return it }
        val errors = mutableListOf<Throwable>()
        for (source in sources) {
            try {
                return source.resolve(videoId, quality).also { cache[videoId] = it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errors += e
            }
        }
        throw StreamUnavailableException(videoId, errors)
    }

    suspend fun reject(videoId: String) {
        val stream = cache.remove(videoId) ?: return
        sources.firstOrNull { it.name == stream.source }?.onRejected(videoId, stream)
    }

    fun cached(videoId: String): StreamInfo? = cache[videoId]
}
