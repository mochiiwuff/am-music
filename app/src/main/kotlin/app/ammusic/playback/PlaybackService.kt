package app.ammusic.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.audiofx.Equalizer
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import app.ammusic.MainActivity
import app.ammusic.R
import app.ammusic.data.UserSettings
import app.ammusic.data.downloads.DownloadRepository
import app.ammusic.data.equalizerGains
import app.ammusic.data.gainAt
import app.ammusic.data.library.LibraryRepository
import app.ammusic.data.library.toTrack
import app.ammusic.youtube.YouTubeMusic
import app.ammusic.youtube.stream.StreamInfo
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.PI
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import java.io.File
import java.io.IOException
import javax.inject.Inject

private const val TAG = "AmPlayback"
private const val MAX_RETRIES = 6

fun trackUri(videoId: String) = "ammusic://track/$videoId".toUri()

@OptIn(UnstableApi::class)
@AndroidEntryPoint
class PlaybackService : MediaLibraryService() {
    @Inject lateinit var library: LibraryRepository
    @Inject lateinit var downloads: DownloadRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val retries = HashMap<String, Int>()
    private lateinit var player: ExoPlayer
    private lateinit var tree: LibraryTree
    private var session: MediaLibrarySession? = null
    private var equalizer: Equalizer? = null
    private var saveJob: Job? = null
    private var widgetJob: Job? = null
    private var widgetArt: Pair<String, Pair<Bitmap?, Int?>>? = null
    private lateinit var sources: DefaultMediaSourceFactory
    private var fading: ExoPlayer? = null
    private var fadeJob: Job? = null
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private val musicAttributes = AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build()

