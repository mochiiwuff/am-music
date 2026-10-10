package app.ammusic.data

import app.ammusic.youtube.YouTubeMusic
import app.ammusic.youtube.model.YtMood
import app.ammusic.youtube.model.YtSection
import app.ammusic.youtube.model.YtTrack
import javax.inject.Inject
import javax.inject.Singleton

/** Accès aux contenus YouTube Music pour les écrans. Les humeurs sont gardées en mémoire (elles changent rarement). */
@Singleton
class MusicRepository @Inject constructor() {
    private var moods: List<YtMood>? = null

    suspend fun home(): Result<List<YtSection>> = YouTubeMusic.home()

    suspend fun moods(): Result<List<YtMood>> =
        moods?.let { Result.success(it) } ?: YouTubeMusic.moods().onSuccess { moods = it }

    suspend fun suggestions(query: String): Result<List<String>> = YouTubeMusic.suggestions(query)

    suspend fun search(query: String): Result<List<YtSection>> = YouTubeMusic.search(query)

    suspend fun radio(videoId: String): Result<List<YtTrack>> = YouTubeMusic.radio(videoId)
}
