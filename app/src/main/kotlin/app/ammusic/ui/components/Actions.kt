package app.ammusic.ui.components

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Rect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import app.ammusic.youtube.model.YtAccount
import app.ammusic.youtube.model.YtItem
import app.ammusic.youtube.model.YtMood
import app.ammusic.youtube.model.YtTrack

@Immutable
class AppActions(
    val open: (item: YtItem, origin: String, coverSize: Int) -> Unit,
    val playAll: (tracks: List<YtTrack>, index: Int, shuffle: Boolean?) -> Unit,
    val toggle: () -> Unit,
    val menu: (YtTrack, List<MenuAction>) -> Unit,
    val preview: (YtTrack, List<MenuAction>, Rect?) -> Unit,
    val sheet: (MenuRequest) -> Unit,
    val prompt: (title: String, initial: String, thumbnail: String?, onConfirm: (String) -> Unit) -> Unit,
    val openLibrary: (kind: String) -> Unit,
    val openLogin: () -> Unit,
    val openMood: (YtMood) -> Unit,
    val openMore: (title: String, browseId: String, params: String?) -> Unit,
    val openArtist: (id: String, name: String) -> Unit,
    val back: () -> Unit,
)

val LocalActions = staticCompositionLocalOf<AppActions> { error("AppActions manquant") }

@Immutable
data class NowPlaying(val id: String? = null, val playing: Boolean = false)

val LocalNowPlaying = compositionLocalOf { NowPlaying() }

fun sharedKey(origin: String, item: YtItem): String = "$origin/${item.key}"

val LocalAccount = compositionLocalOf<YtAccount?> { null }
