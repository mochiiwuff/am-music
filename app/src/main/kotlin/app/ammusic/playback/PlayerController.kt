package app.ammusic.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.net.toUri
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

data class PlayerState(
    val track: YtTrack? = null,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val hasNext: Boolean = false,
    val failed: Boolean = false,
)

/** Pont entre l'interface et [PlaybackService]. */
@Singleton
class PlayerController @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: MusicRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private var connection: Deferred<MediaController>? = null
    private var radio: Job? = null

    init {
        scope.launch { runCatching { controller() } }
    }

    /** Lance [track], puis complète la file avec la radio YouTube Music de ce titre. */
    fun play(track: YtTrack) {
        scope.launch {
            val player = controller()
            radio?.cancel()
            player.setMediaItem(track.toMediaItem())
            player.prepare()
            player.play()
            radio = launch {
                val next = repository.radio(track.id).getOrNull().orEmpty().filter { it.id != track.id }
                if (player.currentMediaItem?.mediaId == track.id) player.addMediaItems(next.map { it.toMediaItem() })
            }
        }
    }

    fun toggle() {
        scope.launch {
            val player = controller()
            when {
                player.isPlaying -> player.pause()
                player.playerError != null || player.playbackState == Player.STATE_IDLE -> {
                    player.prepare()
                    player.play()
                }
                else -> player.play()
            }
        }
    }

    fun next() {
        scope.launch { controller().seekToNextMediaItem() }
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
                override fun onEvents(player: Player, events: Player.Events) = publish(player)
            })
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
        val item = player.currentMediaItem
        _state.value = PlayerState(
            track = item?.let {
                YtTrack(
                    id = it.mediaId,
                    title = it.mediaMetadata.title?.toString().orEmpty(),
                    artists = it.mediaMetadata.artist?.toString().orEmpty(),
                    thumbnail = it.mediaMetadata.artworkUri?.toString(),
                )
            },
            isPlaying = player.isPlaying,
            isLoading = player.playWhenReady && player.playbackState == Player.STATE_BUFFERING,
            hasNext = player.hasNextMediaItem(),
            failed = player.playerError != null,
        )
    }
}

private fun YtTrack.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(id)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artists)
            .setAlbumTitle(album)
            .setArtworkUri(thumbnail?.thumbnailAt(544)?.toUri())
            .build(),
    )
    .build()