    override fun onCreate() {
        super.onCreate()
        tree = LibraryTree(library, downloads)
        val cached = CacheDataSource.Factory()
            .setCache(audioCache(this))
            .setUpstreamDataSourceFactory(OkHttpDataSource.Factory(OkHttpClient()))
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        Loudness.init(this)
        val resolving = ResolvingDataSource.Factory(DefaultDataSource.Factory(this, cached)) { spec -> resolve(spec) }
        sources = DefaultMediaSourceFactory(resolving)
        player = newPlayer(focus = true)
        player.addListener(recovery)
        player.addListener(watcher)
        restoreQueue()

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaLibrarySession.Builder(this, player, callback)
            .setSessionActivity(openApp)
            .build()
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).build().apply { setSmallIcon(R.drawable.ic_notification) },
        )

        scope.launch {
            UserSettings.state.map { it.equalizerGains() }.distinctUntilChanged().collect(::applyEqualizer)
        }
        scope.launch {
            UserSettings.state.map { it.normalizeVolume }.distinctUntilChanged().collect { applyGain() }
        }
        scope.launch {
            while (true) {
                delay(250)
                maybeCrossfade()
            }
        }
        scope.launch {
            while (true) {
                delay(10_000)
                if (player.isPlaying) QueueStore.save(this@PlaybackService, player)
            }
        }
        updateWidget()
    }

    private fun newPlayer(focus: Boolean): ExoPlayer = ExoPlayer.Builder(this)
        .setMediaSourceFactory(sources)
        .setAudioAttributes(musicAttributes, focus)
        .setHandleAudioBecomingNoisy(true)
        .setWakeMode(C.WAKE_MODE_NETWORK)
        .build()

    private fun gainFor(item: MediaItem?): Float =
        if (UserSettings.current.normalizeVolume) Loudness.gain(item?.mediaId) else 1f

    private fun applyGain() {
        if (fadeJob?.isActive == true) return
        val gain = gainFor(player.currentMediaItem)
        if (gain != player.volume) Log.d(TAG, "volume réglé à ${"%.2f".format(gain)} pour ${player.currentMediaItem?.mediaId}")
        player.volume = gain
    }

    private fun maybeCrossfade() {
        val seconds = UserSettings.current.crossfadeSeconds
        if (seconds <= 0 || fadeJob?.isActive == true || PlaybackFlags.stopAtEnd) return
        val current = player
        if (!current.isPlaying || current.repeatMode == Player.REPEAT_MODE_ONE || !current.hasNextMediaItem()) return
        val duration = current.duration
        if (duration == C.TIME_UNSET || duration < seconds * 3_000L) return
        val remaining = duration - current.currentPosition
        if (remaining > seconds * 1_000L || remaining < 1_000L) return
        fadeJob = scope.launch { crossfade(current, (remaining - 250L).coerceIn(1_000L, seconds * 1_000L)) }
    }

    private suspend fun crossfade(outgoing: ExoPlayer, lengthMs: Long) {
        val nextIndex = outgoing.nextMediaItemIndex
        if (nextIndex == C.INDEX_UNSET) return
        val items = List(outgoing.mediaItemCount) { outgoing.getMediaItemAt(it) }
        val incoming = newPlayer(focus = false)
        incoming.setMediaItems(items, nextIndex, 0L)
        incoming.shuffleModeEnabled = outgoing.shuffleModeEnabled
        incoming.repeatMode = outgoing.repeatMode
        incoming.volume = 0f
        incoming.prepare()
        incoming.play()
        val ready = withTimeoutOrNull(6_000) {
            while (incoming.playbackState != Player.STATE_READY) delay(50)
            true
        }
        if (ready == null || player !== outgoing || !outgoing.isPlaying) {
            Log.w(TAG, "fondu annulé")
            incoming.release()
            return
        }
        outgoing.removeListener(recovery)
        outgoing.removeListener(watcher)
        outgoing.setAudioAttributes(musicAttributes, false)
        session?.player = incoming
        incoming.addListener(recovery)
        incoming.addListener(watcher)
        incoming.setAudioAttributes(musicAttributes, true)
        player = incoming
        fading = outgoing
        equalizer?.release()
        equalizer = null
        applyEqualizer(UserSettings.current.equalizerGains())
        Log.i(TAG, "fondu de ${lengthMs / 1000} s vers ${incoming.currentMediaItem?.mediaId}")
        val from = gainFor(outgoing.currentMediaItem)
        val to = gainFor(incoming.currentMediaItem)
        val start = System.currentTimeMillis()
        try {
            while (true) {
                val t = ((System.currentTimeMillis() - start).toFloat() / lengthMs).coerceIn(0f, 1f)
                if (!incoming.playWhenReady) break
                outgoing.volume = from * cos(t * PI.toFloat() / 2f)
                incoming.volume = to * sin(t * PI.toFloat() / 2f)
                if (t >= 1f || outgoing.playbackState == Player.STATE_ENDED) break
                delay(40)
            }
        } finally {
            incoming.volume = to
            outgoing.release()
            if (fading === outgoing) fading = null
        }
    }

    private fun resolve(spec: DataSpec): DataSpec {
        val videoId = spec.key ?: throw IOException("Titre sans identifiant")
        val offline = downloads.fileFor(videoId)
        if (offline != null) {
            if (spec.position == 0L) Log.i(TAG, "Lecture de $videoId depuis le téléphone")
            return spec.buildUpon().setUri(Uri.fromFile(offline)).setKey(null).build()
        }
        val stream = try {
            runBlocking(Dispatchers.IO) { YouTubeMusic.streams.resolve(videoId, UserSettings.streamQuality(this@PlaybackService), sticky = spec.position > 0L) }
        } catch (e: Exception) {
            throw IOException(e)
        }
        if (spec.position == 0L) Log.i(TAG, "Flux $videoId via ${stream.source}/${stream.client}, plages de ${stream.chunkBytes} octets")
        if (stream.loudnessDb != null && spec.position == 0L) {
            Loudness.put(videoId, stream.loudnessDb)
            main.post { if (player.currentMediaItem?.mediaId == videoId) applyGain() }
        }
        return spec.withStream(stream)
    }

    private fun restoreQueue() {
        val saved = QueueStore.load(this) ?: return
        player.setMediaItems(saved.tracks.map { it.toMediaItem(playable = true) }, saved.index, saved.positionMs)
        player.shuffleModeEnabled = saved.shuffle
        player.repeatMode = saved.repeat
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        QueueStore.save(this, player)
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        QueueStore.save(this, player)
        fadeJob?.cancel()
        fading?.release()
        fading = null
        scope.cancel()
        equalizer?.release()
        equalizer = null
        session?.release()
        session = null
        player.release()
        super.onDestroy()
    }

    private fun applyEqualizer(gains: List<Int>?) {
        if (gains == null) {
            equalizer?.enabled = false
            return
        }
        val eq = equalizer ?: runCatching { Equalizer(0, player.audioSessionId) }
            .onFailure { Log.w(TAG, "égaliseur indisponible : ${it.javaClass.simpleName}") }
            .getOrNull()
            ?.also { equalizer = it } ?: return
        runCatching {
            val range = eq.bandLevelRange
            for (band in 0 until eq.numberOfBands) {
                val hz = eq.getCenterFreq(band.toShort()) / 1000f
                val level = (gainAt(gains, hz) * 100).toInt().coerceIn(range[0].toInt(), range[1].toInt())
                eq.setBandLevel(band.toShort(), level.toShort())
            }
            eq.enabled = true
        }.onFailure { Log.w(TAG, "égaliseur : ${it.javaClass.simpleName}") }
    }

    private fun saveSoon() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(800)
            QueueStore.save(this@PlaybackService, player)
        }
    }

    private fun updateWidget() {
        if (!NowPlayingWidget.hasWidgets(this)) return
        val metadata = player.currentMediaItem?.mediaMetadata
        val playing = player.isPlaying || (player.playWhenReady && player.playbackState == Player.STATE_BUFFERING)
        val art = metadata?.artworkUri?.toString()
        val known = widgetArt?.takeIf { it.first == art }?.second
        val content = { cover: Bitmap?, tint: Int? -> WidgetContent(metadata?.title?.toString(), metadata?.artist?.toString(), playing, cover, tint) }
        NowPlayingWidget.push(this, content(known?.first, known?.second))
        if (known != null || art == null) return
        widgetJob?.cancel()
        widgetJob = scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { NowPlayingWidget.artwork(this@PlaybackService, art) }.getOrDefault(null to null) }
            widgetArt = art to result
            NowPlayingWidget.push(this@PlaybackService, content(result.first, result.second))
        }
    }

    private val watcher = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.containsAny(
                    Player.EVENT_TIMELINE_CHANGED,
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_IS_PLAYING_CHANGED,
                    Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                    Player.EVENT_REPEAT_MODE_CHANGED,
                )
            ) {
                saveSoon()
            }
            if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) applyGain()
            if (events.containsAny(
                    Player.EVENT_MEDIA_METADATA_CHANGED,
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_IS_PLAYING_CHANGED,
                    Player.EVENT_PLAYBACK_STATE_CHANGED,
                )
            ) {
                updateWidget()
            }
        }

        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            equalizer?.release()
            equalizer = null
            applyEqualizer(UserSettings.current.equalizerGains())
        }
    }

    private val recovery = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) player.currentMediaItem?.mediaId?.let(retries::remove)
        }

        override fun onPlayerError(error: PlaybackException) {
            val videoId = player.currentMediaItem?.mediaId ?: return
            val attempt = retries.getOrDefault(videoId, 0)
            Log.w(TAG, "Erreur de lecture $videoId (essai ${attempt + 1}) : ${error.errorCodeName} / ${error.cause?.message}")
            if (attempt >= MAX_RETRIES) {
                retries.remove(videoId)
                if (player.hasNextMediaItem()) {
                    player.seekToNextMediaItem()
                    player.prepare()
                }
                return
            }
            retries[videoId] = attempt + 1
            scope.launch {
                if (error.isHttpRejection()) {
                    withContext(Dispatchers.IO) { runCatching { YouTubeMusic.streams.reject(videoId) } }
                }
                delay(500)
                if (player.currentMediaItem?.mediaId == videoId) player.prepare()
            }
        }
    }

    private fun <T> later(block: suspend () -> T): ListenableFuture<T> {
        val future = SettableFuture.create<T>()
        scope.launch {
            runCatching { block() }.fold(future::set, future::setException)
        }
        return future
    }

    private suspend fun playableItems(items: List<MediaItem>): List<MediaItem> = items.map { item ->
        val id = item.mediaId.substringAfterLast(TreeSeparator)
        if (item.mediaMetadata.title != null) {
            item.buildUpon().setMediaId(id).build().playable()
        } else {
            withContext(Dispatchers.IO) { library.dao.track(id) }?.toTrack()?.toMediaItem(playable = true) ?: item.buildUpon().setMediaId(id).build().playable()
        }
    }

    private val callback = object : MediaLibrarySession.Callback {
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
        ): ListenableFuture<List<MediaItem>> = if (mediaItems.all { TreeSeparator !in it.mediaId && it.mediaMetadata.title != null }) {
            Futures.immediateFuture(mediaItems.map { it.playable() })
        } else {
            later { playableItems(mediaItems) }
        }

        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val single = mediaItems.singleOrNull()?.mediaId
            if (single == null || TreeSeparator !in single) {
                return if (mediaItems.all { it.mediaMetadata.title != null }) {
                    Futures.immediateFuture(MediaSession.MediaItemsWithStartPosition(mediaItems.map { it.playable() }, startIndex, startPositionMs))
                } else {
                    later { MediaSession.MediaItemsWithStartPosition(playableItems(mediaItems), startIndex, startPositionMs) }
                }
            }
            val parent = single.substringBeforeLast(TreeSeparator)
            val trackId = single.substringAfterLast(TreeSeparator)
            return later {
                val tracks = tree.tracks(parent).distinctBy { it.id }
                val index = tracks.indexOfFirst { it.id == trackId }
                if (index < 0) {
                    MediaSession.MediaItemsWithStartPosition(playableItems(mediaItems), 0, startPositionMs)
                } else {
                    MediaSession.MediaItemsWithStartPosition(tracks.map { it.toMediaItem(playable = true) }, index, startPositionMs)
                }
            }
        }

        @Deprecated("Remplacée par la version avec isForPlayback")
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val saved = QueueStore.load(this@PlaybackService)
                ?: return Futures.immediateFailedFuture(UnsupportedOperationException("Aucune file enregistrée"))
            return Futures.immediateFuture(
                MediaSession.MediaItemsWithStartPosition(saved.tracks.map { it.toMediaItem(playable = true) }, saved.index, saved.positionMs),
            )
        }

        @Suppress("DEPRECATION")
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            isForPlayback: Boolean,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = onPlaybackResumption(mediaSession, controller)

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> = Futures.immediateFuture(LibraryResult.ofItem(tree.root(), params))

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val folder = tree.folderFor(mediaId)
            if (folder != null) return Futures.immediateFuture(LibraryResult.ofItem(folder, null))
            return later {
                val track = withContext(Dispatchers.IO) { library.dao.track(mediaId.substringAfterLast(TreeSeparator)) }
                if (track != null) LibraryResult.ofItem(track.toTrack().toMediaItem(), null) else LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
            }
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = later {
            val all = runCatching { tree.children(parentId) }.getOrDefault(emptyList())
            val from = (page * pageSize).coerceIn(0, all.size)
            val to = (from + pageSize).coerceIn(from, all.size)
            LibraryResult.ofItemList(ImmutableList.copyOf(all.subList(from, to)), params)
        }
    }
}

