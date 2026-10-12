package app.ammusic.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
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
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import app.ammusic.MainActivity
import app.ammusic.R
import app.ammusic.data.UserSettings
import app.ammusic.youtube.YouTubeMusic
import app.ammusic.youtube.stream.StreamInfo
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import java.io.IOException

private const val TAG = "AmPlayback"
private const val MAX_RETRIES = 6

fun trackUri(videoId: String) = "ammusic://track/$videoId".toUri()

@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val retries = HashMap<String, Int>()
    private lateinit var player: ExoPlayer
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val cached = CacheDataSource.Factory()
            .setCache(audioCache(this))
            .setUpstreamDataSourceFactory(OkHttpDataSource.Factory(OkHttpClient()))
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        val resolving = ResolvingDataSource.Factory(cached) { spec ->
            val videoId = spec.key ?: throw IOException("Titre sans identifiant")
            val stream = try {
                runBlocking(Dispatchers.IO) { YouTubeMusic.streams.resolve(videoId, UserSettings.streamQuality(this@PlaybackService), sticky = spec.position > 0L) }
            } catch (e: Exception) {
                throw IOException(e)
            }
            if (spec.position == 0L) Log.i(TAG, "Flux $videoId via ${stream.source}/${stream.client}, plages de ${stream.chunkBytes} octets")
            spec.withStream(stream)
        }

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(resolving))
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        player.addListener(recovery)

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, player)
            .setSessionActivity(openApp)
            .setCallback(SessionCallback)
            .build()
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).build().apply { setSmallIcon(R.drawable.ic_notification) },
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        scope.cancel()
        session?.release()
        session = null
        player.release()
        super.onDestroy()
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

    private object SessionCallback : MediaSession.Callback {
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
        ): ListenableFuture<List<MediaItem>> = Futures.immediateFuture(
            mediaItems.map { it.buildUpon().setUri(trackUri(it.mediaId)).setCustomCacheKey(it.mediaId).build() },
        )
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
