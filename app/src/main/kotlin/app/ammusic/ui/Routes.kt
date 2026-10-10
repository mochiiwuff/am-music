package app.ammusic.ui

import kotlinx.serialization.Serializable

@Serializable data object HomeRoute
@Serializable data object SearchRoute
@Serializable data object LibraryRoute
@Serializable data object SettingsRoute

/**
 * Page d'un album ([album] vrai) ou d'une playlist. Le titre et la pochette voyagent avec la route
 * pour s'afficher tout de suite. [sharedKey] relie la pochette à celle de l'écran d'où l'on vient
 * (vide : pas de pochette voyageuse) et [coverSize] est la taille d'image que cet écran avait déjà chargée.
 */
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

/** Humeur ou genre, ou liste complète d'une section d'artiste ([artist] vrai). */
@Serializable
data class BrowseRoute(val browseId: String, val params: String?, val title: String, val artist: Boolean)

/** Fenêtre de connexion au compte YouTube Music. */
@Serializable data object LoginRoute

/** Une liste de la bibliothèque. [kind] : voir `LibraryKind`. */
@Serializable data class LibraryListRoute(val kind: String)
