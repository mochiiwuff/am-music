package app.ammusic.youtube.stream

import android.content.Context
import android.util.Log
import com.metrolist.innertube.YouTube
import com.metrolist.innertubex.InnerTubeLogLevel
import com.metrolist.innertubex.InnerTubeLogger
import com.metrolist.innertubex.cipher.PlayerConfigRepository
import com.metrolist.innertubex.cipher.RemotePlayerConfigStore
import com.metrolist.innertubex.cipher.YouTubeCipherService
import com.metrolist.innertubex.extraction.ContentHints
import com.metrolist.innertubex.extraction.InnerTubeExtractor
import com.metrolist.innertubex.extraction.PoTokenResult
import com.metrolist.innertubex.extraction.TokenProvider
import com.metrolist.innertubex.extraction.TokenProviderCapabilities
import com.metrolist.innertubex.extraction.YtConfigParser
import com.metrolist.innertubex.extraction.YtConfigParserImpl
import com.metrolist.innertubex.extraction.generateClientPlaybackNonce
import com.metrolist.innertubex.extraction.strategy.PoTokenProviderKind
import com.metrolist.music.utils.potoken.PoTokenGenerator
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.ExperimentalTime
import com.metrolist.innertubex.extraction.AudioQuality as XAudioQuality

private const val TAG = "InnerTubeXSource"
private const val CLIENT_FAILURE_TTL_MS = 5 * 60 * 1000L
private const val DEFAULT_TTL_MS = 5 * 60 * 1000L
private const val PLAYER_CONFIG_URL =
    "https://raw.githubusercontent.com/ZemerTeam/zemer-cipher/master/library/src/main/assets/player_configs.json"

/**
 * Méthode principale : le moteur d'extraction de Metrolist (innertubex).
 * Adapté de `InnerTubeXPlayer.kt` (Metrolist, GPL-3.0). Voir docs/youtube.md.
 */
class InnerTubeXSource(context: Context) : StreamSource {
    override val name = "innertubex"

    private val appContext = context.applicationContext
    private val bundleMutex = Mutex()
    private val failures = ConcurrentHashMap<String, FailedClients>()

    @Volatile
    private var currentBundle: ExtractionBundle? = null

    @OptIn(ExperimentalTime::class)
    override suspend fun resolve(videoId: String, quality: AudioQuality): StreamInfo {
        val hints = ContentHints().withStreamCapabilities(allowHls = false, allowSabr = false, allowBoundedRange = true)
        val stream = requireNotNull(
            bundle().extractor.extract(
                videoId = videoId,
                hints = hints,
                excludedClients = failedClients(videoId),
                audioQuality = when (quality) {
                    AudioQuality.Auto -> XAudioQuality.AUTO
                    AudioQuality.High -> XAudioQuality.HIGH
                    AudioQuality.Low -> XAudioQuality.LOW
                },
                clientPlaybackNonce = generateClientPlaybackNonce(),
            ),
        ) { "innertubex n'a renvoyé aucun flux" }
        check(stream.sabrBootstrap == null) { "Flux SABR non pris en charge" }
        val chunked = stream.requireBoundedRange || stream.useRangeChunks
        return StreamInfo(
            url = stream.audioUrl,
            headers = stream.headers,
            expiresAtMs = stream.expiresAt?.toEpochMilliseconds() ?: (System.currentTimeMillis() + DEFAULT_TTL_MS),
            source = name,
            client = stream.clientName,
            chunkBytes = if (chunked) stream.rangeChunkSizeBytes else 0L,
            loudnessDb = stream.loudnessDb,
            contentLength = stream.contentLengthBytes,
        )
    }

    override suspend fun onRejected(videoId: String, stream: StreamInfo) {
        failures.compute(videoId) { _, old -> FailedClients(old?.names.orEmpty() + stream.client, System.currentTimeMillis()) }
        // Un refus peut venir d'une signature mal calculée : si la table de déchiffrement
        // a changé entre-temps, tous les clients redeviennent utilisables.
        if (bundle().cipherService.refreshAfterStreamRejection()) failures.clear()
    }

