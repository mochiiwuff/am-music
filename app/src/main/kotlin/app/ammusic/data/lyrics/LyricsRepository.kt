package app.ammusic.data.lyrics

import android.util.Log
import app.ammusic.youtube.model.YtTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.FormBody
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

private val TitleNoise = Regex(
    "\\s*[(\\[][^)\\]]*(official|officiel|video|vidéo|clip|audio|lyric|paroles|visuali[sz]er|remaster)[^)\\]]*[)\\]]",
    RegexOption.IGNORE_CASE,
)

private const val Tag = "AmLyrics"

private val LyricsPlusServers = listOf("https://lyricsplus.prjktla.my.id", "https://lyricsplus.binimum.org")

@Serializable
private data class PlusWord(val time: Long = 0, val duration: Long = 0, val text: String = "", val isBackground: Boolean = false)

@Serializable
private data class PlusLine(val time: Long = 0, val duration: Long = 0, val text: String = "", val syllabus: List<PlusWord>? = null)

@Serializable
private data class PlusResponse(val lyrics: List<PlusLine> = emptyList())

@Singleton
class LyricsRepository @Inject constructor() {
    private val client = OkHttpClient.Builder().callTimeout(12, TimeUnit.SECONDS).build()
    private val json = Json { ignoreUnknownKeys = true }
    private val cache = object : LinkedHashMap<String, Lyrics>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Lyrics>?) = size > 30
    }

    private val translations = object : LinkedHashMap<String, Translation>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Translation>?) = size > 12
    }

    suspend fun translate(lines: List<String>, target: String): Translation? = withContext(Dispatchers.IO) {
        val text = lines.joinToString("\n")
        if (text.isBlank()) return@withContext null
        val key = target + ":" + text.hashCode()
        synchronized(translations) { translations[key] }?.let { return@withContext it }
        val url = "https://translate.googleapis.com/translate_a/single".toHttpUrl().newBuilder()
            .addQueryParameter("client", "gtx")
            .addQueryParameter("sl", "auto")
            .addQueryParameter("tl", target)
            .addQueryParameter("dt", "t")
            .build()
        val request = Request.Builder().url(url).post(FormBody.Builder().add("q", text).build()).build()
        val body = runCatching { client.newCall(request).execute().use { if (it.isSuccessful) it.body?.string() else null } }
            .onFailure { Log.w(Tag, "traduction : ${it.javaClass.simpleName}") }
            .getOrNull() ?: return@withContext null
        val result = runCatching { parseTranslation(body, lines.size, target) }.onFailure { Log.w(Tag, "traduction illisible") }.getOrNull()
        if (result != null) synchronized(translations) { translations[key] = result }
        result
    }

    suspend fun find(track: YtTrack, durationMs: Long): Lyrics = withContext(Dispatchers.IO) {
        synchronized(cache) { cache[track.id] }?.let { return@withContext it }
        val title = track.title.replace(TitleNoise, "").trim().ifEmpty { track.title }
        val artist = track.artists.substringBefore(", ")
        val seconds = (durationMs / 1000).toInt()

        val (reference, detailed) = coroutineScope {
            val reference = async { lrcLib(title, artist, track.album, seconds) }
            val detailed = async { lyricsPlus(title, artist, track.album, seconds) ?: betterLyrics(title, artist, track.album, seconds) }
            reference.await() to detailed.await()
        }
        val found = when {
            reference is Lyrics.Synced && detailed != null && detailed.lines.any { it.words.isNotEmpty() } ->
                alignTo(reference.lines, detailed.lines)?.let(Lyrics::Synced) ?: reference
            reference is Lyrics.Synced -> reference
            detailed != null -> detailed
            else -> reference ?: Lyrics.None
        }
        val detail = when (found) {
            is Lyrics.Synced -> if (found.lines.any { it.words.isNotEmpty() }) "mot à mot" else "ligne par ligne"
            is Lyrics.Plain -> "texte seul"
            Lyrics.None -> "rien trouvé"
        }
        Log.d(Tag, "« $title » — $artist : $detail")
        if (found != Lyrics.None) synchronized(cache) { cache[track.id] = found }
        found
    }

    private fun lyricsPlus(title: String, artist: String, album: String?, seconds: Int): Lyrics.Synced? {
        for (server in LyricsPlusServers) {
            val url = "$server/v2/lyrics/get".toHttpUrl().newBuilder()
                .addQueryParameter("title", title)
                .addQueryParameter("artist", artist)
                .apply {
                    if (seconds > 0) addQueryParameter("duration", seconds.toString())
                    if (!album.isNullOrBlank()) addQueryParameter("album", album)
                }
                .build()
            val body = get(url) ?: continue
            val lines = runCatching { json.decodeFromString<PlusResponse>(body).lyrics }.getOrNull().orEmpty().map { line ->
                val words = line.syllabus.orEmpty().filterNot { it.isBackground }.map { LyricWord(it.text, it.time, it.time + it.duration) }
                LyricLine(line.time, line.text.trim(), words)
            }
            if (lines.isNotEmpty()) return Lyrics.Synced(lines.sortedBy { it.startMs })
        }
        return null
    }

    private fun betterLyrics(title: String, artist: String, album: String?, seconds: Int): Lyrics.Synced? {
        val url = "https://lyrics-api.boidu.dev/getLyrics".toHttpUrl().newBuilder()
            .addQueryParameter("s", title)
            .addQueryParameter("a", artist)
            .apply {
                if (seconds > 0) addQueryParameter("d", seconds.toString())
                if (!album.isNullOrBlank()) addQueryParameter("al", album)
            }
            .build()
        val body = get(url) ?: return null
        val ttml = runCatching { json.decodeFromString<TtmlResponse>(body).ttml }
            .onFailure { Log.w(Tag, "BetterLyrics : réponse illisible (${it.javaClass.simpleName})") }
            .getOrNull() ?: return null
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

    private fun get(url: HttpUrl): String? = runCatching {
        val request = Request.Builder().url(url).header("User-Agent", "AM Music (appli personnelle)").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) Log.d(Tag, "${url.host} : réponse ${response.code}")
            if (response.isSuccessful) response.body?.string() else null
        }
    }.onFailure { Log.w(Tag, "${url.host} : ${it.javaClass.simpleName}") }.getOrNull()
}

class Translation(val lines: List<String>, val sameLanguage: Boolean)

internal fun parseTranslation(body: String, expected: Int, target: String): Translation? {
    val root = Json.parseToJsonElement(body).jsonArray
    val source = root.getOrNull(2)?.jsonPrimitive?.contentOrNull
    if (source != null && source.substringBefore('-') == target) return Translation(emptyList(), sameLanguage = true)
    val text = root[0].jsonArray.joinToString("") { segment -> segment.jsonArray[0].jsonPrimitive.contentOrNull.orEmpty() }
    val lines = text.split("\n").map { it.trim() }
    return if (lines.size == expected) Translation(lines, sameLanguage = false) else null
}
