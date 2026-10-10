package app.ammusic.youtube.model

/** Ce que YouTube Music peut renvoyer dans une section d'accueil ou de recherche. */
sealed interface YtItem {
    val id: String
    val title: String
    val subtitle: String
    val thumbnail: String?

    /** Clé unique pour les listes (un titre et un album peuvent partager le même identifiant). */
    val key: String
}

data class YtTrack(
    override val id: String,
    override val title: String,
    val artists: String,
    val album: String? = null,
    override val thumbnail: String? = null,
    val durationSeconds: Int? = null,
    val artistId: String? = null,
    val albumId: String? = null,
    /** Identifiant du titre dans sa playlist : YouTube le demande pour l'en retirer. */
    val setVideoId: String? = null,
) : YtItem {
    override val subtitle get() = artists
    override val key get() = "t:$id"
}

data class YtAlbum(
    override val id: String,
    val playlistId: String,
    override val title: String,
    override val subtitle: String,
    override val thumbnail: String?,
    val artistId: String? = null,
    val year: Int? = null,
) : YtItem {
    override val key get() = "a:$id"
}

data class YtPlaylist(
    override val id: String,
    override val title: String,
    override val subtitle: String,
    override val thumbnail: String?,
    /** Vrai pour une playlist du compte connecté, que l'on peut renommer, supprimer et modifier. */
    val editable: Boolean = false,
) : YtItem {
    override val key get() = "p:$id"
}

data class YtArtist(
    override val id: String,
    override val title: String,
    override val thumbnail: String?,
) : YtItem {
    override val subtitle get() = "Artiste"
    override val key get() = "r:$id"
}

/** [moreId] et [moreParams] mènent à la liste complète de la section (« Tout voir »), quand YouTube en propose une. */
data class YtSection(val title: String, val items: List<YtItem>, val moreId: String? = null, val moreParams: String? = null)

data class YtAlbumPage(val album: YtAlbum, val tracks: List<YtTrack>, val otherVersions: List<YtAlbum>)

/** [continuation] sert à demander la suite des titres avec `playlistMore`. */
data class YtPlaylistPage(val playlist: YtPlaylist, val description: String?, val tracks: List<YtTrack>, val continuation: String?)

/** [listeners] est une phrase déjà rédigée par YouTube (« 2,4 M d'auditeurs mensuels »). */
data class YtArtistPage(val artist: YtArtist, val listeners: String?, val description: String?, val sections: List<YtSection>)

/** Page faite de sections : une humeur, un genre, ou la liste complète d'une section d'artiste. */
data class YtBrowsePage(val title: String, val sections: List<YtSection>)

/** Ce qu'il faut pour agir au nom d'un compte : les cookies de music.youtube.com et deux identifiants de la page. */
data class YtSession(val cookie: String, val visitorData: String?, val dataSyncId: String?)

data class YtAccount(val name: String, val email: String?, val avatar: String?)

enum class YtSearchFilter { Songs, Albums, Artists, Playlists, Videos }

data class YtSearchPage(val items: List<YtItem>, val continuation: String?)

/** Humeur ou genre. [color] est la couleur ARGB que YouTube associe à la tuile. */
data class YtMood(val title: String, val color: Long, val browseId: String, val params: String?)

private val SizeSuffix = Regex("=w\\d+-h\\d+.*$")
private val SquareSuffix = Regex("=s\\d+.*$")

/** Demande la vignette à la taille voulue (en pixels) quand l'URL le permet. */
fun String.thumbnailAt(size: Int): String = when {
    SizeSuffix.containsMatchIn(this) -> replace(SizeSuffix, "=w$size-h$size-l90-rj")
    SquareSuffix.containsMatchIn(this) -> replace(SquareSuffix, "=s$size")
    else -> this
}
