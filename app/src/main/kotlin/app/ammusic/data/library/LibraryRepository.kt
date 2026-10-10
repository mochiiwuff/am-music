package app.ammusic.data.library

import android.content.Context
import android.util.Log
import androidx.room.Room
import app.ammusic.youtube.YouTubeMusic
import app.ammusic.youtube.model.YtAlbum
import app.ammusic.youtube.model.YtArtist
import app.ammusic.youtube.model.YtPlaylist
import app.ammusic.youtube.model.YtTrack
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private const val LocalPrefix = "LOCAL_"
private const val StaleAfterMs = 10 * 60 * 1000L

/**
 * Bibliothèque : titres aimés, albums, artistes, playlists, historique.
 * Tout est lu depuis la base du téléphone (rapide, disponible sans réseau). Connectée, l'appli y recopie
 * la bibliothèque du compte ([sync]) et répète chaque action auprès de YouTube ; en invité, tout reste local.
 */
@Singleton
class LibraryRepository @Inject constructor(@ApplicationContext context: Context) {
    private val dao = Room.databaseBuilder(context, LibraryDatabase::class.java, "library.db").build().dao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lastSync = 0L

    val liked: Flow<List<YtTrack>> = dao.liked().map { rows -> rows.map { it.toTrack() } }
    val history: Flow<List<YtTrack>> = dao.history().map { rows -> rows.map { it.toTrack() } }
    val albums: Flow<List<YtAlbum>> = dao.albums().map { rows -> rows.map { it.toAlbum() } }
    val artists: Flow<List<YtArtist>> = dao.artists().map { rows -> rows.map { YtArtist(it.id, it.name, it.thumbnail) } }
    val playlists: Flow<List<YtPlaylist>> = dao.playlists().map { rows -> rows.map { it.toPlaylist() } }

    val likedIds: StateFlow<Set<String>> = dao.likedIds().map { it.toSet() }.stateIn(scope, SharingStarted.Eagerly, emptySet())

    /** Identifiants des albums, artistes et playlists présents dans la bibliothèque. */
    val savedIds: StateFlow<Set<String>> = combine(dao.albums(), dao.artists(), dao.playlists()) { albums, artists, playlists ->
        buildSet {
            albums.forEach { add(it.id) }
            artists.forEach { add(it.id) }
            playlists.forEach { add(it.id) }
        }
    }.stateIn(scope, SharingStarted.Eagerly, emptySet())

    private val signedIn get() = YouTubeMusic.isSignedIn

    fun isLocal(playlistId: String) = playlistId.startsWith(LocalPrefix)

    fun toggleLike(track: YtTrack) = scope.launch {
        val like = track.id !in likedIds.value
        if (like) {
            dao.upsertTracks(listOf(track.toRow()))
            dao.insertLiked(listOf(LikedRow(track.id, now(), local = !signedIn)))
        } else {
            dao.deleteLiked(track.id)
        }
        if (signedIn) YouTubeMusic.like(track.id, like)
    }

    fun toggleAlbum(album: YtAlbum) = scope.launch {
        val save = album.id !in savedIds.value
        if (save) dao.insertAlbums(listOf(album.toRow(now(), local = !signedIn))) else dao.deleteAlbum(album.id)
        if (signedIn && album.playlistId.isNotEmpty()) YouTubeMusic.save(album.playlistId, save)
    }

    fun toggleArtist(artist: YtArtist) = scope.launch {
        val follow = artist.id !in savedIds.value
        if (follow) dao.insertArtists(listOf(ArtistRow(artist.id, artist.title, artist.thumbnail, now(), local = !signedIn))) else dao.deleteArtist(artist.id)
        if (signedIn) YouTubeMusic.follow(artist.id, follow)
    }

    /** Enregistre ou retire la playlist de quelqu'un d'autre. */
    fun togglePlaylist(playlist: YtPlaylist) = scope.launch {
        val save = playlist.id !in savedIds.value
        if (save) dao.upsertPlaylists(listOf(playlist.toRow(now(), PlaylistKind.Saved))) else dao.deletePlaylistRow(playlist.id)
        if (signedIn) YouTubeMusic.save(playlist.id, save)
    }

    /** Crée une playlist (sur le compte si connectée, sinon sur le téléphone) et renvoie son identifiant, ou `null` en cas d'échec. */
    suspend fun createPlaylist(title: String): String? = withContext(Dispatchers.IO) {
        if (signedIn) {
            val id = YouTubeMusic.createPlaylist(title).getOrNull() ?: return@withContext null
            dao.upsertPlaylists(listOf(PlaylistRow(id, title, "Votre playlist", null, now(), PlaylistKind.Mine)))
            id
        } else {
            val id = LocalPrefix + UUID.randomUUID()
            dao.upsertPlaylists(listOf(PlaylistRow(id, title, "Sur ce téléphone", null, now(), PlaylistKind.Local)))
            id
        }
    }

    fun renamePlaylist(id: String, title: String) = scope.launch {
        dao.renamePlaylist(id, title)
        if (!isLocal(id)) YouTubeMusic.renamePlaylist(id, title)
    }

