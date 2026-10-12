package app.ammusic.ui.detail

import app.ammusic.youtube.tr

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.ammusic.ui.components.AmIcon
import app.ammusic.ui.components.AmIcons
import app.ammusic.ui.components.AmText
import app.ammusic.ui.components.LocalActions
import app.ammusic.ui.components.LocalBottomInset
import app.ammusic.ui.components.LocalHazeState
import app.ammusic.ui.components.glass
import app.ammusic.ui.components.pressable
import app.ammusic.ui.theme.AmTheme
import dev.chrisbanes.haze.hazeSource

val DetailBarHeight = 48.dp

@Composable
fun DetailScaffold(
    title: String,
    state: LazyListState,
    revealAfter: Dp,
    underStatusBar: Boolean,
    trailing: (@Composable () -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    val colors = AmTheme.colors
    val actions = LocalActions.current
    val density = LocalDensity.current
    val collapse = remember(state, revealAfter, density) {
        val start = with(density) { revealAfter.toPx() }
        val range = with(density) { 36.dp.toPx() }
        derivedStateOf {
            if (state.firstVisibleItemIndex > 0) 1f else ((state.firstVisibleItemScrollOffset - start) / range).coerceIn(0f, 1f)
        }
    }
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = state,
            modifier = Modifier.fillMaxSize().hazeSource(LocalHazeState.current).background(colors.background),
            contentPadding = PaddingValues(
                top = if (underStatusBar) 0.dp else statusBar + DetailBarHeight,
                bottom = LocalBottomInset.current,
            ),
            content = content,
        )

        Box(Modifier.fillMaxWidth().height(statusBar + DetailBarHeight)) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = collapse.value }
                    .glass(RectangleShape, bordered = false)
                    .drawBehind {
                        drawLine(colors.hairline, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
                    },
            )
            Box(Modifier.fillMaxSize().padding(top = statusBar)) {
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 8.dp)
                        .size(48.dp)
                        .pressable(scale = 0.88f, label = tr("Retour", "Back"), onClick = actions.back),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(34.dp)
                            .graphicsLayer { alpha = 1f - collapse.value }
                            .drawBehind { drawCircle(colors.glass.copy(alpha = 0.62f)) },
                    )
                    AmIcon(AmIcons.ChevronLeft, size = 22.dp, modifier = Modifier.padding(end = 2.dp))
                }
                if (trailing != null) {
                    Box(Modifier.align(Alignment.CenterEnd).padding(end = 8.dp).size(48.dp), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier
                                .size(34.dp)
                                .graphicsLayer { alpha = 1f - collapse.value }
                                .drawBehind { drawCircle(colors.glass.copy(alpha = 0.62f)) },
                        )
                        trailing()
                    }
                }
                AmText(
                    text = title,
                    style = AmTheme.type.headline,
                    maxLines = 1,
                    modifier = Modifier.align(Alignment.Center).padding(horizontal = 64.dp).graphicsLayer {
                        alpha = collapse.value
                        translationY = (1f - collapse.value) * 8.dp.toPx()
                    },
                )
            }
        }
    }
}

@Composable
fun PillButton(label: String, icon: ImageVector, filled: Boolean, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AmTheme.colors
    val content = if (filled) Color.White else colors.accent
    Row(
        modifier
            .height(50.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .pressable(haptic = true, label = label) { if (enabled) onClick() }
            .clip(RoundedCornerShape(14.dp))
            .background(if (filled) colors.accent else colors.surface),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AmIcon(icon, tint = content, size = 18.dp)
        AmText(label, style = AmTheme.type.headline, color = content, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
fun SaveButton(saved: Boolean, addLabel: String, removeLabel: String, onToggle: () -> Unit) {
    val turn by animateFloatAsState(if (saved) 1f else 0f, spring(dampingRatio = 1f, stiffness = 440f), label = "save")
    Box(
        Modifier.size(48.dp).pressable(scale = 0.85f, haptic = true, label = if (saved) removeLabel else addLabel, onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        AmIcon(
            icon = if (saved) AmIcons.Check else AmIcons.Plus,
            tint = if (saved) AmTheme.colors.accent else AmTheme.colors.text,
            size = 22.dp,
            modifier = Modifier.graphicsLayer {
                rotationZ = (1f - turn) * -90f * if (saved) 1f else 0f
                scaleX = 0.8f + 0.2f * if (saved) turn else 1f - turn
                scaleY = scaleX
            },
        )
    }
}
