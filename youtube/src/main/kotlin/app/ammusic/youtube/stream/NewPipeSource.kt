package app.ammusic.youtube.stream

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.stream.StreamInfo as NewPipeStreamInfo

private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"
private val ExpireParam = Regex("[?&]expire=(\\d+)")

/** Méthode de secours : NewPipeExtractor, utilisée quand innertubex ne trouve aucun flux. */
class NewPipeSource(private val client: OkHttpClient = OkHttpClient()) : StreamSource {
    override val name = "newpipe"

    private val ready by lazy { NewPipe.init(OkDownloader(client)) }

    override suspend fun resolve(videoId: String, quality: AudioQuality): StreamInfo = withContext(Dispatchers.IO) {
        ready
        val info = NewPipeStreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$videoId")
        val candidates = info.audioStreams.filter { it.isUrl }
        val best = when (quality) {
            AudioQuality.Low -> candidates.minByOrNull { it.averageBitrate }
            else -> candidates.maxByOrNull { it.averageBitrate }
        } ?: error("NewPipe n'a trouvé aucun flux audio")
        val expire = ExpireParam.find(best.content)?.groupValues?.get(1)?.toLongOrNull()
        StreamInfo(
            url = best.content,
            headers = mapOf("User-Agent" to USER_AGENT),
            expiresAtMs = expire?.times(1000L) ?: (System.currentTimeMillis() + 5 * 60 * 1000L),
            source = name,
            client = "newpipe",
        )
    }

    private class OkDownloader(private val client: OkHttpClient) : Downloader() {
        override fun execute(request: Request): Response {
            val builder = okhttp3.Request.Builder()
                .method(request.httpMethod(), request.dataToSend()?.toRequestBody())
                .url(request.url())
                .header("User-Agent", USER_AGENT)
            request.headers().forEach { (name, values) ->
                builder.removeHeader(name)
                values.forEach { builder.addHeader(name, it) }
            }
            client.newCall(builder.build()).execute().use { response ->
                if (response.code == 429) throw ReCaptchaException("reCaptcha demandé", request.url())
                return Response(
                    response.code,
                    response.message,
                    response.headers.toMultimap(),
                    response.body.string(),
                    response.request.url.toString(),
                )
            }
        }
    }
}
