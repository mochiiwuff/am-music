package app.ammusic.playback

import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import app.ammusic.data.downloads.DownloadRepository
import app.ammusic.data.library.CoverSeparator
import app.ammusic.data.library.LibraryRepository
import app.ammusic.youtube.YouTubeMusic
import app.ammusic.youtube.model.YtTrack
import app.ammusic.youtube.model.thumbnailAt
import app.ammusic.youtube.tr
import kotlinx.coroutines.flow.first

private const val Root = "root"
private const val Liked = "liked"
private const val Playlists = "playlists"
private const val Albums = "albums"
private const val Downloads = "downloads"
private const val History = "history"
private const val PlaylistPrefix = "playlist:"
private const val AlbumPrefix = "album:"
const val TreeSeparator = '|'

class LibraryTree(private val library: LibraryRepository, private val downloads: DownloadRepository) {
    fun root(): MediaItem = folder(Root, "AM Music", null, MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)

    fun folderFor(id: String): MediaItem? = shelves().firstOrNull { it.mediaId == id } ?: if (id == Root) root() else null

    private fun shelves() = listOf(
        folder(Liked, tr("Titres aimés", "Liked songs"), null, MediaMetadata.MEDIA_TYPE_PLAYLIST),
        folder(Playlists, "Playlists", null, MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS),
        folder(Albums, "Albums", null, MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS),
        folder(Downloads, tr("Téléchargés", "Downloaded"), null, MediaMetadata.MEDIA_TYPE_PLAYLIST),
        folder(History, tr("Historique", "History"), null, MediaMetadata.MEDIA_TYPE_PLAYLIST),
    )

    suspend fun children(parentId: String): List<MediaItem> = when (parentId) {
        Root -> shelves()
        Playlists -> library.playlists.first().map {
            folder(PlaylistPrefix + it.id, it.title, it.thumbnail?.substringBefore(CoverSeparator), MediaMetadata.MEDIA_TYPE_PLAYLIST, it.subtitle)
        }
        Albums -> library.albums.first().map { folder(AlbumPrefix + it.id, it.title, it.thumbnail, MediaMetadata.MEDIA_TYPE_ALBUM, it.subtitle) }
        else -> tracks(parentId).map { track -> track.toMediaItem().buildUpon().setMediaId("$parentId$TreeSeparator${track.id}").build() }
    }

    suspend fun tracks(parentId: String): List<YtTrack> = when {
        parentId == Liked -> library.liked.first()
        parentId == Downloads -> downloads.tracks.first()
        parentId == History -> library.history.first()
        parentId.startsWith(PlaylistPrefix) -> {
            val id = parentId.removePrefix(PlaylistPrefix)
            library.playlistWithTracks(id).first().second.ifEmpty { YouTubeMusic.playlist(id).getOrNull()?.tracks.orEmpty() }
        }
        parentId.startsWith(AlbumPrefix) -> YouTubeMusic.album(parentId.removePrefix(AlbumPrefix)).getOrNull()?.tracks.orEmpty()
        else -> emptyList()
    }

    private fun folder(id: String, title: String, artwork: String?, type: Int, subtitle: String? = null): MediaItem = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setArtworkUri(artwork?.thumbnailAt(320)?.toUri())
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(type)
                .build(),
        )
        .build()
}
