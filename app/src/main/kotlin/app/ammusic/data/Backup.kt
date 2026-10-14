package app.ammusic.data

import android.content.Context
import android.net.Uri
import app.ammusic.data.library.AlbumRow
import app.ammusic.data.library.ArtistRow
import app.ammusic.data.library.HistoryRow
import app.ammusic.data.library.LibraryRepository
import app.ammusic.data.library.LikedRow
import app.ammusic.data.library.PlayRow
import app.ammusic.data.library.PlaylistRow
import app.ammusic.data.library.PlaylistTrackRow
import app.ammusic.data.library.TrackRow
import app.ammusic.youtube.tr
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class BackupFile(
    val format: String = "am-music-backup",
    val version: Int = 1,
    val exportedAt: Long,
    val settings: Settings,
    val tracks: List<TrackRow>,
    val liked: List<LikedRow>,
    val albums: List<AlbumRow>,
    val artists: List<ArtistRow>,
    val playlists: List<PlaylistRow>,
    val playlistTracks: List<PlaylistTrackRow>,
    val history: List<HistoryRow>,
    val plays: List<PlayRow>,
)

class BackupSummary(val playlists: Int, val liked: Int, val plays: Int)

@Singleton
class Backup @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val library: LibraryRepository,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun export(target: Uri): Result<BackupSummary> = withContext(Dispatchers.IO) {
        runCatching {
            val dao = library.dao
            val playlists = dao.localPlaylists()
            val links = dao.localPlaylistTracks()
            val liked = dao.localLiked()
            val history = dao.allHistory()
            val plays = dao.allPlays()
            val albums = dao.localAlbums()
            val artists = dao.localArtists()
            val used = (links.map { it.trackId } + liked.map { it.trackId } + history.map { it.trackId } + plays.map { it.trackId }).toSet()
            val file = BackupFile(
                exportedAt = System.currentTimeMillis(),
                settings = UserSettings.current,
                tracks = dao.allTracks().filter { it.id in used },
                liked = liked,
                albums = albums,
                artists = artists,
                playlists = playlists,
                playlistTracks = links,
                history = history,
                plays = plays,
            )
            context.contentResolver.openOutputStream(target, "wt")?.use { it.write(json.encodeToString(BackupFile.serializer(), file).toByteArray()) }
                ?: error("fichier inaccessible")
            BackupSummary(playlists.size, liked.size, plays.size)
        }
    }

    suspend fun import(source: Uri): Result<BackupSummary> = withContext(Dispatchers.IO) {
        runCatching {
            val text = context.contentResolver.openInputStream(source)?.use { it.readBytes().decodeToString() } ?: error("fichier inaccessible")
            val file = json.decodeFromString(BackupFile.serializer(), text)
            require(file.format == "am-music-backup") { "ce fichier n'est pas une sauvegarde AM Music" }
            val dao = library.dao
            dao.insertTracksIfMissing(file.tracks)
            dao.upsertLiked(file.liked)
            dao.upsertAlbums(file.albums)
            dao.upsertArtists(file.artists)
            dao.upsertPlaylists(file.playlists)
            dao.upsertPlaylistTracks(file.playlistTracks)
            dao.upsertHistoryRows(file.history)
            file.plays.filter { dao.countPlay(it.trackId, it.startedAt) == 0 }.forEach { dao.insertPlay(it.copy(id = 0)) }
            UserSettings.update { file.settings.copy(language = language) }
            BackupSummary(file.playlists.size, file.liked.size, file.plays.size)
        }
    }

    fun suggestedName(): String {
        val day = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT).format(java.util.Date())
        return tr("am-music-sauvegarde-$day.json", "am-music-backup-$day.json")
    }
}
