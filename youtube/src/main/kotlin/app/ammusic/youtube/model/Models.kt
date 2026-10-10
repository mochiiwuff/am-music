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
) : YtItem {
    override val key get() = "a:$id"
}

data class YtPlaylist(
    override val id: String,
    override val title: String,
    override val subtitle: String,
    override val thumbnail: String?,
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

data class YtSection(val title: String, val items: List<YtItem>)

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
