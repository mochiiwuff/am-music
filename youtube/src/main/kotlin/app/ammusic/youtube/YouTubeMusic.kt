package app.ammusic.youtube

import android.content.Context
import app.ammusic.youtube.model.YtAlbum
import app.ammusic.youtube.model.YtArtist
import app.ammusic.youtube.model.YtItem
import app.ammusic.youtube.model.YtMood
import app.ammusic.youtube.model.YtPlaylist
import app.ammusic.youtube.model.YtSection
import app.ammusic.youtube.model.YtTrack
import app.ammusic.youtube.stream.InnerTubeXSource
import app.ammusic.youtube.stream.NewPipeSource
import app.ammusic.youtube.stream.StreamResolver
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.innertube.models.YTItem
import com.metrolist.innertubex.models.YouTubeLocale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Seule porte d'entrée de l'appli vers YouTube Music.
 * Le reste du module (`com.metrolist.*`) est repris de Metrolist et ne doit pas être appelé d'ailleurs.
 */
object YouTubeMusic {
    lateinit var streams: StreamResolver
        private set

    fun init(context: Context, country: String = "FR", language: String = "fr") {
        YouTube.locale = YouTubeLocale(gl = country, hl = language)
        streams = StreamResolver(listOf(InnerTubeXSource(context), NewPipeSource()))
    }

    suspend fun home(): Result<List<YtSection>> = io {
        YouTube.home().map { page ->
            page.sections.mapNotNull { section -> section(section.title, section.items) }
        }
    }

    suspend fun moods(): Result<List<YtMood>> = io {
        YouTube.moodAndGenres().map { groups ->
            groups.flatMap { group ->
                group.items.map { YtMood(it.title, it.stripeColor, it.endpoint.browseId, it.endpoint.params) }
            }.distinctBy { it.title }
        }
    }

    suspend fun suggestions(query: String): Result<List<String>> = io {
        YouTube.searchSuggestions(query).map { it.queries }
    }

    /** Résultats groupés comme sur YouTube Music : meilleur résultat, titres, albums, artistes… */
    suspend fun search(query: String): Result<List<YtSection>> = io {
        YouTube.searchSummary(query).map { page ->
            page.summaries.mapNotNull { section(SearchTitles[it.title] ?: it.title, it.items) }
        }
    }

    /** Le code repris de Metrolist nomme ces sections en anglais, quelle que soit la langue demandée. */
    private val SearchTitles = mapOf(
        "Top result" to "Meilleur résultat",
        "Songs" to "Titres",
        "Videos" to "Clips",
        "Albums" to "Albums",
        "Artists" to "Artistes",
        "Playlists" to "Playlists",
        "Community playlists" to "Playlists de la communauté",
        "Featured playlists" to "Playlists à la une",
        "Other" to "Autres",
    )

    /** File « radio » de YouTube Music à partir d'un titre (le titre de départ est en tête). */
    suspend fun radio(videoId: String): Result<List<YtTrack>> = io {
        YouTube.next(WatchEndpoint(videoId = videoId, playlistId = "RDAMVM$videoId")).map { result ->
            result.items.map { it.toTrack() }
        }
    }

    private fun section(title: String, items: List<YTItem>): YtSection? =
        YtSection(title, items.mapNotNull { it.toItem() }.distinctBy { it.key }).takeIf { it.items.isNotEmpty() }

    private fun SongItem.toTrack() = YtTrack(
        id = id,
        title = title,
        artists = artists.joinToString(", ") { it.name },
        album = album?.name,
        thumbnail = thumbnail,
        durationSeconds = duration,
    )

    /** Podcasts et épisodes sont ignorés : l'appli ne gère que la musique. */
    private fun YTItem.toItem(): YtItem? = when (this) {
        is SongItem -> toTrack()
        is AlbumItem -> YtAlbum(browseId, playlistId, title, artists?.joinToString(", ") { it.name }.orEmpty(), thumbnail)
        is PlaylistItem -> YtPlaylist(id, title, author?.name ?: songCountText.orEmpty(), thumbnail)
        is ArtistItem -> YtArtist(id, title, thumbnail)
        else -> null
    }

    private suspend fun <T> io(block: suspend () -> Result<T>): Result<T> = withContext(Dispatchers.IO) { block() }
}
