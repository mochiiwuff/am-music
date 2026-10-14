package app.ammusic.playback

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.ammusic.data.MusicRepository
import app.ammusic.data.SkipSegments
import app.ammusic.data.UserSettings
import app.ammusic.data.library.LibraryRepository
import app.ammusic.youtube.model.YtTrack
import app.ammusic.youtube.model.isVideoThumbnail
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class RepeatMode { Off, All, One }

data class SleepTimer(val endsAtMs: Long? = null, val endOfTrack: Boolean = false, val minutes: Int? = null) {
    val active: Boolean get() = endsAtMs != null || endOfTrack
}

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

@Singleton
class PlayerController @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: MusicRepository,
    private val library: LibraryRepository,
    private val segments: SkipSegments,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private var connection: Deferred<MediaController>? = null
    private var player: MediaController? = null
    private var radio: Job? = null
    private var coverJob: Job? = null
    private val suggested = HashSet<String>()
    private var skipJob: Job? = null
    private var sleepJob: Job? = null
    private var listening: Listening? = null
    private val _sleep = MutableStateFlow(SleepTimer())
    val sleep: StateFlow<SleepTimer> = _sleep.asStateFlow()
    private val covers = HashMap<String, String?>()

    val positionMs: Long get() = player?.currentPosition ?: 0L

    init {
        scope.launch { runCatching { controller() } }
        scope.launch { library.likedIds.collect { player?.let(::publish) } }
    }

    fun play(track: YtTrack) = withPlayer { player ->
        radio?.cancel()
        suggested.clear()
        player.setMediaItem(track.toMediaItem())
        player.prepare()
        player.play()
        extendQueue(player)
    }

    fun playAll(tracks: List<YtTrack>, startIndex: Int = 0, shuffle: Boolean? = null) = withPlayer { player ->
        val items = tracks.distinctBy { it.id }
        if (items.isEmpty()) return@withPlayer
        radio?.cancel()
        suggested.clear()
        if (shuffle != null) player.shuffleModeEnabled = shuffle
        val start = if (shuffle == true) items.indices.random() else items.indexOfFirst { it.id == tracks.getOrNull(startIndex)?.id }
        player.setMediaItems(items.map { it.toMediaItem() }, start.coerceAtLeast(0), C.TIME_UNSET)
        player.prepare()
        player.play()
        extendQueue(player)
    }

    fun playNext(track: YtTrack) = insert(track) { player -> player.currentMediaItemIndex + 1 }

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
        when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> {
                radio?.cancel()
                val last = player.mediaItemCount - 1
                for (index in last downTo player.currentMediaItemIndex + 1) {
                    if (player.getMediaItemAt(index).mediaId in suggested) player.removeMediaItem(index)
                }
                player.repeatMode = Player.REPEAT_MODE_ALL
            }
            Player.REPEAT_MODE_ALL -> player.repeatMode = Player.REPEAT_MODE_ONE
            else -> {
                player.repeatMode = Player.REPEAT_MODE_OFF
                extendQueue(player)
            }
        }
    }

    fun toggleLike() = withPlayer { player ->
        player.currentMediaItem?.toTrack()?.let(library::toggleLike)
    }

    private fun withPlayer(block: (MediaController) -> Unit) {
        scope.launch { block(controller()) }
    }

    private fun fixCover(player: MediaController) {
        val track = player.currentMediaItem?.toTrack() ?: return
        if (track.thumbnail?.isVideoThumbnail() != true) return
        coverJob?.cancel()
        coverJob = scope.launch {
            if (track.id !in covers) {
                covers[track.id] = repository.songCover(track).getOrElse { return@launch }
                Log.d("AmPlayback", "pochette de ${track.id} : ${if (covers[track.id] != null) "trouvée" else "aucune"}")
            }
            val cover = covers[track.id] ?: return@launch
            val index = (0 until player.mediaItemCount).firstOrNull { player.getMediaItemAt(it).mediaId == track.id } ?: return@launch
            val item = player.getMediaItemAt(index)
            val metadata = item.mediaMetadata.buildUpon().setArtworkUri(cover.thumbnailAt(544).toUri()).build()
            player.replaceMediaItem(index, item.buildUpon().setMediaMetadata(metadata).build())
        }
    }

    fun sleepIn(minutes: Int?) {
        sleepJob?.cancel()
        if (minutes == null) {
            _sleep.value = SleepTimer()
            return
        }
        _sleep.value = SleepTimer(endsAtMs = System.currentTimeMillis() + minutes * 60_000L, minutes = minutes)
        sleepJob = scope.launch {
            delay(minutes * 60_000L)
            val player = player
            if (player != null && player.isPlaying) {
                repeat(24) { step ->
                    player.volume = 1f - (step + 1) / 24f
                    delay(150)
                }
                player.pause()
                player.volume = 1f
            }
            _sleep.value = SleepTimer()
        }
    }

    fun sleepAtEndOfTrack() {
        sleepJob?.cancel()
        _sleep.value = SleepTimer(endOfTrack = true)
    }

    private fun trackListening(player: MediaController) {
        val track = player.currentMediaItem?.toTrack()
        if (listening?.track?.id != track?.id) {
            finishListening()
            listening = track?.let { Listening(it, System.currentTimeMillis()) }
        }
        val current = listening ?: return
        val now = System.currentTimeMillis()
        if (player.isPlaying && current.resumedAt == null) {
            current.resumedAt = now
            if (!current.recorded && !UserSettings.current.historyPaused) {
                current.recorded = true
                library.recordPlay(current.track)
            }
        } else if (!player.isPlaying && current.resumedAt != null) {
            current.listenedMs += now - (current.resumedAt ?: now)
            current.resumedAt = null
        }
    }

    private fun finishListening() {
        val done = listening ?: return
        listening = null
        val total = done.listenedMs + (done.resumedAt?.let { System.currentTimeMillis() - it } ?: 0L)
        if (total >= CountAfterMs && !UserSettings.current.historyPaused) library.recordListen(done.track, done.startedAt, total)
    }

    private fun watchSegments(player: MediaController) {
        skipJob?.cancel()
        if (!UserSettings.current.skipSegments) return
        val id = player.currentMediaItem?.mediaId ?: return
        skipJob = scope.launch {
            val parts = segments.find(id)
            if (parts.isEmpty()) return@launch
            while (player.currentMediaItem?.mediaId == id) {
                if (player.isPlaying) {
                    val position = player.currentPosition
                    parts.firstOrNull { position >= it.startMs && position < it.endMs - 300 }?.let { part ->
                        Log.d("AmPlayback", "passage sauté dans $id : ${part.startMs} → ${part.endMs} ms")
                        player.seekTo(part.endMs)
                    }
                }
                delay(350)
            }
        }
    }

    private fun extendQueue(player: MediaController) {
        if (!UserSettings.current.autoplay || player.repeatMode == Player.REPEAT_MODE_ALL) return
        if (radio?.isActive == true || player.mediaItemCount == 0) return
        if (player.mediaItemCount - player.currentMediaItemIndex > 3) return
        val seed = player.getMediaItemAt(player.mediaItemCount - 1).mediaId
        radio = scope.launch {
            val known = (0 until player.mediaItemCount).mapTo(HashSet()) { player.getMediaItemAt(it).mediaId }
            val more = repository.radio(seed).getOrNull().orEmpty().filter { known.add(it.id) }
            if (more.isNotEmpty() && player.repeatMode != Player.REPEAT_MODE_ALL) {
                suggested += more.map { it.id }
                player.addMediaItems(more.map { it.toMediaItem() })
            }
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
                    if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) {
                        extendQueue(controller)
                        fixCover(controller)
                        watchSegments(controller)
                    }
                    if (events.containsAny(Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_IS_PLAYING_CHANGED)) trackListening(controller)
                    if (player.playbackState == Player.STATE_ENDED && _sleep.value.endOfTrack) _sleep.value = SleepTimer()
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) finishListening()
                    if (_sleep.value.endOfTrack && (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT)) {
                        controller.pause()
                        _sleep.value = SleepTimer()
                    }
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
            liked = current?.id in library.likedIds.value,
        )
    }
}


private const val CountAfterMs = 30_000L

private class Listening(val track: YtTrack, val startedAt: Long) {
    var listenedMs = 0L
    var resumedAt: Long? = null
    var recorded = false
}
