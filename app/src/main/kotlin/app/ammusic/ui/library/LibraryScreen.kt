package app.ammusic.ui.library

import app.ammusic.youtube.tr

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ammusic.ui.LibraryKind
import app.ammusic.ui.LibraryViewModel
import app.ammusic.ui.components.AmIcon
import app.ammusic.ui.components.AmIcons
import app.ammusic.ui.components.AmText
import app.ammusic.ui.components.Cover
import app.ammusic.ui.components.EmptyState
import app.ammusic.ui.components.IconButton
import app.ammusic.ui.components.ItemRow
import app.ammusic.ui.components.ItemTile
import app.ammusic.ui.components.LargeTitleScreen
import app.ammusic.ui.components.ListCoverSize
import app.ammusic.ui.components.LocalActions
import app.ammusic.ui.components.SectionHeader
import app.ammusic.ui.components.TileCoverSize
import app.ammusic.ui.components.cascade
import app.ammusic.ui.components.hairlineBelow
import app.ammusic.ui.components.listRow
import app.ammusic.ui.components.pressable
import app.ammusic.ui.components.sharedKey
import app.ammusic.ui.detail.PillButton
import app.ammusic.ui.home.ProfileButton
import app.ammusic.ui.theme.AmTheme
import app.ammusic.youtube.model.YtItem
import app.ammusic.youtube.model.YtPlaylist
import app.ammusic.youtube.model.YtTrack
import kotlinx.coroutines.delay

private class Shelf(val kind: String, val label: String, val icon: ImageVector)

private val Shelves get() = listOf(
    Shelf(LibraryKind.Playlists, "Playlists", AmIcons.Playlist),
    Shelf(LibraryKind.Artists, tr("Artistes", "Artists"), AmIcons.Artist),
    Shelf(LibraryKind.Albums, "Albums", AmIcons.Album),
    Shelf(LibraryKind.Liked, tr("Titres aimés", "Liked songs"), AmIcons.Heart),
    Shelf(LibraryKind.History, tr("Historique", "History"), AmIcons.Clock),
)

@Composable
fun LibraryScreen(onProfile: () -> Unit, viewModel: LibraryViewModel = hiltViewModel()) {
    val account by viewModel.account.collectAsStateWithLifecycle()
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val actions = LocalActions.current
    LaunchedEffect(account) { viewModel.refresh() }

    LargeTitleScreen(title = tr("Bibliothèque", "Library"), trailing = { ProfileButton(onProfile) }) {
        itemsIndexed(Shelves, key = { _, shelf -> shelf.kind }, contentType = { _, _ -> "shelf" }) { index, shelf ->
            ShelfRow(shelf, divided = index < Shelves.lastIndex) { actions.openLibrary(shelf.kind) }
        }
        if (account == null) {
            item(key = "guest", contentType = "guest") { GuestCard(onConnect = actions.openLogin) }
        }
        if (recent.isNotEmpty()) {
            item(key = "recentHeader", contentType = "header") { SectionHeader(tr("Ajouts récents", "Recently added")) }
            tileGrid(recent, "library")
        } else {
            item(key = "empty", contentType = "text") {
                EmptyState(AmIcons.Library, tr("Bibliothèque vide", "Your library is empty"), text = tr("Ajoutez des albums, des playlists ou des artistes.", "Add albums, playlists or artists."))
            }
        }
    }
}

