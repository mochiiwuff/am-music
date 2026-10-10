package app.ammusic.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.ammusic.data.MusicRepository
import app.ammusic.youtube.model.YtTrack
import app.ammusic.youtube.model.thumbnailAt
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class RepeatMode { Off, All, One }

data class PlayerState(
    val track: YtTrack? = null,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val hasNext: Boolean = false,
    val failed: Boolean = false,
    val durationMs: Long = 0L,
    val queue: List<YtTrack> = emptyList(),
    val index: Int = 0,
    val shuffle: Boolean = false,
    val repeat: RepeatMode = RepeatMode.Off,
    val liked: Boolean = false,
)

/** Pont entre l'interface et [PlaybackService]. Un titre n'apparaît qu'une fois dans la file. */
@Singleton
class PlayerController @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: MusicRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private var connection: Deferred<MediaController>? = null
    private var player: MediaController? = null
    private var radio: Job? = null
    private val liked = HashSet<String>()

    /** Position de lecture actuelle, à lire depuis l'interface (elle change en continu). */
    val positionMs: Long get() = player?.currentPosition ?: 0L

    init {
        scope.launch { runCatching { controller() } }
    }

    /** Lance [track] ; la radio YouTube Music de ce titre complète ensuite la file, sans fin. */
    fun play(track: YtTrack) = withPlayer { player ->
        radio?.cancel()
        player.setMediaItem(track.toMediaItem())
        player.prepare()
        player.play()
        extendQueue(player)
    }

    /**
     * Lance une liste (album, playlist) à partir de [startIndex]. [shuffle] force le mode aléatoire
     * (on part alors d'un titre au hasard) ou l'arrêt de ce mode ; `null` laisse le réglage tel quel.
     */
    fun playAll(tracks: List<YtTrack>, startIndex: Int = 0, shuffle: Boolean? = null) = withPlayer { player ->
        val items = tracks.distinctBy { it.id }
        if (items.isEmpty()) return@withPlayer
        radio?.cancel()
        if (shuffle != null) player.shuffleModeEnabled = shuffle
        val start = if (shuffle == true) items.indices.random() else items.indexOfFirst { it.id == tracks.getOrNull(startIndex)?.id }
        player.setMediaItems(items.map { it.toMediaItem() }, start.coerceAtLeast(0), C.TIME_UNSET)
        player.prepare()
        player.play()
        extendQueue(player)
    }

    /** Place [track] juste après le titre en cours. */
    fun playNext(track: YtTrack) = insert(track) { player -> player.currentMediaItemIndex + 1 }

    /** Place [track] à la fin de la file. */
    fun addToQueue(track: YtTrack) = insert(track) { player -> player.mediaItemCount }

    private fun insert(track: YtTrack, position: (MediaController) -> Int) = withPlayer { player ->
        if (player.mediaItemCount == 0) {
            play(track)
            return@withPlayer
        }
        if (player.currentMediaItem?.mediaId == track.id) return@withPlayer
        val existing = (0 until player.mediaItemCount).firstOrNull { player.getMediaItemAt(it).mediaId == track.id }
        if (existing != null) player.removeMediaItem(existing)
        player.addMediaItem(position(player).coerceAtMost(player.mediaItemCount), track.toMediaItem())
    }

    fun toggle() = withPlayer { player ->
        when {
            player.isPlaying -> player.pause()
            player.playerError != null || player.playbackState == Player.STATE_IDLE -> {
                player.prepare()
                player.play()
            }
            else -> player.play()
        }
    }

    fun next() = withPlayer { it.seekToNextMediaItem() }

    /** Revient au début du titre, ou au titre précédent si on est dans les premières secondes. */
    fun previous() = withPlayer { it.seekToPrevious() }

    fun seekTo(positionMs: Long) = withPlayer { it.seekTo(positionMs) }

    fun playAt(index: Int) = withPlayer { player ->
        if (index in 0 until player.mediaItemCount) {
            player.seekToDefaultPosition(index)
            player.prepare()
            player.play()
        }
    }

    fun move(from: Int, to: Int) = withPlayer { it.moveMediaItem(from, to) }

    fun remove(index: Int) = withPlayer { it.removeMediaItem(index) }

    fun toggleShuffle() = withPlayer { it.shuffleModeEnabled = !it.shuffleModeEnabled }

    fun cycleRepeat() = withPlayer { player ->
        player.repeatMode = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    /** « J'aime » gardé en mémoire seulement : la vraie bibliothèque arrive en phase 5. */
    fun toggleLike() = withPlayer { player ->
        val id = player.currentMediaItem?.mediaId ?: return@withPlayer
        if (!liked.add(id)) liked.remove(id)
        publish(player)
    }

    private fun withPlayer(block: (MediaController) -> Unit) {
        scope.launch { block(controller()) }
    }

    /** Quand il reste peu de titres devant, ajoute la suite de la radio du dernier titre de la file. */
    private fun extendQueue(player: MediaController) {
        if (radio?.isActive == true || player.mediaItemCount == 0) return
        if (player.mediaItemCount - player.currentMediaItemIndex > 3) return
        val seed = player.getMediaItemAt(player.mediaItemCount - 1).mediaId
        radio = scope.launch {
            val known = (0 until player.mediaItemCount).mapTo(HashSet()) { player.getMediaItemAt(it).mediaId }
            val more = repository.radio(seed).getOrNull().orEmpty().filter { known.add(it.id) }
            if (more.isNotEmpty()) player.addMediaItems(more.map { it.toMediaItem() })
        }
    }

    private suspend fun controller(): MediaController {
        val pending = connection ?: scope.async {
            val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
            val future = MediaController.Builder(context, token).buildAsync()
            val controller = suspendCancellableCoroutine { continuation ->
                future.addListener(
                    { runCatching { future.get() }.fold(continuation::resume, continuation::resumeWithException) },
                    MoreExecutors.directExecutor(),
                )
            }
            controller.addListener(object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) {
                    publish(player)
                    if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) extendQueue(controller)
                }
            })
            player = controller
            publish(controller)
            controller
        }.also { connection = it }
        return try {
            pending.await()
        } catch (e: Exception) {
            connection = null
            throw e
        }
    }

    private fun publish(player: Player) {
        val queue = List(player.mediaItemCount) { player.getMediaItemAt(it).toTrack() }
        val current = player.currentMediaItem?.toTrack()
        _state.value = PlayerState(
            track = current,
            isPlaying = player.isPlaying,
            isLoading = player.playWhenReady && player.playbackState == Player.STATE_BUFFERING,
            hasNext = player.hasNextMediaItem(),
            failed = player.playerError != null,
            durationMs = player.duration.takeIf { it != C.TIME_UNSET }?.coerceAtLeast(0L) ?: 0L,
            queue = queue,
            index = player.currentMediaItemIndex,
            shuffle = player.shuffleModeEnabled,
            repeat = when (player.repeatMode) {
                Player.REPEAT_MODE_ALL -> RepeatMode.All
                Player.REPEAT_MODE_ONE -> RepeatMode.One
                else -> RepeatMode.Off
            },
            liked = current?.id in liked,
        )
    }
}

private fun MediaItem.toTrack() = YtTrack(
    id = mediaId,
    title = mediaMetadata.title?.toString().orEmpty(),
    artists = mediaMetadata.artist?.toString().orEmpty(),
    album = mediaMetadata.albumTitle?.toString(),
    thumbnail = mediaMetadata.artworkUri?.toString(),
    artistId = mediaMetadata.extras?.getString(ArtistIdKey),
    albumId = mediaMetadata.extras?.getString(AlbumIdKey),
)

private const val ArtistIdKey = "artistId"
private const val AlbumIdKey = "albumId"

private fun YtTrack.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(id)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artists)
            .setAlbumTitle(album)
            .setArtworkUri(thumbnail?.thumbnailAt(544)?.toUri())
            .setExtras(bundleOf(ArtistIdKey to artistId, AlbumIdKey to albumId))
            .build(),
    )
    .build()
