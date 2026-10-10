package app.ammusic.ui.player

import app.ammusic.youtube.tr

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.ammusic.playback.PlayerState
import app.ammusic.ui.components.AmIcon
import app.ammusic.ui.components.AmIcons
import app.ammusic.ui.components.AmText
import app.ammusic.ui.components.Cover
import app.ammusic.ui.components.IconButton
import app.ammusic.ui.components.pressable
import app.ammusic.ui.theme.AmTheme
import app.ammusic.youtube.model.YtTrack
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.math.abs

private val Soft = Color(0x99FFFFFF)

@Composable
fun QueuePanel(
    progress: () -> Float,
    top: Dp,
    state: PlayerState,
    background: Color,
    onClose: () -> Unit,
    onPlayAt: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onRemove: (Int) -> Unit,
) {
    val visible by remember { derivedStateOf { progress() > 0f } }
    if (!visible) return
    val haptics = LocalHapticFeedback.current

    Column(
        Modifier
            .fillMaxSize()
            .graphicsLayer { translationY = (1f - progress().coerceIn(0f, 1f)) * size.height }
            .padding(top = top)
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(background)
            .navigationBarsPadding(),
    ) {
        Row(Modifier.fillMaxWidth().height(96.dp).padding(start = 94.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                AmText(state.track?.title.orEmpty(), style = AmTheme.type.headline, color = Color.White, maxLines = 1)
                AmText(state.track?.artists.orEmpty(), style = AmTheme.type.subhead, color = Soft, maxLines = 1)
            }
            IconButton(AmIcons.ChevronDown, label = tr("Fermer la file d'attente", "Close queue"), onClick = onClose, tint = Color.White)
        }
        AmText(tr("File d'attente", "Queue"), style = AmTheme.type.section, color = Color.White, modifier = Modifier.padding(start = 24.dp, top = 8.dp, bottom = 8.dp))

        var tracks by remember { mutableStateOf(state.queue) }
        val listState = rememberLazyListState(initialFirstVisibleItemIndex = state.index)
        val reorder = rememberReorderableLazyListState(listState) { from, to ->
            tracks = tracks.toMutableList().apply { add(to.index, removeAt(from.index)) }
            onMove(from.index, to.index)
        }
        LaunchedEffect(state.queue) {
            if (!reorder.isAnyItemDragging) tracks = state.queue
        }
        val currentId = state.track?.id

        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
                ReorderableItem(reorder, key = track.id) { dragging ->
                    QueueRow(
                        track = track,
                        current = track.id == currentId,
                        lifted = dragging,
                        onClick = { onPlayAt(index) },
                        onRemove = {
                            tracks = tracks.filterNot { it.id == track.id }
                            onRemove(index)
                        },
                        handle = Modifier.draggableHandle(
                            onDragStarted = { haptics.performHapticFeedback(HapticFeedbackType.LongPress) },
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun QueueRow(
    track: YtTrack,
    current: Boolean,
    lifted: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    handle: Modifier,
) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val offset = remember { Animatable(0f) }
    var width by remember { mutableIntStateOf(1) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { width = it.width }
            .draggable(
                state = rememberDraggableState { delta -> scope.launch { offset.snapTo((offset.value + delta).coerceAtMost(0f)) } },
                orientation = Orientation.Horizontal,
                enabled = !current,
                onDragStopped = { velocity ->
                    if (offset.value < -width * 0.35f || velocity < -1600f) {
                        haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                        offset.animateTo(-width.toFloat(), spring(stiffness = 500f), velocity)
                        onRemove()
                    } else {
                        offset.animateTo(0f, spring(dampingRatio = 0.75f, stiffness = 420f), velocity)
                    }
                },
            )
            .graphicsLayer {
                translationX = offset.value
                alpha = 1f - (abs(offset.value) / width).coerceIn(0f, 1f)
                val lift = if (lifted) 1.03f else 1f
                scaleX = lift
                scaleY = lift
            }
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (current || lifted) Color(0x1FFFFFFF) else Color.Transparent)
            .pressable(scale = 0.98f, onClick = onClick)
            .padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(track.thumbnail, Modifier.size(46.dp), corner = 8.dp, requestSize = 160)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            AmText(track.title, style = AmTheme.type.subhead, color = Color.White, maxLines = 1)
            AmText(if (current) tr("En lecture", "Now playing") else track.artists, style = AmTheme.type.footnote, color = if (current) AmTheme.colors.accent else Soft, maxLines = 1)
        }
        Box(handle.size(48.dp), contentAlignment = Alignment.Center) {
            AmIcon(AmIcons.Grip, tint = Soft, size = 22.dp)
        }
    }
}
