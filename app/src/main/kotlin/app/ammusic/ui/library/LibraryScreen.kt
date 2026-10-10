package app.ammusic.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ammusic.ui.LibraryKind
import app.ammusic.ui.LibraryViewModel
import app.ammusic.ui.components.AmIcon
import app.ammusic.ui.components.AmIcons
import app.ammusic.ui.components.AmText
import app.ammusic.ui.components.IconButton
import app.ammusic.ui.components.ItemRow
import app.ammusic.ui.components.ItemTile
import app.ammusic.ui.components.LargeTitleScreen
import app.ammusic.ui.components.LocalActions
import app.ammusic.ui.components.SectionHeader
import app.ammusic.ui.components.TileCoverSize
import app.ammusic.ui.components.pressable
import app.ammusic.ui.detail.PillButton
import app.ammusic.ui.home.ProfileButton
import app.ammusic.ui.theme.AmTheme
import app.ammusic.youtube.model.YtItem
import app.ammusic.youtube.model.YtTrack

private class Shelf(val kind: String, val label: String, val icon: ImageVector)

private val Shelves = listOf(
    Shelf(LibraryKind.Playlists, "Playlists", AmIcons.Playlist),
    Shelf(LibraryKind.Artists, "Artistes", AmIcons.Artist),
    Shelf(LibraryKind.Albums, "Albums", AmIcons.Album),
    Shelf(LibraryKind.Liked, "Titres aimés", AmIcons.Heart),
    Shelf(LibraryKind.History, "Historique", AmIcons.Clock),
)

@Composable
fun LibraryScreen(onProfile: () -> Unit, viewModel: LibraryViewModel = hiltViewModel()) {
    val account by viewModel.account.collectAsStateWithLifecycle()
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val actions = LocalActions.current
    LaunchedEffect(account) { viewModel.refresh() }

    LargeTitleScreen(title = "Bibliothèque", trailing = { ProfileButton(onProfile) }) {
        items(Shelves, key = { it.kind }, contentType = { "shelf" }) { shelf ->
            ShelfRow(shelf) { actions.openLibrary(shelf.kind) }
        }
        if (account == null) {
            item(key = "guest", contentType = "guest") { GuestCard(onConnect = actions.openLogin) }
        }
        if (recent.isNotEmpty()) {
            item(key = "recentHeader", contentType = "header") { SectionHeader("Ajouts récents") }
            tileGrid(recent, "library")
        } else {
            item(key = "empty", contentType = "text") {
                Hint("Votre bibliothèque est vide. Touchez « + » en haut d'un album, d'une playlist ou d'un artiste pour le retrouver ici.")
            }
        }
    }
}

@Composable
private fun ShelfRow(shelf: Shelf, onClick: () -> Unit) {
    val colors = AmTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .pressable(scale = 0.98f, label = shelf.label, onClick = onClick)
            .height(54.dp)
            .drawBehind {
                val y = size.height - 0.5.dp.toPx()
                drawLine(colors.hairline, Offset(58.dp.toPx(), y), Offset(size.width, y), 1.dp.toPx())
            }
            .padding(start = 20.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AmIcon(shelf.icon, tint = colors.accent, size = 24.dp)
        AmText(shelf.label, style = AmTheme.type.body.copy(fontSize = 19.sp), modifier = Modifier.weight(1f).padding(start = 14.dp))
        AmIcon(AmIcons.ChevronRight, tint = colors.textTertiary, size = 18.dp)
    }
}

@Composable
private fun GuestCard(onConnect: () -> Unit) {
    val colors = AmTheme.colors
    Column(
        Modifier
            .padding(start = 20.dp, end = 20.dp, top = 28.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surface)
            .padding(18.dp),
    ) {
        AmText("Retrouvez votre musique", style = AmTheme.type.headline)
        AmText(
            text = "Connectez votre compte YouTube Music pour retrouver vos titres aimés, vos playlists et des recommandations faites pour vous.",
            style = AmTheme.type.subhead,
            color = colors.textSecondary,
            modifier = Modifier.padding(top = 6.dp, bottom = 16.dp),
        )
        Box(
            Modifier
                .height(44.dp)
                .pressable(haptic = true, label = "Se connecter", onClick = onConnect)
                .clip(CircleShape)
                .background(colors.accent)
                .padding(horizontal = 22.dp),
            contentAlignment = Alignment.Center,
        ) {
            AmText("Se connecter", style = AmTheme.type.headline, color = Color.White)
        }
    }
}

