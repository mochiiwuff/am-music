package app.ammusic.data.library

import app.ammusic.youtube.tr

import android.content.Context
import android.util.Log
import androidx.core.content.edit
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

private const val LocalPrefix = "LOCAL_"
private const val StaleAfterMs = 10 * 60 * 1000L
private const val RecentMs = 10 * 60 * 1000L
private const val RefreshEveryMs = 2 * 60 * 1000L
private const val MaxPlaylistPages = 20
private const val Tag = "AmLibrary"
private const val OwnersCheckedKey = "playlistOwnersChecked"

const val CoverSeparator = '\n'

data class LibraryNotice(val text: String, val failed: Boolean = false)

fun songCount(count: Int): String = when (count) {
    0 -> tr("Aucun titre", "No songs")
    1 -> tr("1 titre", "1 song")
    else -> tr("$count titres", "$count songs")
}

fun playlistCover(thumbnails: List<String>): String? {
    val distinct = thumbnails.distinct()
    return if (distinct.size >= 4) distinct.take(4).joinToString(CoverSeparator.toString()) else distinct.firstOrNull()
}

@Singleton
class LibraryRepository @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("library", Context.MODE_PRIVATE)
    internal val dao = Room.databaseBuilder(context, LibraryDatabase::class.java, "library.db").addMigrations(Migration1To2, Migration2To3).build().dao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lastSync = 0L
    private val refreshedAt = ConcurrentHashMap<String, Long>()
    private val removedAt = ConcurrentHashMap<Pair<String, String>, Long>()
    private val _notices = MutableSharedFlow<LibraryNotice>(extraBufferCapacity = 8)
    val notices: SharedFlow<LibraryNotice> = _notices.asSharedFlow()

    val liked: Flow<List<YtTrack>> = dao.liked().map { rows -> rows.map { it.toTrack() } }
    val history: Flow<List<YtTrack>> = dao.history().map { rows -> rows.map { it.toTrack() } }
    val albums: Flow<List<YtAlbum>> = dao.albums().map { rows -> rows.map { it.toAlbum() } }
    val artists: Flow<List<YtArtist>> = dao.artists().map { rows -> rows.map { YtArtist(it.id, it.name, it.thumbnail) } }
    val playlists: Flow<List<YtPlaylist>> = dao.playlists().map { rows -> rows.map { it.toPlaylist() } }

    val likedIds: StateFlow<Set<String>> = dao.likedIds().map { it.toSet() }.stateIn(scope, SharingStarted.Eagerly, emptySet())

    val savedIds: StateFlow<Set<String>> = combine(dao.albums(), dao.artists(), dao.playlists()) { albums, artists, playlists ->
        buildSet {
            albums.forEach { add(it.id) }
            artists.forEach { add(it.id) }
            playlists.forEach { add(it.id) }
        }
    }.stateIn(scope, SharingStarted.Eagerly, emptySet())

    private val signedIn get() = YouTubeMusic.isSignedIn

    init {
        scope.launch { dao.editablePlaylistIds().forEach { refreshSummary(it) } }
    }

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

    fun togglePlaylist(playlist: YtPlaylist) = scope.launch {
        val save = playlist.id !in savedIds.value
        if (save) dao.upsertPlaylists(listOf(playlist.toRow(now(), PlaylistKind.Saved))) else dao.deletePlaylistRow(playlist.id)
        if (signedIn) YouTubeMusic.save(playlist.id, save)
    }

    suspend fun createPlaylist(title: String): String? = withContext(Dispatchers.IO) {
        if (signedIn) {
            val id = YouTubeMusic.createPlaylist(title).getOrElse { error ->
                Log.w(Tag, "création de playlist : échec (${error.javaClass.simpleName} : ${error.message?.take(200)})")
                notify(tr("La playlist n'a pas pu être créée", "The playlist could not be created"), failed = true)
                return@withContext null
            }
            dao.upsertPlaylists(listOf(PlaylistRow(id, title, songCount(0), null, now(), PlaylistKind.Mine)))
            id
        } else {
            val id = LocalPrefix + UUID.randomUUID()
            dao.upsertPlaylists(listOf(PlaylistRow(id, title, songCount(0), null, now(), PlaylistKind.Local)))
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

    suspend fun isMine(playlistId: String): Boolean = dao.playlistRow(playlistId)?.kind == PlaylistKind.Mine

    fun addToPlaylist(playlistId: String, track: YtTrack, quiet: Boolean = false) = scope.launch {
        val playlist = dao.playlistRow(playlistId) ?: return@launch
        if (dao.countPlaylistTrack(playlistId, track.id) > 0) {
            notify(tr("Déjà dans « ${playlist.title} »", "Already in “${playlist.title}”"))
            return@launch
        }
        dao.upsertTracks(listOf(track.toRow()))
        dao.insertPlaylistTrack(PlaylistTrackRow(playlistId, track.id, now()))
        refreshSummary(playlistId)
        val added = tr("Ajouté à « ${playlist.title} »", "Added to “${playlist.title}”")
        if (isLocal(playlistId)) {
            if (!quiet) notify(added)
            return@launch
        }
        twice { YouTubeMusic.addToPlaylist(playlistId, track.id) }.fold(
            onSuccess = { setVideoId ->
                if (setVideoId != null) dao.setSetVideoId(playlistId, track.id, setVideoId)
                if (!quiet) notify(added)
                if (now() - (refreshedAt[playlistId] ?: 0L) > RefreshEveryMs) refreshPlaylist(playlistId)
            },
            onFailure = { error ->
                Log.w(Tag, "ajout à la playlist : échec (${error.javaClass.simpleName} : ${error.message?.take(200)})")
                dao.deletePlaylistTrack(playlistId, track.id)
                refreshSummary(playlistId)
                notify(tr("L'ajout à « ${playlist.title} » a échoué", "Could not add to “${playlist.title}”"), failed = true)
            },
        )
    }

    fun addAllToPlaylist(playlistId: String, title: String, tracks: List<YtTrack>) = scope.launch {
        val start = now()
        val fresh = tracks.distinctBy { it.id }.filter { dao.countPlaylistTrack(playlistId, it.id) == 0 }
        fresh.forEachIndexed { index, track ->
            remember(track)
            dao.insertPlaylistTrack(PlaylistTrackRow(playlistId, track.id, start + index))
        }
        refreshSummary(playlistId)
        if (!isLocal(playlistId)) {
            var failed = 0
            for (track in fresh) {
                twice { YouTubeMusic.addToPlaylist(playlistId, track.id) }.fold(
                    onSuccess = { setVideoId -> if (setVideoId != null) dao.setSetVideoId(playlistId, track.id, setVideoId) },
                    onFailure = {
                        failed++
                        dao.deletePlaylistTrack(playlistId, track.id)
                    },
                )
            }
            refreshSummary(playlistId)
            if (failed > 0) {
                notify(tr("$failed titres n'ont pas pu être ajoutés à « $title »", "$failed songs could not be added to “$title”"), failed = true)
                return@launch
            }
        }
        notify(tr("« $title » : ${songCount(fresh.size)}", "“$title”: ${songCount(fresh.size)}"))
    }

    fun removeFromPlaylist(playlistId: String, track: YtTrack) = scope.launch {
        val known = track.setVideoId ?: dao.setVideoId(playlistId, track.id)
        dao.deletePlaylistTrack(playlistId, track.id)
        refreshSummary(playlistId)
        if (isLocal(playlistId)) return@launch
        removedAt[playlistId to track.id] = now()
        val setVideoId = known ?: remoteTracks(playlistId)?.firstOrNull { it.id == track.id }?.setVideoId
        val removed = setVideoId != null && twice { YouTubeMusic.removeFromPlaylist(playlistId, track.id, setVideoId) }.isSuccess
        if (!removed) {
            Log.w(Tag, "retrait de la playlist : échec pour ${track.id}")
            removedAt.remove(playlistId to track.id)
            refreshPlaylist(playlistId)
            notify(tr("Le retrait de « ${track.title} » a échoué", "Could not remove “${track.title}”"), failed = true)
        }
    }

    fun reorderPlaylist(playlistId: String, ordered: List<YtTrack>) = scope.launch {
        val before = dao.playlistTracks(playlistId).first().map { it.id }
        val after = ordered.map { it.id }.filter { it in before }
        if (before == after || after.size != before.size) return@launch
        dao.reorderPlaylist(playlistId, after, now())
        refreshSummary(playlistId)
        if (isLocal(playlistId)) return@launch
        val ids = dao.setVideoIds(playlistId).associate { it.trackId to it.setVideoId }
        if (after.any { ids[it] == null }) {
            Log.w(Tag, "réordonner $playlistId : identifiants de position manquants")
            notify(tr("L'ordre n'a pas pu être envoyé à votre compte", "The new order could not be sent to your account"), failed = true)
            return@launch
        }
        val current = before.toMutableList()
        for (index in after.indices.reversed()) {
            val id = after[index]
            val successor = after.getOrNull(index + 1)
            val at = current.indexOf(id)
            if (current.getOrNull(at + 1) == successor) continue
            val moved = twice { YouTubeMusic.movePlaylistTrack(playlistId, ids.getValue(id)!!, successor?.let { ids.getValue(it) }) }
            if (moved.isFailure) {
                Log.w(Tag, "réordonner $playlistId : ${moved.exceptionOrNull()?.message?.take(200)}")
                notify(tr("L'ordre n'a pas pu être envoyé à votre compte", "The new order could not be sent to your account"), failed = true)
                return@launch
            }
            current.removeAt(at)
            current.add(successor?.let { current.indexOf(it) } ?: current.size, id)
        }
        refreshedAt[playlistId] = now()
    }

    fun playlistWithTracks(id: String): Flow<Pair<YtPlaylist?, List<YtTrack>>> =
        combine(dao.playlist(id), dao.playlistTracks(id)) { row, tracks -> row?.toPlaylist() to tracks.map { it.toTrack() } }

    suspend fun refreshPlaylist(playlistId: String): Boolean = withContext(Dispatchers.IO) {
        if (dao.playlistRow(playlistId) == null) return@withContext false
        val tracks = remoteTracks(playlistId) ?: return@withContext false
        val recent = now() - RecentMs
        val kept = tracks.filterNot { (removedAt[playlistId to it.id] ?: 0L) > recent }
        dao.replacePlaylistTracks(
            playlistId = playlistId,
            tracks = kept.map { it.toRow() },
            rows = kept.mapIndexed { index, track -> PlaylistTrackRow(playlistId, track.id, index.toLong(), track.setVideoId) },
            keepAfter = recent,
        )
        refreshedAt[playlistId] = now()
        refreshSummary(playlistId)
        Log.d(Tag, "playlist $playlistId : ${kept.size} titres recopiés")
        true
    }

    private suspend fun remoteTracks(playlistId: String): List<YtTrack>? {
        val page = YouTubeMusic.playlist(playlistId).getOrElse { return null }
        val tracks = page.tracks.toMutableList()
        var token = page.continuation
        var pages = 0
        while (token != null && pages++ < MaxPlaylistPages) {
            val (more, next) = YouTubeMusic.playlistMore(token).getOrElse { return null }
            tracks += more
            token = next
        }
        return tracks.distinctBy { it.id }
    }

    private suspend fun refreshSummary(playlistId: String) {
        val count = dao.playlistTrackCount(playlistId)
        val mirrored = isLocal(playlistId) || count > 0 || refreshedAt.containsKey(playlistId)
        if (mirrored) dao.setPlaylistSummary(playlistId, playlistCover(dao.leadingThumbnails(playlistId)), songCount(count))
    }

    private suspend fun <T> twice(call: suspend () -> Result<T>): Result<T> {
        val first = call()
        if (first.isSuccess) return first
        delay(1_200)
        return call()
    }

    fun announce(text: String) = notify(text)

    private fun notify(text: String, failed: Boolean = false) {
        _notices.tryEmit(LibraryNotice(text, failed))
    }

    fun recordPlay(track: YtTrack) = scope.launch {
        remember(track)
        dao.upsertHistory(HistoryRow(track.id, now()))
    }

    fun recordListen(track: YtTrack, startedAt: Long, listenedMs: Long) = scope.launch {
        remember(track)
        dao.insertPlay(PlayRow(trackId = track.id, startedAt = startedAt, listenedMs = listenedMs))
    }

    fun trackPlays(since: Long): Flow<List<Pair<YtTrack, TrackPlays>>> = dao.trackPlays(since).map { rows -> rows.map { it.track.toTrack() to it } }

    fun clearPlays() = scope.launch { dao.clearPlays() }

    private suspend fun remember(track: YtTrack) {
        val known = dao.track(track.id)
        if (known == null) {
            dao.upsertTracks(listOf(track.toRow()))
        } else {
            dao.upsertTracks(
                listOf(
                    known.copy(
                        title = track.title.ifBlank { known.title },
                        artists = track.artists.ifBlank { known.artists },
                        album = track.album ?: known.album,
                        thumbnail = track.thumbnail ?: known.thumbnail,
                        durationSeconds = track.durationSeconds ?: known.durationSeconds,
                        artistId = track.artistId ?: known.artistId,
                        albumId = track.albumId ?: known.albumId,
                    ),
                ),
            )
        }
    }

    fun clearHistory() = scope.launch { dao.clearHistory() }

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
                    val mine = ownedAmong(playlists)
                    dao.replaceRemotePlaylists(
                        rows = playlists.mapIndexed { index, playlist ->
                            playlist.toRow(start - index, if (playlist.id in mine) PlaylistKind.Mine else PlaylistKind.Saved)
                        },
                        keepAfter = start - RecentMs,
                    )
                    dao.editablePlaylistIds().forEach { refreshSummary(it) }
                }
            }
        }
    }

    private suspend fun ownedAmong(playlists: List<YtPlaylist>): Set<String> {
        val mine = dao.minePlaylistIds().toMutableSet()
        val known = if (prefs.getBoolean(OwnersCheckedKey, false)) dao.playlistIds().toSet() else emptySet()
        var complete = true
        for (playlist in playlists) {
            if (playlist.id in mine) continue
            if (playlist.editable) {
                mine += playlist.id
                continue
            }
            if (playlist.id in known) continue
            val page = YouTubeMusic.playlist(playlist.id).getOrNull()
            if (page == null) complete = false else if (page.playlist.editable) mine += playlist.id
        }
        if (complete) prefs.edit { putBoolean(OwnersCheckedKey, true) }
        Log.d(Tag, "playlists à soi : ${mine.size} sur ${playlists.size}")
        return mine
    }

    suspend fun clearRemote() = withContext(Dispatchers.IO) {
        dao.clearRemoteLiked()
        dao.clearRemoteAlbums()
        dao.clearRemoteArtists()
        dao.clearRemotePlaylists(Long.MAX_VALUE)
        dao.deleteOrphanPlaylistTracks()
        refreshedAt.clear()
        removedAt.clear()
        lastSync = 0L
    }

    private fun now() = System.currentTimeMillis()
}

internal fun YtTrack.toRow() = TrackRow(id, title, artists, album, thumbnail, durationSeconds, artistId, albumId)

internal fun TrackRow.toTrack() = YtTrack(id, title, artists, album, thumbnail, durationSeconds, artistId, albumId)

private fun YtAlbum.toRow(addedAt: Long, local: Boolean) = AlbumRow(id, playlistId, title, subtitle, thumbnail, artistId, year, addedAt, local)

private fun AlbumRow.toAlbum() = YtAlbum(id, playlistId, title, subtitle, thumbnail, artistId, year)

private fun YtPlaylist.toRow(addedAt: Long, kind: Int) = PlaylistRow(id, title, subtitle, thumbnail, addedAt, kind)

private fun PlaylistRow.toPlaylist() = YtPlaylist(id, title, subtitle, thumbnail, editable = kind != PlaylistKind.Saved)

private fun <T> Result<T>.logged(what: String, size: (T) -> Int): Result<T> = also { result ->
    result.fold(
        onSuccess = { Log.d(Tag, "$what : ${size(it)} reçus") },
        onFailure = { Log.w(Tag, "$what : échec (${it.javaClass.simpleName} : ${it.message?.take(200)})") },
    )
}
