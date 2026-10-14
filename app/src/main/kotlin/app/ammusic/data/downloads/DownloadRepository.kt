package app.ammusic.data.downloads

import android.content.Context
import android.util.Log
import androidx.core.content.ContextCompat
import app.ammusic.data.UserSettings
import app.ammusic.data.library.DownloadRow
import app.ammusic.data.library.DownloadState
import app.ammusic.data.library.LibraryRepository
import app.ammusic.data.library.toRow
import app.ammusic.data.library.toTrack
import app.ammusic.youtube.YouTubeMusic
import app.ammusic.youtube.model.YtTrack
import app.ammusic.youtube.model.thumbnailAt
import app.ammusic.youtube.stream.AudioQuality
import app.ammusic.youtube.stream.StreamInfo
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val Tag = "AmDownloads"
private const val ChunkBytes = 8L * 1024 * 1024
private const val Attempts = 3

class DownloadProgress(val title: String, val done: Int, val total: Int, val fraction: Float)

private class Rejected(code: Int) : IOException("réponse $code")

@Singleton
class DownloadRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    library: LibraryRepository,
) {
    private val dao = library.dao
    private val dir = File(context.filesDir, "downloads").apply { mkdirs() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    private val completed = ConcurrentHashMap.newKeySet<String>()

    val states: StateFlow<Map<String, Int>> = dao.downloads()
        .map { rows -> rows.associate { it.trackId to it.state } }
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    val tracks: Flow<List<YtTrack>> = dao.downloadedTracks().map { rows -> rows.map { it.toTrack() } }

    private val _running = MutableStateFlow<Map<String, Float>>(emptyMap())
    val running: StateFlow<Map<String, Float>> = _running.asStateFlow()

    private val _bytes = MutableStateFlow(0L)
    val bytes: StateFlow<Long> = _bytes.asStateFlow()

    init {
        scope.launch {
            completed.addAll(dao.completedDownloadIds().filter { File(dir, fileName(it)).exists() })
            dao.requeueDownloads()
            measure()
            if (dao.pendingDownloadCount() > 0) start()
        }
    }

    fun fileFor(trackId: String): File? = File(dir, fileName(trackId)).takeIf { trackId in completed && it.exists() }

    fun download(tracks: List<YtTrack>) {
        if (tracks.isEmpty()) return
        scope.launch {
            val now = System.currentTimeMillis()
            dao.insertTracksIfMissing(tracks.map { it.toRow() })
            dao.insertDownloads(tracks.mapIndexed { index, track -> DownloadRow(track.id, DownloadState.Queued, 0L, now + index) })
            tracks.filter { states.value[it.id] == DownloadState.Failed }.forEach { dao.setDownloadState(it.id, DownloadState.Queued, 0L) }
            start()
        }
    }

    fun remove(trackIds: Collection<String>) {
        if (trackIds.isEmpty()) return
        scope.launch {
            completed.removeAll(trackIds.toSet())
            trackIds.forEach { id ->
                File(dir, fileName(id)).delete()
                File(dir, "$id.part").delete()
            }
            dao.deleteDownloads(trackIds.toList())
            measure()
        }
    }

    fun removeAll() {
        scope.launch {
            completed.clear()
            dir.listFiles()?.forEach { it.delete() }
            dao.deleteDownloads(states.value.keys.toList())
            measure()
        }
    }

    private fun start() {
        runCatching { ContextCompat.startForegroundService(context, android.content.Intent(context, DownloadService::class.java)) }
            .onFailure { Log.w(Tag, "service : ${it.javaClass.simpleName}") }
    }

    suspend fun runQueue(onProgress: (DownloadProgress) -> Unit) = withContext(Dispatchers.IO) {
        var done = 0
        while (true) {
            if (UserSettings.current.downloadOnWifi && UserSettings.onMeteredNetwork(context)) {
                Log.i(Tag, "en attente du Wi-Fi")
                break
            }
            val next = dao.nextQueuedDownload() ?: break
            val track = dao.track(next.trackId)?.toTrack()
            if (track == null) {
                dao.deleteDownloads(listOf(next.trackId))
                continue
            }
            val total = done + dao.pendingDownloadCount()
            dao.setDownloadState(track.id, DownloadState.Running, 0L)
            onProgress(DownloadProgress(track.title, done, total, 0f))
            val size = fetch(track) { fraction ->
                _running.value = mapOf(track.id to fraction)
                onProgress(DownloadProgress(track.title, done, total, fraction))
            }
            _running.value = emptyMap()
            if (size != null) {
                completed += track.id
                dao.setDownloadState(track.id, DownloadState.Done, size)
                keepCover(track)
                done++
            } else {
                dao.setDownloadState(track.id, DownloadState.Failed, 0L)
            }
            measure()
        }
    }

    private suspend fun fetch(track: YtTrack, onFraction: (Float) -> Unit): Long? {
        val part = File(dir, "${track.id}.part")
        repeat(Attempts) { attempt ->
            try {
                val stream = YouTubeMusic.streams.resolve(track.id, AudioQuality.High)
                save(stream, part, onFraction)
                val target = File(dir, fileName(track.id))
                if (part.renameTo(target)) {
                    Log.i(Tag, "${track.id} téléchargé (${target.length() / 1024} Ko)")
                    return target.length()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Rejected) {
                part.delete()
                runCatching { YouTubeMusic.streams.reject(track.id) }
                Log.w(Tag, "${track.id} refusé, essai ${attempt + 1}")
            } catch (e: Exception) {
                Log.w(Tag, "${track.id} : ${e.javaClass.simpleName} ${e.message.orEmpty().take(120)}, essai ${attempt + 1}")
                delay(1_500L * (attempt + 1))
            }
        }
        part.delete()
        return null
    }

    private fun save(stream: StreamInfo, part: File, onFraction: (Float) -> Unit) {
        val chunk = if (stream.chunkBytes > 0) stream.chunkBytes else ChunkBytes
        var total = stream.contentLength ?: -1L
        var start = part.length()
        if (total in 1..start) start = 0L.also { part.delete() }
        FileOutputStream(part, start > 0).use { out ->
            while (total < 0 || start < total) {
                val end = if (total > 0) minOf(start + chunk, total) - 1 else start + chunk - 1
                val request = Request.Builder().url(stream.url).apply {
                    stream.headers.forEach { (name, value) -> header(name, value) }
                    header("Range", "bytes=$start-$end")
                }.build()
                client.newCall(request).execute().use { response ->
                    if (response.code == 403 || response.code == 410) throw Rejected(response.code)
                    if (!response.isSuccessful) throw IOException("réponse ${response.code}")
                    val body = response.body ?: throw IOException("réponse vide")
                    if (response.code == 200) {
                        if (start > 0) throw IOException("plages refusées")
                        total = body.contentLength()
                    } else if (total < 0) {
                        total = response.header("Content-Range")?.substringAfter('/')?.toLongOrNull() ?: -1L
                    }
                    var read = 0L
                    body.byteStream().use { input ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            out.write(buffer, 0, count)
                            read += count
                            if (total > 0) onFraction(((start + read).toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                    if (read == 0L) throw IOException("aucune donnée")
                    val requested = end - start + 1
                    start += read
                    if (total < 0 && read < requested) total = start
                    if (response.code == 200) total = start
                }
            }
        }
    }

    private fun keepCover(track: YtTrack) {
        val url = track.thumbnail ?: return
        val loader = SingletonImageLoader.get(context)
        listOf(url.thumbnailAt(544), url.thumbnailAt(160)).forEach { loader.enqueue(ImageRequest.Builder(context).data(it).build()) }
    }

    private fun measure() {
        _bytes.value = dir.listFiles()?.filter { it.name.endsWith(".audio") }?.sumOf { it.length() } ?: 0L
    }

    private fun fileName(trackId: String) = "$trackId.audio"
}