@Composable
private fun Hint(text: String) {
    AmText(text, style = AmTheme.type.subhead, color = AmTheme.colors.textSecondary, modifier = Modifier.padding(horizontal = 20.dp, vertical = 28.dp))
}

private fun LazyListScope.tileGrid(items: List<YtItem>, origin: String) {
    items(items.chunked(2), key = { it.first().key }, contentType = { "tiles" }) { pair ->
        val actions = LocalActions.current
        Row(
            Modifier.fillMaxWidth().animateItem().padding(start = 20.dp, end = 20.dp, bottom = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            pair.forEach { item ->
                ItemTile(item, origin, Modifier.weight(1f)) { actions.open(item, origin, TileCoverSize) }
            }
            if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
fun LibraryListScreen(kind: String, viewModel: LibraryViewModel = hiltViewModel()) {
    val actions = LocalActions.current
    val origin = "library:$kind"
    val shelf = Shelves.first { it.kind == kind }
    val liked by viewModel.liked.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val artists by viewModel.artists.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val newPlaylist = { actions.prompt("Nouvelle playlist", "") { title -> viewModel.createPlaylist(title) } }

    LargeTitleScreen(
        title = shelf.label,
        leading = { IconButton(AmIcons.ChevronLeft, label = "Retour", onClick = actions.back) },
        trailing = {
            when {
                kind == LibraryKind.Playlists -> IconButton(AmIcons.Plus, label = "Nouvelle playlist", onClick = newPlaylist, tint = AmTheme.colors.accent)
                kind == LibraryKind.History && history.isNotEmpty() ->
                    IconButton(AmIcons.Trash, label = "Effacer l'historique", onClick = viewModel::clearHistory, tint = AmTheme.colors.textSecondary)
            }
        },
    ) {
        when (kind) {
            LibraryKind.Liked -> tracks(liked, origin, "Touchez le cœur du lecteur pour garder un titre ici.")
            LibraryKind.History -> tracks(history, origin, "Les titres que vous écoutez apparaîtront ici.")
            LibraryKind.Albums -> {
                if (albums.isEmpty()) item(key = "empty") { Hint("Touchez « + » en haut d'un album pour le garder ici.") }
                item(key = "top") { Spacer(Modifier.height(12.dp)) }
                tileGrid(albums, origin)
            }
            LibraryKind.Artists -> {
                if (artists.isEmpty()) item(key = "empty") { Hint("Touchez « + » en haut de la page d'un artiste pour le suivre.") }
                rows(artists, origin)
            }
            else -> {
                if (playlists.isEmpty()) item(key = "empty") { Hint("Touchez « + » pour créer votre première playlist.") }
                rows(playlists, origin)
            }
        }
    }
}

private fun LazyListScope.rows(items: List<YtItem>, origin: String) {
    items(items, key = { it.key }, contentType = { "row" }) { item ->
        ItemRow(item, origin, Modifier.fillMaxWidth().animateItem().padding(horizontal = 20.dp, vertical = 6.dp))
    }
}

private fun LazyListScope.tracks(tracks: List<YtTrack>, origin: String, emptyHint: String) {
    if (tracks.isEmpty()) {
        item(key = "empty") { Hint(emptyHint) }
        return
    }
    item(key = "buttons", contentType = "buttons") {
        val actions = LocalActions.current
        Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PillButton("Lecture", AmIcons.Play, filled = true, enabled = true, onClick = { actions.playAll(tracks, 0, false) }, modifier = Modifier.weight(1f))
            PillButton("Aléatoire", AmIcons.Shuffle, filled = false, enabled = true, onClick = { actions.playAll(tracks, 0, true) }, modifier = Modifier.weight(1f))
        }
    }
    itemsIndexed(tracks, key = { _, track -> track.id }, contentType = { _, _ -> "track" }) { index, track ->
        val actions = LocalActions.current
        ItemRow(
            item = track,
            origin = origin,
            modifier = Modifier.fillMaxWidth().animateItem().padding(horizontal = 20.dp, vertical = 6.dp),
            onClick = { actions.playAll(tracks, index, null) },
        )
    }
}
