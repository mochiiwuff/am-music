package app.ammusic.youtube.stream

import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException

enum class AudioQuality { Auto, High, Low }

/** Flux audio prêt à être lu. [chunkBytes] > 0 : le serveur exige des requêtes par plages de cette taille. */
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

/** Une méthode pour obtenir le flux audio d'un titre. */
interface StreamSource {
    val name: String

    suspend fun resolve(videoId: String, quality: AudioQuality): StreamInfo

    /** Le serveur a refusé une URL donnée par cette méthode (403 / 410). */
    suspend fun onRejected(videoId: String, stream: StreamInfo) {}
}

class StreamUnavailableException(val videoId: String, val causes: List<Throwable>) :
    Exception("Aucun flux lisible pour $videoId : " + causes.joinToString(" | ") { "${it::class.simpleName}: ${it.message}" })

/**
 * Chaîne de secours : essaie chaque méthode dans l'ordre et garde l'URL obtenue
 * jusqu'à une minute avant son expiration.
 */
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

    /** À appeler quand la lecture échoue sur une erreur HTTP : oublie l'URL et prévient la méthode fautive. */
    suspend fun reject(videoId: String) {
        val stream = cache.remove(videoId) ?: return
        sources.firstOrNull { it.name == stream.source }?.onRejected(videoId, stream)
    }

    fun cached(videoId: String): StreamInfo? = cache[videoId]
}
