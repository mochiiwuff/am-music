package app.ammusic.ui.detail

import app.ammusic.youtube.tr

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ammusic.ui.CollectionRoute
import app.ammusic.ui.CollectionState
import app.ammusic.ui.CollectionViewModel
import app.ammusic.ui.LoadStatus
import app.ammusic.ui.components.AmIcons
import app.ammusic.ui.components.AmText
import app.ammusic.ui.components.Cover
import app.ammusic.ui.components.ErrorState
import app.ammusic.ui.components.IconButton
import app.ammusic.ui.components.ItemCarousel
import app.ammusic.ui.components.ItemRow
import app.ammusic.ui.components.MenuAction
import app.ammusic.ui.components.MenuRequest
import app.ammusic.ui.components.LocalActions
import app.ammusic.ui.components.LocalNowPlaying
import app.ammusic.ui.components.NowPlayingBars
import app.ammusic.ui.components.RowsSkeleton
import app.ammusic.ui.components.SectionHeader
import app.ammusic.ui.components.cascade
import app.ammusic.ui.components.pressable
import app.ammusic.ui.components.sharedCover
import app.ammusic.ui.player.rememberPlayerColors
import app.ammusic.ui.theme.AmTheme
import app.ammusic.youtube.model.YtTrack
import kotlinx.coroutines.delay

@Composable
fun CollectionScreen(viewModel: CollectionViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val route = viewModel.route
    val actions = LocalActions.current
    val list = rememberLazyListState()
    val tint = rememberPlayerColors(state.thumbnail, stiffness = 90f).glow
    val origin = "collection:${route.id}"

    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(state.status) {
        if (state.status == LoadStatus.Ready) {
            delay(600)
            settled = true
        }
    }

    val savedIds by viewModel.savedIds.collectAsStateWithLifecycle()
    val playlistMenu = {
        actions.sheet(
            MenuRequest(
                title = state.title,
                subtitle = count(state.tracks.size),
                thumbnail = state.thumbnail,
                actions = listOf(
                    MenuAction(AmIcons.Edit, tr("Renommer", "Rename")) { actions.prompt(tr("Renommer la playlist", "Rename playlist"), state.title, viewModel::rename) },
                    MenuAction(AmIcons.Trash, tr("Supprimer la playlist", "Delete playlist"), destructive = true) {
                        viewModel.delete()
                        actions.back()
                    },
                ),
            ),
        )
    }

    DetailScaffold(
        title = state.title,
        state = list,
        revealAfter = 280.dp,
        underStatusBar = false,
        trailing = {
            when {
                state.editable -> IconButton(AmIcons.More, label = tr("Options de la playlist", "Playlist options"), onClick = playlistMenu)
                state.status == LoadStatus.Ready -> SaveButton(
                    saved = route.id in savedIds,
                    addLabel = tr("Ajouter à la bibliothèque", "Add to library"),
                    removeLabel = tr("Retirer de la bibliothèque", "Remove from library"),
                    onToggle = viewModel::toggleSaved,
                )
            }
        },
    ) {
        item(key = "header", contentType = "header") {
            CollectionHeader(
                state = state,
                route = route,
                tint = tint,
                onPlay = { actions.playAll(state.tracks, 0, false) },
                onShuffle = { actions.playAll(state.tracks, 0, true) },
            )
        }

        when (state.status) {
            LoadStatus.Loading -> item(key = "skeleton") { RowsSkeleton(7, Modifier.padding(top = 12.dp)) }

            LoadStatus.Error -> item(key = "error") {
                ErrorState(
                    title = if (state.isAlbum) tr("Album indisponible", "Album unavailable") else tr("Playlist indisponible", "Playlist unavailable"),
                    message = tr("Vérifiez votre connexion internet, puis réessayez.", "Check your internet connection, then try again."),
                    onRetry = viewModel::load,
                )
            }

            LoadStatus.Ready -> {
                state.description?.let { description ->
                    item(key = "description", contentType = "text") {
                        AmText(
                            text = description,
                            style = AmTheme.type.footnote,
                            color = AmTheme.colors.textSecondary,
                            maxLines = 3,
                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
                        )
                    }
                }
                itemsIndexed(state.tracks, key = { _, track -> track.id }, contentType = { _, _ -> "track" }) { index, track ->
                    val play = { actions.playAll(state.tracks, index, null) }
                    if (state.isAlbum) {
                        AlbumTrackRow(
                            index = index,
                            track = track,
                            showArtists = track.artists.isNotBlank() && track.artists != state.subtitle,
                            onClick = play,
                            modifier = Modifier.cascade(index, enabled = !settled),
                        )
                    } else {
                        ItemRow(
                            item = track,
                            origin = origin,
                            extras = if (state.editable) {
                                listOf(MenuAction(AmIcons.Remove, tr("Retirer de la playlist", "Remove from playlist"), destructive = true) { viewModel.remove(track) })
                            } else {
                                emptyList()
                            },
                            modifier = Modifier.fillMaxWidth().cascade(index, enabled = !settled).padding(horizontal = 20.dp, vertical = 6.dp),
                            onClick = play,
                        )
                    }
                }
                item(key = "footer", contentType = "text") {
                    AmText(
                        text = if (state.tracks.isEmpty() && state.editable) {
                            tr("Cette playlist est vide. Ajoutez-y des titres avec leur menu « ⋯ ».", "This playlist is empty. Add songs from their “⋯” menu.")
                        } else {
                            summary(state.tracks)
                        },
                        style = AmTheme.type.footnote,
                        color = AmTheme.colors.textTertiary,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                    )
                }
                if (state.otherVersions.isNotEmpty()) {
                    item(key = "othersHeader", contentType = "header") { SectionHeader(tr("Autres versions", "Other versions"), top = 12.dp) }
                    item(key = "others", contentType = "carousel") { ItemCarousel(state.otherVersions, origin) }
                }
            }
        }
    }
}

