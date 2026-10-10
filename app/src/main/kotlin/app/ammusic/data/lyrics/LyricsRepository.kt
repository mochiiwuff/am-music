package app.ammusic.data.lyrics

import android.util.Log
import app.ammusic.youtube.model.YtTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Serializable
private data class TtmlResponse(val ttml: String? = null)

@Serializable
private data class LrcLibEntry(
    val duration: Double? = null,
    val instrumental: Boolean = false,
    val plainLyrics: String? = null,
    val syncedLyrics: String? = null,
)

/** Mentions ajoutées aux titres de clips, qui empêchent de retrouver les paroles (« (Official Video) », « [Clip officiel] »…). */
private val TitleNoise = Regex(
    "\\s*[(\\[][^)\\]]*(official|officiel|video|vidéo|clip|audio|lyric|paroles|visuali[sz]er|remaster)[^)\\]]*[)\\]]",
    RegexOption.IGNORE_CASE,
)

private const val Tag = "AmLyrics"

/**
 * Cherche les paroles d'un titre, de la source la plus précise à la plus simple :
 * BetterLyrics (mot à mot), puis LRCLIB (ligne par ligne, sinon texte seul).
 * Les réponses sont gardées en mémoire le temps de la session.
 */
@Singleton
class LyricsRepository @Inject constructor() {
    private val client = OkHttpClient.Builder().callTimeout(12, TimeUnit.SECONDS).build()
    private val json = Json { ignoreUnknownKeys = true }
    private val cache = object : LinkedHashMap<String, Lyrics>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Lyrics>?) = size > 30
    }

    suspend fun find(track: YtTrack, durationMs: Long): Lyrics = withContext(Dispatchers.IO) {
        synchronized(cache) { cache[track.id] }?.let { return@withContext it }
        val title = track.title.replace(TitleNoise, "").trim().ifEmpty { track.title }
        val artist = track.artists.substringBefore(", ")
        val seconds = (durationMs / 1000).toInt()

        val found = wordByWord(title, artist, track.album, seconds)
            ?: lrcLib(title, artist, track.album, seconds)
            ?: Lyrics.None
        val detail = when (found) {
            is Lyrics.Synced -> if (found.lines.any { it.words.isNotEmpty() }) "mot à mot" else "ligne par ligne"
            is Lyrics.Plain -> "texte seul"
            Lyrics.None -> "rien trouvé"
        }
        Log.d(Tag, "« $title » — $artist : $detail")
        // Un échec n'est pas gardé : la prochaine ouverture retentera (réseau revenu, paroles ajoutées…).
        if (found != Lyrics.None) synchronized(cache) { cache[track.id] = found }
        found
    }

    private fun wordByWord(title: String, artist: String, album: String?, seconds: Int): Lyrics? {
        val url = "https://lyrics-api.boidu.dev/getLyrics".toHttpUrl().newBuilder()
            .addQueryParameter("s", title)
            .addQueryParameter("a", artist)
            .apply {
                if (seconds > 0) addQueryParameter("d", seconds.toString())
                if (!album.isNullOrBlank()) addQueryParameter("al", album)
            }
            .build()
        val ttml = get(url)?.let { runCatching { json.decodeFromString<TtmlResponse>(it).ttml }.getOrNull() } ?: return null
        val lines = parseTtml(ttml)
        Log.d(Tag, "BetterLyrics : ${ttml.length} caractères, ${lines.size} lignes")
        return lines.takeIf { it.isNotEmpty() }?.let(Lyrics::Synced)
    }

    private fun lrcLib(title: String, artist: String, album: String?, seconds: Int): Lyrics? {
        val exact = if (seconds > 0) {
            val url = "https://lrclib.net/api/get".toHttpUrl().newBuilder()
                .addQueryParameter("track_name", title)
                .addQueryParameter("artist_name", artist)
                .addQueryParameter("album_name", album.orEmpty())
                .addQueryParameter("duration", seconds.toString())
                .build()
            get(url)?.let { runCatching { json.decodeFromString<LrcLibEntry>(it) }.getOrNull() }
        } else {
            null
        }
        exact?.toLyrics()?.let { return it }

        // Pas de fiche exacte : on cherche, et on garde la durée la plus proche (5 s d'écart au plus si on la connaît).
        val url = "https://lrclib.net/api/search".toHttpUrl().newBuilder()
            .addQueryParameter("track_name", title)
            .addQueryParameter("artist_name", artist)
            .build()
        val candidates = get(url)?.let { runCatching { json.decodeFromString<List<LrcLibEntry>>(it) }.getOrNull() }.orEmpty()
            .filter { seconds <= 0 || it.duration == null || abs(it.duration - seconds) <= 5 }
            .sortedBy { if (seconds > 0 && it.duration != null) abs(it.duration - seconds) else 0.0 }
        return candidates.firstOrNull { !it.syncedLyrics.isNullOrBlank() }?.toLyrics() ?: candidates.firstNotNullOfOrNull { it.toLyrics() }
    }

    private fun LrcLibEntry.toLyrics(): Lyrics? {
        syncedLyrics?.takeIf { it.isNotBlank() }?.let(::parseLrc)?.takeIf { it.isNotEmpty() }?.let { return Lyrics.Synced(it) }
        return plainLyrics?.takeIf { it.isNotBlank() }?.let(Lyrics::Plain)
    }

    /** Corps de la réponse, ou `null` si la requête échoue ou ne trouve rien. */
    private fun get(url: HttpUrl): String? = runCatching {
        val request = Request.Builder().url(url).header("User-Agent", "AM Music (appli personnelle)").build()
        client.newCall(request).execute().use { response -> if (response.isSuccessful) response.body?.string() else null }
    }.onFailure { Log.w(Tag, "${url.host} : ${it.javaClass.simpleName}") }.getOrNull()
}