    private fun failedClients(videoId: String): Set<String> {
        val failed = failures[videoId] ?: return emptySet()
        if (System.currentTimeMillis() - failed.atMs !in 0 until CLIENT_FAILURE_TTL_MS) {
            failures.remove(videoId, failed)
            return emptySet()
        }
        return failed.names
    }

    private suspend fun bundle(): ExtractionBundle {
        val transport = YouTube.extractionTransport()
        currentBundle?.takeIf { it.generation == transport.generation }?.let { return it }
        return bundleMutex.withLock {
            val latest = YouTube.extractionTransport()
            currentBundle?.takeIf { it.generation == latest.generation }?.let { return@withLock it }
            try {
                currentBundle?.cipherService?.dispose()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Fermeture de l'ancien déchiffreur impossible", e)
            }
            val remoteStore = RemotePlayerConfigStore(latest.httpClient, configRepository, logger)
            val cipherService = YouTubeCipherService(latest.httpClient, remoteStore, logger)
            val extractor = InnerTubeExtractor(
                configParser = YtConfigParserImpl(latest.httpClient, latest.innerTube, remoteStore, logger).withEmbeddedFallback(),
                cipherService = cipherService,
                innerTube = latest.innerTube,
                tokenProvider = tokenProvider,
                logger = logger,
            )
            ExtractionBundle(latest.generation, cipherService, extractor).also { currentBundle = it }
        }
    }

    private val configRepository: PlayerConfigRepository by lazy {
        val prefs = appContext.getSharedPreferences("innertubex_player_config", Context.MODE_PRIVATE)
        object : PlayerConfigRepository {
            override val enabled = true
            override val sourceUrl = PLAYER_CONFIG_URL
            override val defaultSourceUrl = PLAYER_CONFIG_URL
            override var cachedJson: String
                get() = prefs.getString("json", "").orEmpty()
                set(value) = prefs.edit().putString("json", value).apply()
            override var cachedAtMs: Long
                get() = prefs.getLong("cached_at_ms", 0L)
                set(value) = prefs.edit().putLong("cached_at_ms", value).apply()
            override var cachedSourceUrl: String
                get() = prefs.getString("source_url", "").orEmpty()
                set(value) = prefs.edit().putString("source_url", value).apply()
            override var cachedEtag: String
                get() = prefs.getString("etag", "").orEmpty()
                set(value) = prefs.edit().putString("etag", value).apply()
        }
    }

    private val poTokenGenerator by lazy { PoTokenGenerator(appContext) }

    private val tokenProvider = object : TokenProvider {
        override val capabilities = TokenProviderCapabilities(
            providers = setOf(PoTokenProviderKind.WEB_BOTGUARD),
            usesWebView = true,
        )

        override suspend fun getPoToken(videoId: String, visitorData: String, cookie: String?): PoTokenResult? =
            poTokenGenerator.getWebClientPoToken(videoId, visitorData)?.let { token ->
                PoTokenResult(
                    playerRequestToken = token.playerRequestPoToken,
                    streamingDataToken = token.streamingDataPoToken,
                    visitorData = visitorData,
                )
            }

        override suspend fun close() = poTokenGenerator.close()
    }

    private val logger = InnerTubeLogger { event ->
        if (event.level == InnerTubeLogLevel.WARN || event.level == InnerTubeLogLevel.ERROR) {
            Log.w(event.tag, event.message + event.details.entries.joinToString(prefix = " [", postfix = "]") { "${it.key}=${it.value}" })
        }
    }

    private fun YtConfigParser.withEmbeddedFallback(): YtConfigParser = object : YtConfigParser by this {
        override suspend fun fetchConfig(videoId: String, useLoginCookies: Boolean) =
            try {
                this@withEmbeddedFallback.fetchConfig(videoId, useLoginCookies)
            } catch (_: IllegalStateException) {
                this@withEmbeddedFallback.fetchEmbeddedConfig(videoId, useLoginCookies = false)
            }
    }

    private class ExtractionBundle(val generation: Long, val cipherService: YouTubeCipherService, val extractor: InnerTubeExtractor)

    private class FailedClients(val names: Set<String>, val atMs: Long)
}
