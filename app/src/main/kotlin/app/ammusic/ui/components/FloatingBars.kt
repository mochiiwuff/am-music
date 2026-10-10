package app.ammusic.ui.components

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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import app.ammusic.playback.PlayerState
import app.ammusic.ui.theme.AmTheme

enum class Tab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    Home("Accueil", AmIcons.Home, AmIcons.HomeFilled),
    Search("Recherche", AmIcons.Search, AmIcons.SearchBold),
    Library("Bibliothèque", AmIcons.Library, AmIcons.LibraryFilled),
}

private val TabBarHeight = 64.dp
private val MiniPlayerHeight = 56.dp
private val Gap = 8.dp

/** Hauteur occupée par les deux barres dépliées, marges comprises (hors barre système). */
val FloatingBarsHeight = TabBarHeight + Gap + MiniPlayerHeight + 24.dp

/**
 * Barre d'onglets et mini-lecteur flottants. Quand [collapsed] passe à vrai, la barre d'onglets
 * se réduit à un rond et le mini-lecteur descend se loger à côté d'elle.
 */
@Composable
fun FloatingBars(
    current: Tab,
    onSelect: (Tab) -> Unit,
    collapsed: Boolean,
    onExpand: () -> Unit,
    player: PlayerState,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = animateFloatAsState(
        targetValue = if (collapsed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.84f, stiffness = 300f),
        label = "bars",
    )
    Layout(
        content = {
            TabBar(current, onSelect, collapsed, { progress.value }, onExpand)
            MiniPlayer(player, onTogglePlay, onNext)
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
        val height = lerp(tabHeight + gap + miniHeight, miniHeight, t)

        val tab = measurables[0].measure(Constraints.fixed(tabW, tabH))
        val mini = measurables[1].measure(Constraints.fixed(width - miniX, miniHeight))
        layout(width, height) {
            mini.place(miniX, 0)
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
    Box(Modifier.fillMaxSize().glass(CircleShape)) {
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
            Box(Modifier.fillMaxSize().pressable(scale = 0.9f, haptic = true, label = "Afficher les onglets", onClick = onExpand))
        }
    }
}

@Composable
private fun MiniPlayer(player: PlayerState, onTogglePlay: () -> Unit, onNext: () -> Unit) {
    val colors = AmTheme.colors
    val track = player.track
    val active = player.isPlaying || player.isLoading
    Row(
        modifier = Modifier.fillMaxSize().glass(CircleShape).padding(start = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(track?.thumbnail, Modifier.size(40.dp), requestSize = 160, circle = true)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            AmText(track?.title ?: "Aucune lecture en cours", style = AmTheme.type.subhead, maxLines = 1)
            val subtitle = when {
                track == null -> "Choisissez un titre"
                player.failed -> "Lecture impossible, nouvel essai…"
                player.isLoading -> "Chargement…"
                else -> track.artists
            }
            AmText(subtitle, style = AmTheme.type.footnote, color = colors.textSecondary, maxLines = 1)
        }
        if (track != null) {
            IconButton(
                icon = if (active) AmIcons.Pause else AmIcons.Play,
                label = if (active) "Pause" else "Lecture",
                onClick = onTogglePlay,
            )
            IconButton(
                icon = AmIcons.Next,
                label = "Titre suivant",
                onClick = onNext,
                tint = if (player.hasNext) colors.text else colors.textTertiary,
            )
        }
    }
}