private fun PlaybackException.isHttpRejection(): Boolean {
    var cause: Throwable? = this
    while (cause != null) {
        if (cause is HttpDataSource.InvalidResponseCodeException) return cause.responseCode in setOf(403, 410, 416)
        cause = cause.cause
    }
    return false
}

@OptIn(UnstableApi::class)
private fun DataSpec.withStream(stream: StreamInfo): DataSpec {
    val total = stream.contentLength
    val format = if (key != null && total != null) "$key@$total" else key
    val resolved = buildUpon().setUri(stream.url.toUri()).setKey(format).setHttpRequestHeaders(httpRequestHeaders + stream.headers).build()
    if (stream.chunkBytes <= 0L) return resolved
    val bounded = if (length == C.LENGTH_UNSET.toLong()) stream.chunkBytes else minOf(length, stream.chunkBytes)
    return resolved.subrange(0, bounded)
}

private var cache: SimpleCache? = null

@OptIn(UnstableApi::class)
@Synchronized
private fun audioCache(context: Context): SimpleCache = cache ?: SimpleCache(
    File(context.cacheDir, "audio"),
    LeastRecentlyUsedCacheEvictor(UserSettings.current.cacheMb * 1024L * 1024L),
    StandaloneDatabaseProvider(context.applicationContext),
).also { cache = it }

@OptIn(UnstableApi::class)
fun audioCacheBytes(context: Context): Long = audioCache(context).cacheSpace

@OptIn(UnstableApi::class)
fun clearAudioCache(context: Context, keep: String?) {
    val cache = audioCache(context)
    cache.keys.toList().filterNot { keep != null && it.startsWith(keep) }.forEach(cache::removeResource)
}
