package app.ammusic.ui

import kotlinx.serialization.Serializable

@Serializable data object HomeRoute
@Serializable data object SearchRoute
@Serializable data object LibraryRoute
@Serializable data object SettingsRoute

@Serializable
data class CollectionRoute(
    val album: Boolean,
    val id: String,
    val title: String,
    val subtitle: String,
    val thumbnail: String?,
    val sharedKey: String,
    val coverSize: Int,
)

@Serializable
data class ArtistRoute(val id: String, val name: String, val thumbnail: String?, val sharedKey: String, val coverSize: Int)

@Serializable
data class BrowseRoute(val browseId: String, val params: String?, val title: String, val artist: Boolean)

@Serializable data object LoginRoute

@Serializable data class LibraryListRoute(val kind: String)
@Serializable data object RecapRoute
@Serializable data object EqualizerRoute