@Composable
private fun ShelfRow(shelf: Shelf, divided: Boolean, onClick: () -> Unit) {
    val colors = AmTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .pressable(scale = 0.98f, label = shelf.label, onClick = onClick)
            .height(50.dp)
            .hairlineBelow(if (divided) colors.hairline else Color.Transparent, 64.dp)
            .padding(start = 20.dp, end = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
            AmIcon(shelf.icon, tint = colors.accent, size = 26.dp)
        }
        AmText(
            text = shelf.label,
            style = AmTheme.type.body.copy(fontSize = 20.sp, lineHeight = 25.sp),
            maxLines = 1,
            modifier = Modifier.weight(1f).padding(start = 14.dp),
        )
        AmIcon(AmIcons.ChevronRight, tint = colors.textTertiary, size = 15.dp)
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
        AmText(tr("Compte YouTube Music", "YouTube Music account"), style = AmTheme.type.headline)
        AmText(
            text = tr("Connectez-vous pour retrouver votre bibliothèque.", "Sign in to get your library."),
            style = AmTheme.type.subhead,
            color = colors.textSecondary,
            modifier = Modifier.padding(top = 6.dp, bottom = 16.dp),
        )
        Box(
            Modifier
                .height(44.dp)
                .pressable(haptic = true, label = tr("Se connecter", "Sign in"), onClick = onConnect)
                .clip(CircleShape)
                .background(colors.accent)
                .padding(horizontal = 22.dp),
            contentAlignment = Alignment.Center,
        ) {
            AmText(tr("Se connecter", "Sign in"), style = AmTheme.type.headline, color = Color.White)
        }
    }
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
    val newPlaylist = {
        actions.prompt(tr("Nouvelle playlist", "New playlist"), "", null) { title ->
            viewModel.createPlaylist(title) { id -> actions.open(YtPlaylist(id, title, "", null, editable = true), origin, ListCoverSize) }
        }
    }
    var settled by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(600)
        settled = true
    }

    LargeTitleScreen(
        title = shelf.label,
        leading = { IconButton(AmIcons.ChevronLeft, label = tr("Retour", "Back"), onClick = actions.back) },
        trailing = {
            when {
                kind == LibraryKind.History && history.isNotEmpty() ->
                    IconButton(AmIcons.Trash, label = tr("Effacer l'historique", "Clear history"), onClick = viewModel::clearHistory, tint = AmTheme.colors.textSecondary)
            }
        },
    ) {
        when (kind) {
            LibraryKind.Liked -> tracks(liked, origin, AmIcons.Heart, tr("Aucun titre aimé", "No liked songs"))
            LibraryKind.History -> tracks(history, origin, AmIcons.Clock, tr("Historique vide", "No history"))
            LibraryKind.Albums -> {
                if (albums.isEmpty()) item(key = "empty") { EmptyState(AmIcons.Album, tr("Aucun album", "No albums")) }
                item(key = "top") { Spacer(Modifier.height(12.dp)) }
                tileGrid(albums, origin)
            }
            LibraryKind.Artists -> {
                if (artists.isEmpty()) item(key = "empty") { EmptyState(AmIcons.Artist, tr("Aucun artiste suivi", "No followed artists")) }
                rows(artists, origin)
            }
            else -> {
                item(key = "new", contentType = "new") { NewPlaylistRow(newPlaylist, Modifier.cascade(0, enabled = !settled)) }
                itemsIndexed(playlists, key = { _, playlist -> playlist.key }, contentType = { _, _ -> "playlist" }) { index, playlist ->
                    PlaylistRow(playlist, origin, Modifier.animateItem().cascade(index + 1, enabled = !settled))
                }
            }
        }
    }
}

private fun LazyListScope.rows(items: List<YtItem>, origin: String) {
    items(items, key = { it.key }, contentType = { "row" }) { item ->
        ItemRow(item, origin, Modifier.fillMaxWidth().animateItem().listRow())
    }
}

private fun LazyListScope.tracks(tracks: List<YtTrack>, origin: String, emptyIcon: ImageVector, emptyTitle: String) {
    if (tracks.isEmpty()) {
        item(key = "empty") { EmptyState(emptyIcon, emptyTitle) }
        return
    }
    item(key = "buttons", contentType = "buttons") {
        val actions = LocalActions.current
        Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PillButton(tr("Lecture", "Play"), AmIcons.Play, filled = true, enabled = true, onClick = { actions.playAll(tracks, 0, false) }, modifier = Modifier.weight(1f))
            PillButton(tr("Aléatoire", "Shuffle"), AmIcons.Shuffle, filled = false, enabled = true, onClick = { actions.playAll(tracks, 0, true) }, modifier = Modifier.weight(1f))
        }
    }
    itemsIndexed(tracks, key = { _, track -> track.id }, contentType = { _, _ -> "track" }) { index, track ->
        val actions = LocalActions.current
        ItemRow(
            item = track,
            origin = origin,
            modifier = Modifier.fillMaxWidth().animateItem().listRow(),
            onClick = { actions.playAll(tracks, index, null) },
        )
    }
}

@Composable
private fun NewPlaylistRow(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AmTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .pressable(scale = 0.98f, haptic = true, label = tr("Nouvelle playlist", "New playlist"), onClick = onClick)
            .hairlineBelow(colors.hairline, 106.dp)
            .padding(start = 20.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(72.dp).clip(RoundedCornerShape(9.dp)).background(colors.surface), contentAlignment = Alignment.Center) {
            AmIcon(AmIcons.Plus, tint = colors.accent, size = 28.dp)
        }
        AmText(tr("Nouvelle playlist…", "New playlist…"), color = colors.accent, modifier = Modifier.padding(start = 14.dp))
    }
}

@Composable
private fun PlaylistRow(playlist: YtPlaylist, origin: String, modifier: Modifier = Modifier) {
    val colors = AmTheme.colors
    val actions = LocalActions.current
    Row(
        modifier
            .fillMaxWidth()
            .pressable(scale = 0.98f) { actions.open(playlist, origin, ListCoverSize) }
            .hairlineBelow(colors.hairline, 106.dp)
            .padding(start = 20.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(
            url = playlist.thumbnail,
            modifier = Modifier.size(72.dp),
            corner = 9.dp,
            requestSize = ListCoverSize,
            sharedKey = sharedKey(origin, playlist),
            glyph = AmIcons.Playlist,
        )
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            AmText(playlist.title, style = AmTheme.type.body.copy(fontWeight = FontWeight.Medium), maxLines = 1)
            if (playlist.subtitle.isNotBlank()) {
                AmText(playlist.subtitle, style = AmTheme.type.footnote, color = colors.textSecondary, maxLines = 1, modifier = Modifier.padding(top = 2.dp))
            }
        }
        AmIcon(AmIcons.ChevronRight, tint = colors.textTertiary, size = 18.dp)
    }
}
