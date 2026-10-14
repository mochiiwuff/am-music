package app.ammusic.playback

import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import app.ammusic.youtube.model.YtTrack
import app.ammusic.youtube.model.thumbnailAt

private const val ArtistIdKey = "artistId"
private const val AlbumIdKey = "albumId"
private const val DurationKey = "durationSeconds"

fun MediaItem.toTrack() = YtTrack(
    id = mediaId,
    title = mediaMetadata.title?.toString().orEmpty(),
    artists = mediaMetadata.artist?.toString().orEmpty(),
    album = mediaMetadata.albumTitle?.toString(),
    thumbnail = mediaMetadata.artworkUri?.toString(),
    durationSeconds = mediaMetadata.extras?.getInt(DurationKey, 0)?.takeIf { it > 0 },
    artistId = mediaMetadata.extras?.getString(ArtistIdKey),
    albumId = mediaMetadata.extras?.getString(AlbumIdKey),
)

fun YtTrack.toMediaItem(playable: Boolean = false): MediaItem = MediaItem.Builder()
    .setMediaId(id)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artists)
            .setAlbumTitle(album)
            .setArtworkUri(thumbnail?.thumbnailAt(544)?.toUri())
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .setExtras(bundleOf(ArtistIdKey to artistId, AlbumIdKey to albumId, DurationKey to (durationSeconds ?: 0)))
            .build(),
    )
    .apply { if (playable) setUri(trackUri(id)).setCustomCacheKey(id) }
    .build()

fun MediaItem.playable(): MediaItem = buildUpon().setUri(trackUri(mediaId)).setCustomCacheKey(mediaId).build()
