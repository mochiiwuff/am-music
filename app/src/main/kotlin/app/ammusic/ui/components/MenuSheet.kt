package app.ammusic.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.ammusic.ui.theme.AmTheme
import kotlinx.coroutines.launch

class MenuAction(val icon: ImageVector, val label: String, val destructive: Boolean = false, val onClick: () -> Unit)

class MenuRequest(val title: String, val subtitle: String, val thumbnail: String?, val actions: List<MenuAction>)

@Composable
fun MenuSheet(request: MenuRequest?, onDismiss: () -> Unit) {
    val colors = AmTheme.colors
    val scope = rememberCoroutineScope()
    var shown by remember { mutableStateOf<MenuRequest?>(null) }
    val progress = remember { Animatable(0f) }
    val drag = remember { Animatable(0f) }

    LaunchedEffect(request) {
        if (request != null) {
            shown = request
            drag.snapTo(0f)
            progress.animateTo(1f, spring(dampingRatio = 1f, stiffness = 420f))
        } else {
            progress.animateTo(0f, spring(dampingRatio = 1f, stiffness = 520f))
            shown = null
        }
    }

    val current = shown ?: return
    val rows = current.actions
    val threshold = with(LocalDensity.current) { 96.dp.toPx() }
    BackHandler(enabled = request != null, onBack = onDismiss)

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind { drawRect(Color.Black, alpha = 0.52f * progress.value) }
                .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .graphicsLayer { translationY = (1f - progress.value) * size.height + drag.value }
                .draggable(
                    state = rememberDraggableState { delta -> scope.launch { drag.snapTo((drag.value + delta).coerceAtLeast(0f)) } },
                    orientation = Orientation.Vertical,
                    onDragStopped = { velocity ->
                        if (drag.value > threshold || velocity > 1400f) onDismiss() else drag.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 420f))
                    },
                )
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(colors.surface)
                .pointerInput(Unit) { detectTapGestures {} }
                .navigationBarsPadding()
                .padding(bottom = 10.dp),
        ) {
            Box(
                Modifier
                    .padding(top = 8.dp)
                    .align(Alignment.CenterHorizontally)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(colors.textTertiary.copy(alpha = 0.5f)),
            )
            Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Cover(current.thumbnail, Modifier.size(56.dp), corner = 9.dp, requestSize = RowCoverSize)
                Column(Modifier.padding(start = 14.dp)) {
                    AmText(current.title, style = AmTheme.type.headline, maxLines = 1)
                    AmText(current.subtitle, style = AmTheme.type.subhead, color = colors.textSecondary, maxLines = 1)
                }
            }
            Box(Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(1.dp).background(colors.hairline))
            Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                rows.forEachIndexed { index, action ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                val reveal = ((progress.value - 0.3f - index.coerceAtMost(5) * 0.05f) / 0.45f).coerceIn(0f, 1f)
                                alpha = reveal
                                translationY = (1f - reveal) * 14.dp.toPx()
                            }
                            .pressable(scale = 0.98f, haptic = true) {
                                onDismiss()
                                action.onClick()
                            }
                            .height(54.dp)
                            .padding(horizontal = 24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val tint = if (action.destructive) colors.accent else colors.text
                        AmIcon(action.icon, tint = tint, size = 22.dp)
                        AmText(action.label, color = tint, maxLines = 1, modifier = Modifier.padding(start = 18.dp))
                    }
                }
            }
        }
    }
}
