package app.ammusic.data

import app.ammusic.youtube.YouTubeMusic
import app.ammusic.youtube.model.YtAlbumPage
import app.ammusic.youtube.model.YtArtistPage
import app.ammusic.youtube.model.YtBrowsePage
import app.ammusic.youtube.model.YtMood
import app.ammusic.youtube.model.YtPlaylistPage
import app.ammusic.youtube.model.YtSearchFilter
import app.ammusic.youtube.model.YtSearchPage
import app.ammusic.youtube.model.YtSection
import app.ammusic.youtube.model.YtTrack
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicRepository @Inject constructor() {
    private var moods: List<YtMood>? = null

    suspend fun home(): Result<List<YtSection>> = YouTubeMusic.home()

    suspend fun moods(): Result<List<YtMood>> =
        moods?.let { Result.success(it) } ?: YouTubeMusic.moods().onSuccess { moods = it }

    suspend fun suggestions(query: String): Result<List<String>> = YouTubeMusic.suggestions(query)

    suspend fun search(query: String): Result<List<YtSection>> = YouTubeMusic.search(query)

    suspend fun search(query: String, filter: YtSearchFilter): Result<YtSearchPage> = YouTubeMusic.search(query, filter)

    suspend fun searchMore(continuation: String): Result<YtSearchPage> = YouTubeMusic.searchMore(continuation)

    suspend fun radio(videoId: String): Result<List<YtTrack>> = YouTubeMusic.radio(videoId)

    suspend fun album(browseId: String): Result<YtAlbumPage> = YouTubeMusic.album(browseId)

    suspend fun playlist(playlistId: String): Result<YtPlaylistPage> = YouTubeMusic.playlist(playlistId)

    suspend fun playlistMore(continuation: String): Result<Pair<List<YtTrack>, String?>> = YouTubeMusic.playlistMore(continuation)

    suspend fun artist(browseId: String): Result<YtArtistPage> = YouTubeMusic.artist(browseId)

    suspend fun artistItems(browseId: String, params: String?): Result<YtBrowsePage> = YouTubeMusic.artistItems(browseId, params)

    suspend fun mood(browseId: String, params: String?): Result<YtBrowsePage> = YouTubeMusic.mood(browseId, params)
}
