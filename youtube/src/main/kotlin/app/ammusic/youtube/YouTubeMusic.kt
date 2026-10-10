package app.ammusic.youtube

import android.content.Context
import app.ammusic.youtube.model.YtAccount
import app.ammusic.youtube.model.YtAlbum
import app.ammusic.youtube.model.YtAlbumPage
import app.ammusic.youtube.model.YtArtist
import app.ammusic.youtube.model.YtArtistPage
import app.ammusic.youtube.model.YtBrowsePage
import app.ammusic.youtube.model.YtItem
import app.ammusic.youtube.model.YtMood
import app.ammusic.youtube.model.YtPlaylist
import app.ammusic.youtube.model.YtPlaylistPage
import app.ammusic.youtube.model.YtSearchFilter
import app.ammusic.youtube.model.YtSearchPage
import app.ammusic.youtube.model.YtSection
import app.ammusic.youtube.model.YtSession
import app.ammusic.youtube.model.YtTrack
import app.ammusic.youtube.stream.InnerTubeXSource
import app.ammusic.youtube.stream.NewPipeSource
import app.ammusic.youtube.stream.StreamResolver
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.BrowseEndpoint
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.innertube.models.YTItem
import com.metrolist.innertube.utils.completed
import com.metrolist.innertube.utils.parseCookieString
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
        guestVisitorData = YouTube.visitorData
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

    /** Résultats d'un seul type, page par page ([searchMore] donne la suite). */
    suspend fun search(query: String, filter: YtSearchFilter): Result<YtSearchPage> = io {
        val value = when (filter) {
            YtSearchFilter.Songs -> YouTube.SearchFilter.FILTER_SONG
            YtSearchFilter.Albums -> YouTube.SearchFilter.FILTER_ALBUM
            YtSearchFilter.Artists -> YouTube.SearchFilter.FILTER_ARTIST
            YtSearchFilter.Playlists -> YouTube.SearchFilter.FILTER_COMMUNITY_PLAYLIST
            YtSearchFilter.Videos -> YouTube.SearchFilter.FILTER_VIDEO
        }
        YouTube.search(query, value).map { YtSearchPage(it.items.toItems(), it.continuation) }
    }

    suspend fun searchMore(continuation: String): Result<YtSearchPage> = io {
        YouTube.searchContinuation(continuation).map { YtSearchPage(it.items.toItems(), it.continuation) }
    }

    suspend fun album(browseId: String): Result<YtAlbumPage> = io {
        YouTube.album(browseId).map { page ->
            YtAlbumPage(page.album.toAlbum(), page.songs.map { it.toTrack() }, page.otherVersions.map { it.toAlbum() })
        }
    }

    suspend fun playlist(playlistId: String): Result<YtPlaylistPage> = io {
        YouTube.playlist(playlistId).map { page ->
            YtPlaylistPage(
                playlist = page.playlist.toPlaylist(),
                description = page.playlist.description?.takeIf { it.isNotBlank() },
                tracks = page.songs.map { it.toTrack() }.distinctBy { it.id },
                continuation = page.songsContinuation ?: page.continuation,
            )
        }
    }

    /** Suite des titres d'une playlist : les titres, et le jeton de la page suivante s'il en reste. */
    suspend fun playlistMore(continuation: String): Result<Pair<List<YtTrack>, String?>> = io {
        YouTube.playlistContinuation(continuation).map { page -> page.songs.map { it.toTrack() } to page.continuation }
    }

    suspend fun artist(browseId: String): Result<YtArtistPage> = io {
        YouTube.artist(browseId).map { page ->
            YtArtistPage(
                artist = YtArtist(page.artist.id, page.artist.title, page.artist.thumbnail),
                listeners = page.monthlyListenerCount ?: page.subscriberCountText,
                description = page.description?.takeIf { it.isNotBlank() },
                sections = page.sections.mapNotNull { section ->
                    section(section.title, section.items)?.copy(
                        moreId = section.moreEndpoint?.browseId,
                        moreParams = section.moreEndpoint?.params,
                    )
                },
            )
        }
    }

    /** Liste complète d'une section d'artiste (tous ses albums, tous ses titres…). */
    suspend fun artistItems(browseId: String, params: String?): Result<YtBrowsePage> = io {
        YouTube.artistItems(BrowseEndpoint(browseId, params)).map { page ->
            YtBrowsePage(page.title, listOfNotNull(section(page.title, page.items)))
        }
    }

    /** Contenu d'une humeur ou d'un genre : des sections de playlists et d'albums. */
    suspend fun mood(browseId: String, params: String?): Result<YtBrowsePage> = io {
        YouTube.browse(browseId, params).map { page ->
            YtBrowsePage(page.title.orEmpty(), page.items.mapNotNull { section(it.title.orEmpty(), it.items) })
        }
    }

    // ----- Compte -----

    val isSignedIn: Boolean get() = YouTube.cookie?.let { "SAPISID" in parseCookieString(it) } == true

    /** Passe en mode connecté avec [session], ou revient en mode invité avec `null`. */
    fun signIn(session: YtSession?) {
        YouTube.cookie = session?.cookie
        YouTube.dataSyncId = session?.dataSyncId?.let { id ->
            // La page donne « compte||canal » : on garde la partie qui désigne le canal utilisé.
            id.takeIf { "||" !in it } ?: id.takeIf { it.endsWith("||") }?.substringBefore("||") ?: id.substringAfter("||")
        }
        YouTube.visitorData = session?.visitorData ?: guestVisitorData
        YouTube.useLoginForBrowse = session != null
    }

    suspend fun account(): Result<YtAccount> = io {
        YouTube.accountInfo().map { YtAccount(it.name, it.email, it.thumbnailUrl) }
    }

    suspend fun likedTracks(): Result<List<YtTrack>> = io {
        YouTube.playlist("LM").completed().map { page -> page.songs.map { it.toTrack() }.distinctBy { it.id } }
    }

    /** Playlists de la bibliothèque du compte : les siennes ([YtPlaylist.editable]) et celles enregistrées. */
    suspend fun libraryPlaylists(): Result<List<YtPlaylist>> = io {
        YouTube.library("FEmusic_liked_playlists").completed().map { page ->
            page.items.filterIsInstance<PlaylistItem>().filterNot { it.id in AutoPlaylists }.map { it.toPlaylist() }
        }
    }

    suspend fun libraryAlbums(): Result<List<YtAlbum>> = io {
        YouTube.library("FEmusic_liked_albums").completed().map { page -> page.items.filterIsInstance<AlbumItem>().map { it.toAlbum() } }
    }

    suspend fun libraryArtists(): Result<List<YtArtist>> = io {
        YouTube.library("FEmusic_library_corpus_artists").completed().map { page ->
            page.items.filterIsInstance<ArtistItem>().map { YtArtist(it.channelId ?: it.id.removePrefix("MPLA"), it.title, it.thumbnail) }
        }
    }

    suspend fun like(videoId: String, like: Boolean): Result<Unit> = io { YouTube.likeVideo(videoId, like).map { } }

    /** Ajoute ou retire de la bibliothèque un album (par son `playlistId`) ou une playlist. */
    suspend fun save(playlistId: String, save: Boolean): Result<Unit> = io { YouTube.likePlaylist(playlistId, save).map { } }

    suspend fun follow(channelId: String, follow: Boolean): Result<Unit> = io { YouTube.subscribeChannel(channelId, follow).map { } }

    suspend fun createPlaylist(title: String): Result<String> = io { runCatching { YouTube.createPlaylist(title) } }

    suspend fun renamePlaylist(playlistId: String, title: String): Result<Unit> = io { YouTube.renamePlaylist(playlistId, title).map { } }

    suspend fun deletePlaylist(playlistId: String): Result<Unit> = io { YouTube.deletePlaylist(playlistId).map { } }

    suspend fun addToPlaylist(playlistId: String, videoId: String): Result<Unit> = io { YouTube.addToPlaylist(playlistId, videoId).map { } }

    suspend fun removeFromPlaylist(playlistId: String, videoId: String, setVideoId: String): Result<Unit> = io {
        YouTube.removeFromPlaylist(playlistId, videoId, setVideoId).map { }
    }

    /** « Titres aimés » et « Épisodes pour plus tard » : listes automatiques, gérées à part. */
    private val AutoPlaylists = setOf("LM", "SE")

    private var guestVisitorData: String? = null

    private fun section(title: String, items: List<YTItem>): YtSection? =
        YtSection(title, items.toItems()).takeIf { it.items.isNotEmpty() }

    private fun List<YTItem>.toItems(): List<YtItem> = mapNotNull { it.toItem() }.distinctBy { it.key }

    private fun SongItem.toTrack() = YtTrack(
        id = id,
        title = title,
        artists = artists.joinToString(", ") { it.name },
        album = album?.name,
        thumbnail = thumbnail,
        durationSeconds = duration,
        artistId = artists.firstNotNullOfOrNull { it.id },
        albumId = album?.id,
        setVideoId = setVideoId,
    )

    private fun AlbumItem.toAlbum() = YtAlbum(
        id = browseId,
        playlistId = playlistId,
        title = title,
        subtitle = artists?.joinToString(", ") { it.name }.orEmpty(),
        thumbnail = thumbnail,
        artistId = artists?.firstNotNullOfOrNull { it.id },
        year = year,
    )

    private fun PlaylistItem.toPlaylist() = YtPlaylist(id, title, author?.name ?: songCountText.orEmpty(), thumbnail, isEditable)

    /** Podcasts et épisodes sont ignorés : l'appli ne gère que la musique. */
    private fun YTItem.toItem(): YtItem? = when (this) {
        is SongItem -> toTrack()
        is AlbumItem -> toAlbum()
        is PlaylistItem -> toPlaylist()
        is ArtistItem -> YtArtist(id, title, thumbnail)
        else -> null
    }

    private suspend fun <T> io(block: suspend () -> Result<T>): Result<T> = withContext(Dispatchers.IO) { block() }
}