    fun deletePlaylist(id: String) = scope.launch {
        dao.deletePlaylist(id)
        if (!isLocal(id)) YouTubeMusic.deletePlaylist(id)
    }

    fun addToPlaylist(playlistId: String, track: YtTrack) = scope.launch {
        if (isLocal(playlistId)) {
            dao.upsertTracks(listOf(track.toRow()))
            dao.insertPlaylistTrack(PlaylistTrackRow(playlistId, track.id, now()))
            dao.setPlaylistThumbnail(playlistId, dao.firstThumbnail(playlistId))
        } else {
            YouTubeMusic.addToPlaylist(playlistId, track.id)
        }
    }

    /** Pour une playlist du compte, [YtTrack.setVideoId] doit être renseigné (il l'est sur la page de la playlist). */
    fun removeFromPlaylist(playlistId: String, track: YtTrack) = scope.launch {
        if (isLocal(playlistId)) {
            dao.deletePlaylistTrack(playlistId, track.id)
            dao.setPlaylistThumbnail(playlistId, dao.firstThumbnail(playlistId))
        } else {
            track.setVideoId?.let { YouTubeMusic.removeFromPlaylist(playlistId, track.id, it) }
        }
    }

    fun localPlaylist(id: String): Flow<Pair<YtPlaylist?, List<YtTrack>>> =
        combine(dao.playlist(id), dao.playlistTracks(id)) { row, tracks -> row?.toPlaylist() to tracks.map { it.toTrack() } }

    fun recordPlay(track: YtTrack) = scope.launch {
        dao.upsertTracks(listOf(track.toRow()))
        dao.upsertHistory(HistoryRow(track.id, now()))
    }

    fun clearHistory() = scope.launch { dao.clearHistory() }

    /** Recopie la bibliothèque du compte dans la base. Sans effet en mode invité, ou si [force] est faux et la dernière copie récente. */
    suspend fun sync(force: Boolean = true) = withContext(Dispatchers.IO) {
        if (!signedIn || (!force && now() - lastSync < StaleAfterMs)) return@withContext
        lastSync = now()
        val start = now()
        coroutineScope {
            launch {
                YouTubeMusic.likedTracks().logged("titres aimés") { it.size }.onSuccess { tracks ->
                    dao.replaceRemoteLiked(tracks.map { it.toRow() }, tracks.mapIndexed { index, track -> LikedRow(track.id, start - index, local = false) })
                }
            }
            launch {
                YouTubeMusic.libraryAlbums().logged("albums") { it.size }.onSuccess { albums ->
                    dao.replaceRemoteAlbums(albums.mapIndexed { index, album -> album.toRow(start - index, local = false) })
                }
            }
            launch {
                YouTubeMusic.libraryArtists().logged("artistes") { it.size }.onSuccess { artists ->
                    dao.replaceRemoteArtists(artists.mapIndexed { index, artist -> ArtistRow(artist.id, artist.title, artist.thumbnail, start - index, local = false) })
                }
            }
            launch {
                YouTubeMusic.libraryPlaylists().logged("playlists") { it.size }.onSuccess { playlists ->
                    dao.replaceRemotePlaylists(
                        playlists.mapIndexed { index, playlist ->
                            playlist.toRow(start - index, if (playlist.editable) PlaylistKind.Mine else PlaylistKind.Saved)
                        },
                    )
                }
            }
        }
    }

    /** À la déconnexion : retire tout ce qui venait du compte, garde ce qui a été ajouté en invité. */
    suspend fun clearRemote() = withContext(Dispatchers.IO) {
        dao.clearRemoteLiked()
        dao.clearRemoteAlbums()
        dao.clearRemoteArtists()
        dao.clearRemotePlaylists()
        lastSync = 0L
    }

    private fun now() = System.currentTimeMillis()
}

private fun YtTrack.toRow() = TrackRow(id, title, artists, album, thumbnail, durationSeconds, artistId, albumId)

private fun TrackRow.toTrack() = YtTrack(id, title, artists, album, thumbnail, durationSeconds, artistId, albumId)

private fun YtAlbum.toRow(addedAt: Long, local: Boolean) = AlbumRow(id, playlistId, title, subtitle, thumbnail, artistId, year, addedAt, local)

private fun AlbumRow.toAlbum() = YtAlbum(id, playlistId, title, subtitle, thumbnail, artistId, year)

private fun YtPlaylist.toRow(addedAt: Long, kind: Int) = PlaylistRow(id, title, subtitle, thumbnail, addedAt, kind)

private fun PlaylistRow.toPlaylist() = YtPlaylist(id, title, subtitle, thumbnail, editable = kind != PlaylistKind.Saved)

/** Trace dans le journal (`adb logcat -s AmLibrary`) ce que la copie de la bibliothèque du compte a reçu, ou pourquoi elle a échoué. */
private fun <T> Result<T>.logged(what: String, size: (T) -> Int): Result<T> = also { result ->
    result.fold(
        onSuccess = { Log.d("AmLibrary", "$what : ${size(it)} reçus") },
        onFailure = { Log.w("AmLibrary", "$what : échec (${it.javaClass.simpleName} : ${it.message?.take(200)})") },
    )
}
