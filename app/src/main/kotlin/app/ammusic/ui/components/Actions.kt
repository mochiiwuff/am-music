package app.ammusic.ui.components

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import app.ammusic.youtube.model.YtItem
import app.ammusic.youtube.model.YtMood
import app.ammusic.youtube.model.YtTrack

/**
 * Ce que n'importe quel élément de l'interface peut déclencher, sans faire descendre
 * une dizaine de fonctions d'écran en écran. Fourni par `AppRoot`.
 */
@Immutable
class AppActions(
    /** Ouvre la page d'un album, d'une playlist ou d'un artiste ; lance le titre (et sa radio) si c'en est un. */
    val open: (item: YtItem, origin: String, coverSize: Int) -> Unit,
    /** Lance une liste de titres à partir de `index` ; `shuffle` : voir `PlayerController.playAll`. */
    val playAll: (tracks: List<YtTrack>, index: Int, shuffle: Boolean?) -> Unit,
    /** Ouvre le menu « ⋯ » d'un titre. */
    val menu: (YtTrack) -> Unit,
    val openMood: (YtMood) -> Unit,
    /** Ouvre la liste complète d'une section d'artiste. */
    val openMore: (title: String, browseId: String, params: String?) -> Unit,
    val openArtist: (id: String, name: String) -> Unit,
    val back: () -> Unit,
)

val LocalActions = staticCompositionLocalOf<AppActions> { error("AppActions manquant") }

/** Titre en cours de lecture, pour le signaler dans les listes. */
@Immutable
data class NowPlaying(val id: String? = null, val playing: Boolean = false)

val LocalNowPlaying = compositionLocalOf { NowPlaying() }

/** Clé qui relie la pochette d'un élément de liste à celle de la page qu'il ouvre. [origin] distingue les listes d'un même écran. */
fun sharedKey(origin: String, item: YtItem): String = "$origin/${item.key}"
