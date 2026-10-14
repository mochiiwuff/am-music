package app.ammusic.playback

import android.content.Context
import androidx.media3.common.Player
import app.ammusic.youtube.model.YtTrack
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
private data class StoredTrack(
    val id: String,
    val title: String,
    val artists: String,
    val album: String? = null,
    val thumbnail: String? = null,
    val durationSeconds: Int? = null,
    val artistId: String? = null,
    val albumId: String? = null,
)

@Serializable
private data class StoredQueue(
    val tracks: List<StoredTrack>,
    val index: Int,
    val positionMs: Long,
    val shuffle: Boolean = false,
    val repeat: Int = Player.REPEAT_MODE_OFF,
)

class SavedQueue(val tracks: List<YtTrack>, val index: Int, val positionMs: Long, val shuffle: Boolean, val repeat: Int) {
    val current: YtTrack? get() = tracks.getOrNull(index)
}

object QueueStore {
    private val json = Json { ignoreUnknownKeys = true }

    private fun file(context: Context) = File(context.filesDir, "queue.json")

    fun save(context: Context, player: Player) {
        val count = player.mediaItemCount
        if (count == 0) {
            file(context).delete()
            return
        }
        val tracks = List(count) { player.getMediaItemAt(it).toTrack() }.map {
            StoredTrack(it.id, it.title, it.artists, it.album, it.thumbnail, it.durationSeconds, it.artistId, it.albumId)
        }
        val stored = StoredQueue(tracks, player.currentMediaItemIndex, player.currentPosition.coerceAtLeast(0L), player.shuffleModeEnabled, player.repeatMode)
        runCatching {
            val temp = File(context.filesDir, "queue.json.tmp")
            temp.writeText(json.encodeToString(StoredQueue.serializer(), stored))
            temp.renameTo(file(context))
        }
    }

    fun load(context: Context): SavedQueue? = runCatching {
        val stored = json.decodeFromString(StoredQueue.serializer(), file(context).readText())
        val tracks = stored.tracks.map { YtTrack(it.id, it.title, it.artists, it.album, it.thumbnail, it.durationSeconds, it.artistId, it.albumId) }
        if (tracks.isEmpty()) null else SavedQueue(tracks, stored.index.coerceIn(0, tracks.lastIndex), stored.positionMs, stored.shuffle, stored.repeat)
    }.getOrNull()
}