private fun count(tracks: Int) = if (tracks > 1) tr("$tracks titres", "$tracks songs") else tr("$tracks titre", "$tracks song")

private fun summary(tracks: List<YtTrack>): String {
    val seconds = tracks.sumOf { it.durationSeconds ?: return count(tracks.size) }
    val minutes = (seconds + 30) / 60
    val duration = if (minutes >= 60) "${minutes / 60} h ${minutes % 60} min" else "$minutes min"
    return "${count(tracks.size)} · $duration"
}

@Composable
private fun CollectionHeader(state: CollectionState, route: CollectionRoute, tint: Color, onPlay: () -> Unit, onShuffle: () -> Unit) {
    val colors = AmTheme.colors
    val actions = LocalActions.current
    val lift = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + DetailBarHeight
    val strength = if (colors.isDark) 0.62f else 0.34f
    val kind = if (state.isAlbum) "Album" else "Playlist"
    val artistId = state.artistId

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                val top = -lift.toPx()
                drawRect(
                    brush = Brush.verticalGradient(listOf(tint.copy(alpha = strength), tint.copy(alpha = 0f)), startY = top, endY = size.height * 0.94f),
                    topLeft = Offset(0f, top),
                    size = Size(size.width, size.height - top),
                )
            }
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(10.dp))
        Box(Modifier.size(232.dp).sharedCover(route.sharedKey)) {
            Cover(state.thumbnail, Modifier.fillMaxSize(), corner = 14.dp, requestSize = route.coverSize, elevated = true)
            Cover(state.thumbnail, Modifier.fillMaxSize(), corner = 14.dp, requestSize = 720, placeholder = false)
        }
        Spacer(Modifier.height(22.dp))
        AmText(state.title, style = AmTheme.type.section, textAlign = TextAlign.Center, maxLines = 3)
        if (state.subtitle.isNotBlank()) {
            val open = if (artistId != null) {
                Modifier.pressable(scale = 0.97f, label = tr("Voir l'artiste", "Go to artist")) { actions.openArtist(artistId, state.subtitle) }
            } else {
                Modifier
            }
            AmText(
                text = state.subtitle,
                style = AmTheme.type.headline.copy(fontSize = 19.sp, fontWeight = FontWeight.Medium),
                color = colors.accent,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = open.padding(top = 3.dp, bottom = 2.dp),
            )
        }
        AmText(
            text = listOfNotNull(kind, state.year?.toString()).joinToString(" · ").uppercase(),
            style = AmTheme.type.caption.copy(letterSpacing = 0.08.em, fontWeight = FontWeight.SemiBold),
            color = colors.textSecondary,
            modifier = Modifier.padding(top = 6.dp),
        )
        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val ready = state.tracks.isNotEmpty()
            PillButton(tr("Lecture", "Play"), AmIcons.Play, filled = true, enabled = ready, onClick = onPlay, modifier = Modifier.weight(1f))
            PillButton(tr("Aléatoire", "Shuffle"), AmIcons.Shuffle, filled = false, enabled = ready, onClick = onShuffle, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun AlbumTrackRow(index: Int, track: YtTrack, showArtists: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AmTheme.colors
    val actions = LocalActions.current
    val now = LocalNowPlaying.current
    val current = now.id == track.id
    Row(
        modifier
            .fillMaxWidth()
            .pressable(scale = 0.98f, onLongClick = { actions.menu(track, emptyList()) }, onClick = onClick)
            .height(56.dp)
            .drawBehind {
                val y = size.height - 0.5.dp.toPx()
                drawLine(colors.hairline, Offset(54.dp.toPx(), y), Offset(size.width, y), 1.dp.toPx())
            }
            .padding(start = 20.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(34.dp), contentAlignment = Alignment.CenterStart) {
            if (current) {
                NowPlayingBars(now.playing, colors.accent)
            } else {
                AmText("${index + 1}", style = AmTheme.type.subhead.copy(fontFeatureSettings = "tnum"), color = colors.textTertiary)
            }
        }
        Column(Modifier.weight(1f)) {
            AmText(track.title, style = AmTheme.type.body.copy(fontSize = 16.sp), color = if (current) colors.accent else colors.text, maxLines = 1)
            if (showArtists) AmText(track.artists, style = AmTheme.type.footnote, color = colors.textSecondary, maxLines = 1)
        }
        IconButton(AmIcons.More, label = tr("Plus d'options", "More options"), onClick = { actions.menu(track, emptyList()) }, tint = colors.textSecondary, iconSize = 20.dp)
    }
}
