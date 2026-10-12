package app.ammusic.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class SkipSegment(val startMs: Long, val endMs: Long)

@Serializable
private data class SkipEntry(val videoID: String = "", val segments: List<SkipPart> = emptyList())

@Serializable
private data class SkipPart(val segment: List<Double> = emptyList(), val actionType: String = "skip")

private const val Tag = "AmPlayback"
private const val Categories = """["sponsor","selfpromo","interaction","intro","outro","preview","music_offtopic"]"""
private const val ShortestMs = 1_000L

private val SkipJson = Json { ignoreUnknownKeys = true }

internal fun parseSkipSegments(body: String, videoId: String): List<SkipSegment> =
    SkipJson.decodeFromString<List<SkipEntry>>(body)
        .filter { it.videoID == videoId }
        .flatMap { it.segments }
        .filter { it.actionType == "skip" && it.segment.size == 2 }
        .map { SkipSegment((it.segment[0] * 1000).toLong(), (it.segment[1] * 1000).toLong()) }
        .filter { it.endMs - it.startMs >= ShortestMs }
        .sortedBy { it.startMs }

@Singleton
class SkipSegments @Inject constructor() {
    private val client = OkHttpClient.Builder().callTimeout(10, TimeUnit.SECONDS).build()
    private val cache = object : LinkedHashMap<String, List<SkipSegment>>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<SkipSegment>>?) = size > 60
    }

    suspend fun find(videoId: String): List<SkipSegment> = withContext(Dispatchers.IO) {
        synchronized(cache) { cache[videoId] }?.let { return@withContext it }
        val prefix = MessageDigest.getInstance("SHA-256").digest(videoId.toByteArray()).joinToString("") { "%02x".format(it) }.take(4)
        val url = "https://sponsor.ajay.app/api/skipSegments/$prefix".toHttpUrl().newBuilder().addQueryParameter("categories", Categories).build()
        val request = Request.Builder().url(url).header("User-Agent", "AM Music (appli personnelle)").build()
        runCatching {
            client.newCall(request).execute().use { response ->
                when {
                    response.code == 404 -> emptyList()
                    response.isSuccessful -> parseSkipSegments(response.body?.string().orEmpty(), videoId)
                    else -> error("réponse ${response.code}")
                }
            }
        }.onSuccess { found ->
            synchronized(cache) { cache[videoId] = found }
        }.onFailure {
            Log.w(Tag, "passages à sauter : ${it.javaClass.simpleName} ${it.message.orEmpty().take(80)}")
        }.getOrDefault(emptyList())
    }
}
