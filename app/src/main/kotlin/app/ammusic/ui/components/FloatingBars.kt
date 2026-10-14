package app.ammusic.ui.components

import app.ammusic.youtube.tr

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlin.math.roundToInt
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import app.ammusic.playback.PlayerState
import app.ammusic.ui.player.PlayPauseIcon
import app.ammusic.ui.player.PlayerSheetState
import app.ammusic.ui.theme.AmTheme

enum class Tab(private val fr: String, private val en: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    Home("Accueil", "Home", AmIcons.Home, AmIcons.HomeFilled),
    Search("Recherche", "Search", AmIcons.Search, AmIcons.SearchBold),
    Library("Bibliothèque", "Library", AmIcons.Library, AmIcons.LibraryFilled),
    ;

    val label: String get() = tr(fr, en)
}

private val TabBarHeight = 64.dp
private val MiniPlayerHeight = 56.dp
private val Gap = 8.dp

val FloatingBarsHeight = TabBarHeight + Gap + MiniPlayerHeight + 24.dp

@Composable
fun FloatingBars(
    current: Tab,
    onSelect: (Tab) -> Unit,
    collapsed: Boolean,
    onExpand: () -> Unit,
    player: PlayerState,
    sheet: PlayerSheetState,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasTrack = player.track != null
    val folded = collapsed && hasTrack
    val progress = animateFloatAsState(
        targetValue = if (folded) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.84f, stiffness = 300f),
        label = "bars",
    )
    val presence = animateFloatAsState(
        targetValue = if (hasTrack) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 260f),
        label = "miniPresence",
    )
    Layout(
        content = {
            TabBar(current, onSelect, folded, { progress.value }, onExpand)
            if (hasTrack || presence.value > 0.01f) {
                Box(
                    Modifier.graphicsLayer {
                        val shown = presence.value.coerceIn(0f, 1f)
                        alpha = shown
                        scaleX = 0.94f + 0.06f * presence.value
                        scaleY = scaleX
                    },
                ) {
                    MiniPlayer(player, sheet, onTogglePlay, onNext)
                }
            }
        },
        modifier = modifier.navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
    ) { measurables, constraints ->
        val t = progress.value.coerceIn(0f, 1f)
        val width = constraints.maxWidth
        val tabHeight = TabBarHeight.roundToPx()
        val miniHeight = MiniPlayerHeight.roundToPx()
        val gap = Gap.roundToPx()

        val tabW = lerp(width, miniHeight, t)
        val tabH = lerp(tabHeight, miniHeight, t)
        val miniX = lerp(0, miniHeight + gap, t)
        val miniSpace = ((gap + miniHeight) * presence.value.coerceAtLeast(0f)).roundToInt()
        val height = lerp(tabHeight + miniSpace, miniHeight, t)

        val tab = measurables[0].measure(Constraints.fixed(tabW, tabH))
        val mini = measurables.getOrNull(1)?.measure(Constraints.fixed(width - miniX, miniHeight))
        layout(width, height) {
            mini?.place(miniX, 0)
            tab.place(0, height - tabH)
        }
    }
}

@Composable
private fun TabBar(
    current: Tab,
    onSelect: (Tab) -> Unit,
    collapsed: Boolean,
    progress: () -> Float,
    onExpand: () -> Unit,
) {
    val colors = AmTheme.colors
    val position = animateFloatAsState(
        targetValue = current.ordinal.toFloat(),
        animationSpec = spring(dampingRatio = 0.76f, stiffness = 420f),
        label = "tabPill",
    )
    Box(Modifier.fillMaxSize().barShadow().glass(CircleShape)) {
        Row(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = (1f - progress() * 2.2f).coerceIn(0f, 1f) }
                .slidingPill({ position.value }, Tab.entries.size, colors.text.copy(alpha = 0.08f), corner = 28.dp),
        ) {
            Tab.entries.forEach { tab ->
                val tint by animateColorAsState(
                    targetValue = if (tab == current) colors.accent else colors.textSecondary,
                    animationSpec = spring(stiffness = 400f),
                    label = "tabTint",
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .pressable(scale = 0.9f, haptic = true, label = tab.label) { onSelect(tab) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    AmIcon(if (tab == current) tab.selectedIcon else tab.icon, tint = tint)
                    Spacer(Modifier.height(4.dp))
                    AmText(tab.label, style = AmTheme.type.caption, color = tint, maxLines = 1)
                }
            }
        }
        Box(
            Modifier.fillMaxSize().graphicsLayer { alpha = ((progress() - 0.55f) * 2.2f).coerceIn(0f, 1f) },
            contentAlignment = Alignment.Center,
        ) {
            AmIcon(current.selectedIcon, tint = colors.accent)
        }
        if (collapsed) {
            Box(Modifier.fillMaxSize().pressable(scale = 0.9f, haptic = true, label = tr("Afficher les onglets", "Show tabs"), onClick = onExpand))
        }
    }
}

@Composable
private fun MiniPlayer(player: PlayerState, sheet: PlayerSheetState, onTogglePlay: () -> Unit, onNext: () -> Unit) {
    val colors = AmTheme.colors
    val track = player.track
    val active = player.isPlaying || player.isLoading
    Row(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { sheet.miniBounds = it.boundsInRoot() }
            .barShadow()
            .glass(CircleShape)
            .draggable(
                state = rememberDraggableState { sheet.dragBy(-it) },
                orientation = Orientation.Vertical,
                enabled = track != null,
                onDragStopped = { sheet.settle(-it) },
            )
            .pressable(scale = 0.985f, label = tr("Ouvrir le lecteur", "Open player")) { if (track != null) sheet.expand() }
            .padding(start = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(
            url = track?.thumbnail,
            modifier = Modifier
                .size(40.dp)
                .onGloballyPositioned { sheet.miniArtBounds = it.boundsInRoot() }
                .graphicsLayer { alpha = if (sheet.progress.value > 0f) 0f else 1f },
            requestSize = 160,
            circle = true,
        )
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            AmText(track?.title ?: tr("Aucune lecture en cours", "Nothing playing"), style = AmTheme.type.subhead, maxLines = 1)
            val subtitle = when {
                track == null -> tr("Choisissez un titre", "Pick a song")
                player.failed -> tr("Lecture impossible, nouvel essai…", "Playback failed, retrying…")
                player.isLoading -> tr("Chargement…", "Loading…")
                else -> track.artists
            }
            AmText(subtitle, style = AmTheme.type.footnote, color = colors.textSecondary, maxLines = 1)
        }
        if (track != null) {
            Box(
                Modifier.size(48.dp).pressable(scale = 0.85f, haptic = true, label = if (active) "Pause" else tr("Lecture", "Play"), onClick = onTogglePlay),
                contentAlignment = Alignment.Center,
            ) {
                PlayPauseIcon(playing = active, color = colors.text, size = 26.dp)
            }
            IconButton(
                icon = AmIcons.Next,
                label = tr("Titre suivant", "Next song"),
                onClick = onNext,
                tint = if (player.hasNext) colors.text else colors.textTertiary,
            )
        }
    }
}

@Composable
private fun Modifier.barShadow(): Modifier {
    val dark = AmTheme.colors.isDark
    return shadow(
        elevation = if (dark) 10.dp else 18.dp,
        shape = CircleShape,
        ambientColor = Color.Black.copy(alpha = if (dark) 0.3f else 0.12f),
        spotColor = Color.Black.copy(alpha = if (dark) 0.4f else 0.18f),
    )
}
