package app.ammusic.ui.detail

import app.ammusic.youtube.tr

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ammusic.data.library.CoverSeparator
import app.ammusic.data.library.DownloadState
import app.ammusic.data.library.songCount
import app.ammusic.ui.CollectionRoute
import app.ammusic.ui.CollectionState
import app.ammusic.ui.CollectionViewModel
import app.ammusic.ui.LoadStatus
import app.ammusic.ui.components.AmIcon
import app.ammusic.ui.components.AmIcons
import app.ammusic.ui.components.AmText
import app.ammusic.ui.components.Cover
import app.ammusic.ui.components.ErrorState
import app.ammusic.ui.components.EmptyState
import app.ammusic.ui.components.IconButton
import app.ammusic.ui.components.ItemCarousel
import app.ammusic.ui.components.ItemRow
import app.ammusic.ui.components.MenuAction
import app.ammusic.ui.components.MenuRequest
import app.ammusic.ui.components.LocalActions
import app.ammusic.ui.components.LocalDownloads
import app.ammusic.ui.components.ProgressRing
import app.ammusic.ui.components.RowCoverSize
import app.ammusic.ui.components.LocalNowPlaying
import app.ammusic.ui.components.NowPlayingBars
import app.ammusic.ui.components.RowsSkeleton
import app.ammusic.ui.components.SectionHeader
import app.ammusic.ui.components.SwipeToRemove
import app.ammusic.ui.components.cascade
import app.ammusic.ui.components.hairlineBelow
import app.ammusic.ui.components.pressable
import app.ammusic.ui.components.sharedCover
import app.ammusic.ui.player.PlayPauseIcon
import app.ammusic.ui.player.PlayerColors
import app.ammusic.ui.player.rememberPlayerColors
import app.ammusic.ui.theme.AmTheme
import app.ammusic.youtube.model.YtTrack
import kotlinx.coroutines.delay
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun CollectionScreen(viewModel: CollectionViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val route = viewModel.route
    val actions = LocalActions.current
    val list = rememberLazyListState()
    val palette = rememberPlayerColors(state.thumbnail?.substringBefore(CoverSeparator), stiffness = 90f)
    val origin = "collection:${route.id}"
    val now = LocalNowPlaying.current
    val active = remember(now.id, state.tracks) { now.id != null && state.tracks.any { it.id == now.id } }
    val divider = AmTheme.colors.hairline
    var editing by rememberSaveable { mutableStateOf(false) }
    var order by remember { mutableStateOf<List<YtTrack>?>(null) }
    val shown = order ?: state.tracks
    val haptics = LocalHapticFeedback.current
    val reorder = rememberReorderableLazyListState(list) { from, to ->
        val current = order ?: return@rememberReorderableLazyListState
        val start = current.indexOfFirst { it.id == from.key }
        val end = current.indexOfFirst { it.id == to.key }
        if (start >= 0 && end >= 0) order = current.toMutableList().apply { add(end, removeAt(start)) }
    }
    val finishEditing = {
        order?.let(viewModel::reorder)
        editing = false
        order = null
    }
    BackHandler(enabled = editing, onBack = finishEditing)

    var settled by rememberSaveable { mutableStateOf(false) }
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
                subtitle = songCount(state.tracks.size),
                thumbnail = state.thumbnail,
                actions = listOfNotNull(
                    if (state.tracks.size > 1) {
                        MenuAction(AmIcons.Grip, tr("Modifier l'ordre", "Reorder")) {
                            order = state.tracks
                            editing = true
                        }
                    } else {
                        null
                    },
                    MenuAction(AmIcons.Edit, tr("Renommer", "Rename")) { actions.prompt(tr("Renommer la playlist", "Rename playlist"), state.title, state.thumbnail, viewModel::rename) },
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
        revealAfter = 296.dp,
        underStatusBar = false,
        trailing = {
            when {
                editing -> DoneButton(finishEditing)
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
                list = list,
                palette = palette,
                active = active,
                playing = now.playing,
                animate = !settled,
                onPlay = { if (active) actions.toggle() else actions.playAll(state.tracks, 0, false) },
                onShuffle = { actions.playAll(state.tracks, 0, true) },
                onDownload = { actions.download(state.tracks) },
                onRemoveDownloads = { actions.removeDownloads(state.tracks.map { it.id }) },
            )
        }

        when (state.status) {
            LoadStatus.Loading -> item(key = "skeleton") { RowsSkeleton(7, Modifier.padding(top = 12.dp)) }

            LoadStatus.Error -> item(key = "error") {
                ErrorState(
                    title = if (state.isAlbum) tr("Album indisponible", "Album unavailable") else tr("Playlist indisponible", "Playlist unavailable"),
                    message = tr("Vérifiez votre connexion.", "Check your connection."),
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
                itemsIndexed(shown, key = { _, track -> track.id }, contentType = { _, _ -> "track" }) { index, track ->
                    val play = { actions.playAll(state.tracks, index, null) }
                    if (editing) {
                        ReorderableItem(reorder, key = track.id) { lifted ->
                            EditRow(
                                track = track,
                                lifted = lifted,
                                handle = Modifier.draggableHandle(onDragStarted = { haptics.performHapticFeedback(HapticFeedbackType.LongPress) }),
                                modifier = Modifier.hairlineBelow(divider, 80.dp),
                            )
                        }
                        return@itemsIndexed
                    }
                    val placed = Modifier.fillMaxWidth().animateItem().cascade(index, enabled = !settled).hairlineBelow(divider, 80.dp)
                    when {
                        state.isAlbum -> AlbumTrackRow(
                            index = index,
                            track = track,
                            showArtists = track.artists.isNotBlank() && track.artists != state.subtitle,
                            onClick = play,
                            modifier = Modifier.cascade(index, enabled = !settled),
                        )

                        state.editable -> SwipeToRemove(onRemove = { viewModel.remove(track) }, modifier = placed) {
                            ItemRow(
                                item = track,
                                origin = origin,
                                extras = listOf(
                                    MenuAction(AmIcons.Remove, tr("Retirer de la playlist", "Remove from playlist"), destructive = true) { viewModel.remove(track) },
                                ),
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                                onClick = play,
                            )
                        }

                        else -> ItemRow(track, origin, placed.padding(horizontal = 20.dp, vertical = 6.dp), onClick = play)
                    }
                }
                if (state.tracks.isEmpty() && state.editable) {
                    item(key = "empty", contentType = "empty") {
                        EmptyState(AmIcons.Playlist, tr("Cette playlist est vide", "This playlist is empty"))
                    }
                } else {
                    item(key = "footer", contentType = "text") {
                        AmText(
                            text = summary(state.tracks),
                            style = AmTheme.type.footnote,
                            color = AmTheme.colors.textTertiary,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                        )
                    }
                }
                if (state.otherVersions.isNotEmpty()) {
                    item(key = "othersHeader", contentType = "header") { SectionHeader(tr("Autres versions", "Other versions"), top = 12.dp) }
                    item(key = "others", contentType = "carousel") { ItemCarousel(state.otherVersions, origin) }
                }
            }
        }
    }
}

private fun summary(tracks: List<YtTrack>): String {
    val seconds = tracks.sumOf { it.durationSeconds ?: return songCount(tracks.size) }
    val minutes = (seconds + 30) / 60
    val duration = if (minutes >= 60) "${minutes / 60} h ${minutes % 60} min" else "$minutes min"
    return "${songCount(tracks.size)} · $duration"
}

@Composable
private fun CollectionHeader(
    state: CollectionState,
    route: CollectionRoute,
    list: LazyListState,
    palette: PlayerColors,
    active: Boolean,
    playing: Boolean,
    animate: Boolean,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onDownload: () -> Unit,
    onRemoveDownloads: () -> Unit,
) {
    val colors = AmTheme.colors
    val actions = LocalActions.current
    val lift = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + DetailBarHeight
    val strength = if (colors.isDark) 0.58f else 0.3f
    val kind = if (state.isAlbum) "Album" else "Playlist"
    val artistId = state.artistId
    val drift = rememberInfiniteTransition(label = "aura")
    val a by drift.animateFloat(0f, 1f, infiniteRepeatable(tween(13_000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "a")
    val b by drift.animateFloat(0f, 1f, infiniteRepeatable(tween(19_000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b")
    val away = { height: Float ->
        val scrolled = if (list.firstVisibleItemIndex == 0) list.firstVisibleItemScrollOffset.toFloat() else height
        (scrolled / height).coerceIn(0f, 1f)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                val top = -lift.toPx()
                val area = Size(size.width, size.height - top)
                val origin = Offset(0f, top)
                val glow = palette.glow
                val shade = palette.shade
                drawRect(
                    brush = Brush.verticalGradient(listOf(glow.copy(alpha = strength * 0.55f), glow.copy(alpha = 0f)), startY = top, endY = size.height * 0.9f),
                    topLeft = origin,
                    size = area,
                )
                val radius = size.width * 0.92f
                val first = Offset(lerp(size.width * 0.04f, size.width * 0.62f, a), top + lerp(area.height * 0.02f, area.height * 0.3f, b))
                drawRect(Brush.radialGradient(listOf(glow.copy(alpha = strength), glow.copy(alpha = 0f)), first, radius), origin, area)
                val second = Offset(lerp(size.width * 1.02f, size.width * 0.42f, b), top + lerp(area.height * 0.46f, area.height * 0.16f, a))
                drawRect(Brush.radialGradient(listOf(shade.copy(alpha = strength * 0.9f), shade.copy(alpha = 0f)), second, radius), origin, area)
                val fade = size.height * 0.5f
                drawRect(
                    brush = Brush.verticalGradient(listOf(colors.background.copy(alpha = 0f), colors.background), startY = fade, endY = size.height),
                    topLeft = Offset(0f, fade),
                    size = Size(size.width, size.height - fade),
                )
            }
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(10.dp))
        val glyph = if (state.isAlbum) null else AmIcons.Playlist
        Box(
            Modifier
                .size(248.dp)
                .drawBehind {
                    val center = Offset(size.width / 2f, size.height * 0.6f)
                    val radius = size.width * 0.8f
                    val halo = palette.glow.copy(alpha = (if (colors.isDark) 0.5f else 0.42f) * (1f - away(size.height)))
                    drawCircle(Brush.radialGradient(listOf(halo, halo.copy(alpha = 0f)), center, radius), radius, center)
                }
                .graphicsLayer {
                    val gone = away(size.height)
                    val shrink = 1f - 0.18f * gone
                    scaleX = shrink
                    scaleY = shrink
                    alpha = 1f - gone * gone
                    transformOrigin = TransformOrigin(0.5f, 1f)
                }
                .sharedCover(route.sharedKey),
        ) {
            Cover(state.thumbnail, Modifier.fillMaxSize(), corner = 14.dp, requestSize = route.coverSize, elevated = true, glyph = glyph)
            Cover(state.thumbnail, Modifier.fillMaxSize(), corner = 14.dp, requestSize = 720, placeholder = false)
        }
        Spacer(Modifier.height(22.dp))
        AmText(state.title, style = AmTheme.type.section, textAlign = TextAlign.Center, maxLines = 3, modifier = Modifier.cascade(2, animate))
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
                modifier = Modifier.cascade(4, animate).then(open).padding(top = 3.dp, bottom = 2.dp),
            )
        }
        if (state.isAlbum) {
            AmText(
                text = listOfNotNull(kind, state.year?.toString()).joinToString(" · ").uppercase(),
                style = AmTheme.type.caption.copy(letterSpacing = 0.08.em, fontWeight = FontWeight.SemiBold),
                color = colors.textSecondary,
                modifier = Modifier.cascade(6, animate).padding(top = 6.dp),
            )
        }
        Spacer(Modifier.height(22.dp))
        Row(Modifier.cascade(8, animate), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val ready = state.tracks.isNotEmpty()
            PlayButton(pause = active && playing, enabled = ready, onClick = onPlay, modifier = Modifier.weight(1f))
            PillButton(tr("Aléatoire", "Shuffle"), AmIcons.Shuffle, filled = false, enabled = ready, onClick = onShuffle, modifier = Modifier.weight(1f))
            DownloadButton(state.tracks, enabled = ready, onDownload = onDownload, onRemove = onRemoveDownloads)
        }
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun PlayButton(pause: Boolean, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = if (pause) "Pause" else tr("Lecture", "Play")
    Row(
        modifier
            .height(50.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .pressable(haptic = true, label = label) { if (enabled) onClick() }
            .clip(RoundedCornerShape(14.dp))
            .background(AmTheme.colors.accent),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlayPauseIcon(playing = pause, color = Color.White, size = 20.dp)
        AnimatedContent(
            targetState = label,
            transitionSpec = {
                val enter = fadeIn(spring(stiffness = 380f)) + slideInVertically(spring(dampingRatio = 0.8f, stiffness = 380f)) { it / 2 }
                enter togetherWith fadeOut(spring(stiffness = 520f)) + slideOutVertically(spring(stiffness = 520f)) { -it / 2 }
            },
            label = "play",
        ) { text ->
            AmText(text, style = AmTheme.type.headline, color = Color.White, modifier = Modifier.padding(start = 8.dp))
        }
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
            .pressable(scale = 0.98f, onLongClick = { actions.preview(track, emptyList(), null) }, onClick = onClick)
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

@Composable
private fun DownloadButton(tracks: List<YtTrack>, enabled: Boolean, onDownload: () -> Unit, onRemove: () -> Unit) {
    val colors = AmTheme.colors
    val actions = LocalActions.current
    val info = LocalDownloads.current
    val states = tracks.map { info.states[it.id] }
    val done = states.count { it == DownloadState.Done }
    val pending = states.count { it == DownloadState.Queued || it == DownloadState.Running }
    val complete = tracks.isNotEmpty() && done == tracks.size
    val fraction = if (tracks.isEmpty()) 0f else (done + tracks.sumOf { (info.running[it.id] ?: 0f).toDouble() }.toFloat()) / tracks.size
    val label = when {
        complete -> tr("Supprimer les téléchargements", "Remove downloads")
        pending > 0 -> tr("Téléchargement en cours", "Downloading")
        else -> tr("Télécharger", "Download")
    }
    Box(
        Modifier
            .size(50.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .pressable(scale = 0.9f, haptic = true, label = label) {
                when {
                    !enabled || pending > 0 -> Unit
                    complete -> actions.sheet(
                        MenuRequest(
                            title = label,
                            subtitle = songCount(done),
                            thumbnail = null,
                            actions = listOf(MenuAction(AmIcons.Trash, tr("Supprimer du téléphone", "Remove from this phone"), destructive = true, onClick = onRemove)),
                        ),
                    )
                    else -> onDownload()
                }
            }
            .clip(CircleShape)
            .background(colors.surface),
        contentAlignment = Alignment.Center,
    ) {
        if (pending > 0 || (done > 0 && !complete)) ProgressRing(fraction, colors.accent, Modifier.size(38.dp), stroke = 2.4.dp)
        AnimatedContent(
            targetState = complete,
            transitionSpec = { (fadeIn(spring(stiffness = 420f)) + scaleIn(spring(dampingRatio = 1f, stiffness = 420f), 0.7f)) togetherWith fadeOut(spring(stiffness = 520f)) },
            label = "download",
        ) { saved ->
            AmIcon(if (saved) AmIcons.Downloaded else AmIcons.Download, tint = colors.accent, size = if (pending > 0) 18.dp else 22.dp)
        }
    }
}

@Composable
private fun DoneButton(onClick: () -> Unit) {
    Box(Modifier.height(48.dp).pressable(haptic = true, label = tr("Terminé", "Done"), onClick = onClick).padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
        AmText(tr("OK", "Done"), style = AmTheme.type.headline, color = AmTheme.colors.accent)
    }
}

@Composable
private fun EditRow(track: YtTrack, lifted: Boolean, handle: Modifier, modifier: Modifier = Modifier) {
    val colors = AmTheme.colors
    val lift by animateFloatAsState(if (lifted) 1f else 0f, spring(dampingRatio = 1f, stiffness = 520f), label = "lift")
    Row(
        modifier
            .fillMaxWidth()
            .zIndex(if (lifted) 1f else 0f)
            .graphicsLayer {
                scaleX = 1f + 0.03f * lift
                scaleY = scaleX
                shadowElevation = 18.dp.toPx() * lift
                shape = RoundedCornerShape(12.dp)
                clip = lift > 0f
            }
            .background(colors.surfaceHigh.copy(alpha = lift))
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(track.thumbnail, Modifier.size(48.dp), corner = 6.dp, requestSize = RowCoverSize)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            AmText(track.title, style = AmTheme.type.body.copy(fontSize = 16.sp, lineHeight = 21.sp), maxLines = 1)
            AmText(track.artists, style = AmTheme.type.footnote, color = colors.textSecondary, maxLines = 1)
        }
        Box(handle.size(48.dp), contentAlignment = Alignment.Center) {
            AmIcon(AmIcons.Grip, tint = colors.textSecondary, size = 22.dp)
        }
    }
}
