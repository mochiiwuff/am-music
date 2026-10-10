package app.ammusic.youtube.model

sealed interface YtItem {
    val id: String
    val title: String
    val subtitle: String
    val thumbnail: String?

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

data class YtSection(val title: String, val items: List<YtItem>, val moreId: String? = null, val moreParams: String? = null)

data class YtAlbumPage(val album: YtAlbum, val tracks: List<YtTrack>, val otherVersions: List<YtAlbum>)

data class YtPlaylistPage(val playlist: YtPlaylist, val description: String?, val tracks: List<YtTrack>, val continuation: String?)

data class YtArtistPage(val artist: YtArtist, val listeners: String?, val description: String?, val sections: List<YtSection>)

data class YtBrowsePage(val title: String, val sections: List<YtSection>)

data class YtSession(val cookie: String, val visitorData: String?, val dataSyncId: String?)

data class YtAccount(val name: String, val email: String?, val avatar: String?)

enum class YtSearchFilter { Songs, Albums, Artists, Playlists, Videos }

data class YtSearchPage(val items: List<YtItem>, val continuation: String?)

data class YtMood(val title: String, val color: Long, val browseId: String, val params: String?)

private val SizeSuffix = Regex("=w\\d+-h\\d+.*$")
private val SquareSuffix = Regex("=s\\d+.*$")

private val VideoThumbnail = Regex("^https://i\\.ytimg\\.com/vi(?:_webp)?/([^/]+)/.*$")

fun String.thumbnailAt(size: Int): String = when {
    SizeSuffix.containsMatchIn(this) -> replace(SizeSuffix, "=w$size-h$size-l90-rj")
    SquareSuffix.containsMatchIn(this) -> replace(SquareSuffix, "=s$size")
    size >= 720 -> VideoThumbnail.matchEntire(this)?.let { "https://i.ytimg.com/vi/${it.groupValues[1]}/hq720.jpg" } ?: this
    else -> this
}
