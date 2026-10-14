package app.ammusic.ui.player

import app.ammusic.youtube.tr

import android.app.Activity
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.foundation.Canvas
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.core.view.WindowCompat
import app.ammusic.playback.PlayerState
import app.ammusic.playback.RepeatMode
import app.ammusic.playback.showAudioOutputs
import app.ammusic.ui.PlayerViewModel
import app.ammusic.ui.components.AmIcon
import app.ammusic.ui.components.AmIcons
import app.ammusic.ui.components.AmText
import app.ammusic.ui.components.Cover
import app.ammusic.ui.components.pressable
import app.ammusic.ui.theme.AmTheme
import app.ammusic.youtube.model.YtTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private val SheetSpring = spring<Float>(dampingRatio = 0.9f, stiffness = 260f)
private val PanelSpring = spring<Float>(dampingRatio = 0.88f, stiffness = 300f)
private val Soft = Color(0x99FFFFFF)

@Stable
class PlayerSheetState(private val scope: CoroutineScope) {
    val progress = Animatable(0f)
    var miniBounds by mutableStateOf(Rect.Zero)
    var miniArtBounds by mutableStateOf(Rect.Zero)
    var travelPx = 1f
    val isOpen by derivedStateOf { progress.value > 0f }

    fun dragBy(upPx: Float) {
        scope.launch { progress.snapTo((progress.value + upPx / travelPx).coerceIn(0f, 1f)) }
    }

    fun settle(upVelocity: Float) {
        val target = when {
            upVelocity > 900f -> 1f
            upVelocity < -900f -> 0f
            progress.value > 0.5f -> 1f
            else -> 0f
        }
        animateTo(target, upVelocity / travelPx)
    }

    fun expand() = animateTo(1f)

    fun collapse() = animateTo(0f)

    private fun animateTo(target: Float, velocity: Float = 0f) {
        scope.launch { progress.animateTo(target, SheetSpring, velocity) }
    }
}

@Composable
fun rememberPlayerSheetState(): PlayerSheetState {
    val scope = rememberCoroutineScope()
    return remember { PlayerSheetState(scope) }
}

@Composable
fun PlayerSheet(sheet: PlayerSheetState, state: PlayerState, player: PlayerViewModel) {
    val track = state.track ?: return
    val density = LocalDensity.current
    val colors = rememberPlayerColors(track.thumbnail)
    val queue = remember { Animatable(0f) }
    val lyrics = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    PredictiveBackHandler { events ->
        val queueOpen = queue.targetValue > 0f
        val lyricsOpen = lyrics.targetValue > 0f
        val panelOpen = queueOpen || lyricsOpen
        try {
            events.collect { if (!panelOpen) sheet.progress.snapTo(1f - it.progress * 0.18f) }
            when {
                lyricsOpen -> lyrics.animateTo(0f, PanelSpring)
                queueOpen -> queue.animateTo(0f, PanelSpring)
                else -> sheet.collapse()
            }
        } catch (_: CancellationException) {
            if (!panelOpen) sheet.expand()
        }
    }
    LightSystemBars(sheet)

    BoxWithConstraints(
        Modifier.fillMaxSize().draggable(
            state = rememberDraggableState { sheet.dragBy(-it) },
            orientation = Orientation.Vertical,
            onDragStopped = { sheet.settle(-it) },
        ),
    ) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        val statusBar = WindowInsets.statusBars.getTop(density).toFloat()
        val artSize: Float
        val fullArt: Rect
        val queueTop: Float
        val queueArt: Rect
        with(density) {
            artSize = min(width - 56.dp.toPx(), height * 0.40f)
            fullArt = Rect(Offset((width - artSize) / 2f, statusBar + 60.dp.toPx()), Size(artSize, artSize))
            queueTop = statusBar + 8.dp.toPx()
            queueArt = Rect(Offset(24.dp.toPx(), queueTop + 20.dp.toPx()), Size(56.dp.toPx(), 56.dp.toPx()))
        }

        val clip = remember { Path() }
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = (sheet.progress.value * 5f).coerceIn(0f, 1f) }
                .drawWithContent {
                    val p = sheet.progress.value.coerceIn(0f, 1f)
                    val mini = sheet.miniBounds
                    val rect = Rect(lerp(mini.left, 0f, p), lerp(mini.top, 0f, p), lerp(mini.right, size.width, p), lerp(mini.bottom, size.height, p))
                    clip.rewind()
                    clip.addRoundRect(RoundRect(rect, CornerRadius(lerp(28.dp.toPx(), 0f, p))))
                    clipPath(clip) { this@drawWithContent.drawContent() }
                },
        ) {
            PlayerBackdrop(colors)
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    val p = sheet.progress.value.coerceIn(0f, 1f)
                    alpha = ((p - 0.3f) / 0.5f).coerceIn(0f, 1f)
                    translationY = lerp(sheet.miniBounds.top, 0f, p)
                },
            ) {
                PlayerContent(
                    state = state,
                    track = track,
                    player = player,
                    artSpace = with(density) { artSize.toDp() },
                    onCollapse = sheet::collapse,
                    onQueue = { scope.launch { queue.animateTo(1f, PanelSpring) } },
                    onLyrics = { scope.launch { lyrics.animateTo(1f, PanelSpring) } },
                    fade = { lyrics.value },
                )
                QueuePanel(
                    progress = { queue.value },
                    top = with(density) { queueTop.toDp() },
                    state = state,
                    background = lerp(colors.base, Color.White, 0.07f),
                    onClose = { scope.launch { queue.animateTo(0f, PanelSpring) } },
                    onPlayAt = player::playAt,
                    onMove = player::move,
                    onRemove = player::remove,
                )
                LyricsPanel(
                    progress = { lyrics.value },
                    top = with(density) { queueTop.toDp() },
                    state = state,
                    player = player,
                    onClose = { scope.launch { lyrics.animateTo(0f, PanelSpring) } },
                )
            }
        }

        SharedArtwork(
            sheet = sheet,
            queue = { max(queue.value, lyrics.value) },
            track = track,
            index = state.index,
            playing = state.isPlaying || state.isLoading,
            fullArt = fullArt,
            queueArt = queueArt,
            screenWidth = width,
            canNext = state.hasNext,
            canPrevious = state.hasPrevious,
            onNext = player::next,
            onPrevious = player::previousTrack,
        )
    }
}

@Composable
private fun LightSystemBars(sheet: PlayerSheetState) {
    val view = LocalView.current
    val dark = AmTheme.colors.isDark
    val covered by remember { derivedStateOf { sheet.progress.value > 0.6f } }
    DisposableEffect(covered, dark) {
        val window = (view.context as? Activity)?.window
        val bars = window?.let { WindowCompat.getInsetsController(it, view) }
        bars?.isAppearanceLightStatusBars = !dark && !covered
        bars?.isAppearanceLightNavigationBars = !dark && !covered
        onDispose {
            bars?.isAppearanceLightStatusBars = !dark
            bars?.isAppearanceLightNavigationBars = !dark
        }
    }
}

@Composable
private fun PlayerContent(
    state: PlayerState,
    track: YtTrack,
    player: PlayerViewModel,
    artSpace: Dp,
    onCollapse: () -> Unit,
    onQueue: () -> Unit,
    onLyrics: () -> Unit,
    fade: () -> Float,
) {
    val accent = AmTheme.colors.accent
    val context = LocalContext.current
    val active = state.isPlaying || state.isLoading
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 28.dp)) {
        Box(
            Modifier.fillMaxWidth().height(48.dp).pressable(label = tr("Réduire le lecteur", "Minimize player"), onClick = onCollapse),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(width = 38.dp, height = 5.dp).clip(CircleShape).background(Color(0x66FFFFFF)))
        }
        Spacer(Modifier.height(12.dp + artSpace))

        Column(
            Modifier.weight(1f).fillMaxWidth().graphicsLayer { alpha = 1f - fade().coerceIn(0f, 1f) },
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    AmText(track.title, style = AmTheme.type.section, color = Color.White, maxLines = 1)
                    AmText(
                        text = if (state.failed) tr("Lecture impossible, nouvel essai…", "Playback failed, retrying…") else track.artists,
                        color = Color(0xB3FFFFFF),
                        maxLines = 1,
                    )
                }
                HeartButton(state.liked, player::toggleLike, tint = Color.White, likedTint = accent)
            }

            ProgressSection(track.id, state.isPlaying, state.durationMs, player)

            Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly, Alignment.CenterVertically) {
                SkipButton(forward = false, label = tr("Titre précédent", "Previous song"), enabled = true, size = 72.dp, iconSize = 42.dp) {
                    SkipHint.mark(-1)
                    player.previous()
                }
                Box(
                    Modifier.size(84.dp).pressable(scale = 0.86f, haptic = true, label = if (active) "Pause" else tr("Lecture", "Play"), onClick = player::toggle),
                    contentAlignment = Alignment.Center,
                ) {
                    PlayPauseIcon(playing = active, color = Color.White, size = 60.dp)
                }
                SkipButton(forward = true, label = tr("Titre suivant", "Next song"), enabled = state.hasNext, size = 72.dp, iconSize = 42.dp) {
                    SkipHint.mark(1)
                    player.next()
                }
            }

            val (volume, setVolume) = rememberSystemVolume()
            Row(verticalAlignment = Alignment.CenterVertically) {
                AmIcon(AmIcons.VolumeLow, tint = Soft, size = 20.dp)
                ThinSlider(volume.value, onChange = setVolume, modifier = Modifier.weight(1f).padding(horizontal = 10.dp))
                AmIcon(AmIcons.VolumeHigh, tint = Soft, size = 20.dp)
            }

            Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly, Alignment.CenterVertically) {
                ToggleButton(AmIcons.Shuffle, tr("Lecture aléatoire", "Shuffle"), state.shuffle, player::toggleShuffle)
                ToggleButton(AmIcons.Lyrics, tr("Paroles", "Lyrics"), false, onLyrics)
                ToggleButton(AmIcons.AudioOutput, tr("Sortie audio", "Audio output"), false, { showAudioOutputs(context) })
                ToggleButton(AmIcons.Queue, tr("File d'attente", "Queue"), false, onQueue)
                ToggleButton(
                    icon = AmIcons.Repeat,
                    label = when (state.repeat) {
                        RepeatMode.Off -> tr("Répéter la liste", "Repeat all")
                        RepeatMode.All -> tr("Répéter ce titre", "Repeat this song")
                        RepeatMode.One -> tr("Ne plus répéter", "Stop repeating")
                    },
                    active = state.repeat != RepeatMode.Off,
                    onClick = player::cycleRepeat,
                    badge = state.repeat == RepeatMode.One,
                )
            }
        }
    }
}

@Composable
internal fun ProgressSection(trackId: String, playing: Boolean, durationMs: Long, player: PlayerViewModel) {
    var position by remember(trackId) { mutableLongStateOf(player.positionMs) }
    var seeking by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(trackId, playing) {
        while (true) {
            position = player.positionMs
            delay(250)
        }
    }
    val shown = seeking?.let { (it * durationMs).toLong() } ?: position.coerceAtMost(durationMs.coerceAtLeast(position))
    val target = if (durationMs > 0) shown / durationMs.toFloat() else 0f
    val bar = remember { Animatable(target) }
    LaunchedEffect(target, seeking != null) {
        if (seeking != null || abs(target - bar.value) < 0.02f) bar.snapTo(target) else bar.animateTo(target, spring(dampingRatio = 1f, stiffness = 160f))
    }
    val times = AmTheme.type.caption.copy(fontFeatureSettings = "tnum")
    Column {
        ThinSlider(
            value = bar.value,
            onChange = { seeking = it },
            onRelease = {
                position = (it * durationMs).toLong()
                player.seekTo(position)
                seeking = null
            },
        )
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
            AmText(formatTime(shown), style = times, color = Soft)
            AmText(if (durationMs > 0) "-" + formatTime(durationMs - shown) else "--:--", style = times, color = Soft)
        }
    }
}

@Composable
private fun ToggleButton(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit, badge: Boolean = false) {
    val accent = AmTheme.colors.accent
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val settle = spring<Float>(dampingRatio = 1f, stiffness = 440f)
    val on by animateFloatAsState(if (active) 1f else 0f, settle, label = "on")
    val mark by animateFloatAsState(if (badge) 1f else 0f, settle, label = "badge")
    val tint by animateColorAsState(
        targetValue = if (active) accent else if (pressed) Color.White else Soft,
        animationSpec = if (pressed) spring(dampingRatio = 1f, stiffness = 2400f) else spring(dampingRatio = 1f, stiffness = 440f),
        label = "tint",
    )
    Box(
        Modifier.size(56.dp).pressable(scale = 0.9f, haptic = true, label = label, source = interaction, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AmIcon(icon, tint = tint, size = 26.dp)
        AmText(
            text = "1",
            style = AmTheme.type.caption.copy(fontSize = 11.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold),
            color = accent,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 7.dp, end = 7.dp).graphicsLayer {
                alpha = mark
                scaleX = 0.6f + 0.4f * mark
                scaleY = scaleX
            },
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 5.dp)
                .size(4.dp)
                .graphicsLayer {
                    alpha = on
                    scaleX = on
                    scaleY = on
                }
                .clip(CircleShape)
                .background(accent),
        )
    }
}

object SkipHint {
    private var direction = 0
    private var at = 0L

    fun mark(towards: Int) {
        direction = towards
        at = System.currentTimeMillis()
    }

    fun take(): Int {
        val recent = System.currentTimeMillis() - at < 1_500L
        val value = if (recent) direction else 0
        direction = 0
        return value
    }
}

@Composable
private fun SharedArtwork(
    sheet: PlayerSheetState,
    queue: () -> Float,
    track: YtTrack,
    index: Int,
    playing: Boolean,
    fullArt: Rect,
    queueArt: Rect,
    screenWidth: Float,
    canNext: Boolean,
    canPrevious: Boolean,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val breathe by animateFloatAsState(if (playing) 1f else 0.84f, spring(dampingRatio = 0.62f, stiffness = 240f), label = "breathe")
    val currentX = remember { Animatable(0f) }
    val outgoingX = remember { Animatable(0f) }
    var shown by remember { mutableStateOf(track) }
    var outgoing by remember { mutableStateOf<YtTrack?>(null) }
    var lastIndex by remember { mutableIntStateOf(index) }
    val latestTrack by rememberUpdatedState(track.id)

    val p = sheet.progress.value.coerceIn(0f, 1f)
    val q = queue().coerceIn(0f, 1f)
    val mini = sheet.miniArtBounds
    val resting = Rect(fullArt.center - Offset(fullArt.width, fullArt.height) * (breathe / 2f), fullArt.size * breathe)
    val rect = lerp(mini, lerp(resting, queueArt, q), p)
    val corner = with(density) { lerp(mini.width / 2f, lerp(18.dp.toPx(), 10.dp.toPx(), q), p) }
    val travel = screenWidth * 0.92f

    LaunchedEffect(track.id) {
        if (track.id == shown.id) return@LaunchedEffect
        val hinted = SkipHint.take()
        val forward = when {
            hinted != 0 -> hinted > 0
            index < lastIndex -> lastIndex - index > 1 && index == 0
            else -> true
        }
        lastIndex = index
        val from = currentX.value
        outgoing = shown
        shown = track
        if (p < 0.99f || q > 0.01f) {
            outgoing = null
            currentX.snapTo(0f)
            return@LaunchedEffect
        }
        outgoingX.snapTo(from)
        currentX.snapTo(if (forward) travel else -travel)
        coroutineScope {
            launch { outgoingX.animateTo(if (forward) -travel else travel, spring(dampingRatio = 1f, stiffness = 240f)) }
            launch { currentX.animateTo(0f, spring(dampingRatio = 0.88f, stiffness = 240f)) }
        }
        outgoing = null
    }

    @Composable
    fun Frame(item: YtTrack, offset: () -> Float, modifier: Modifier = Modifier) {
        Box(
            modifier
                .offset { IntOffset((rect.left + offset()).roundToInt(), rect.top.roundToInt()) }
                .requiredSize(with(density) { rect.width.toDp() }, with(density) { rect.height.toDp() })
                .graphicsLayer {
                    val away = (abs(offset()) / travel).coerceIn(0f, 1f)
                    shadowElevation = 28.dp.toPx() * p * (1f - q) * (1f - away)
                    shape = RoundedCornerShape(corner)
                    clip = true
                    alpha = if (ShareOverlay.visible) 0f else 1f - away * 0.55f
                    val shrink = 1f - 0.08f * away
                    scaleX = shrink
                    scaleY = shrink
                },
        ) {
            Cover(item.thumbnail, Modifier.fillMaxSize(), corner = 0.dp, requestSize = 160)
            Cover(item.thumbnail, Modifier.fillMaxSize(), corner = 0.dp, requestSize = 1080, placeholder = false)
        }
    }

    outgoing?.let { Frame(it, { outgoingX.value }) }
    Frame(
        item = shown,
        offset = { currentX.value },
        modifier = Modifier.draggable(
            state = rememberDraggableState { delta ->
                scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    val next = currentX.value + delta
                    val blocked = (next < 0f && !canNext) || (next > 0f && !canPrevious)
                    currentX.snapTo(if (blocked) currentX.value + delta * 0.3f else next)
                }
            },
            orientation = Orientation.Horizontal,
            enabled = p == 1f && q == 0f,
            onDragStopped = { velocity ->
                val threshold = fullArt.width * 0.22f
                val before = latestTrack
                val goNext = (currentX.value < -threshold || velocity < -900f) && canNext && velocity < 600f
                val goPrevious = (currentX.value > threshold || velocity > 900f) && canPrevious && velocity > -600f
                if (goNext || goPrevious) {
                    haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                    SkipHint.mark(if (goNext) 1 else -1)
                    if (goNext) onNext() else onPrevious()
                    val changed = withTimeoutOrNull(800) { snapshotFlow { latestTrack }.first { it != before } }
                    if (changed == null) currentX.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 340f))
                } else {
                    currentX.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 340f), velocity)
                }
            },
        ),
    )
}

@Composable
internal fun SkipButton(forward: Boolean, label: String, enabled: Boolean, size: Dp, iconSize: Dp, onClick: () -> Unit) {
    val scope = rememberCoroutineScope()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(
        targetValue = if (pressed && enabled) 1f else 0f,
        animationSpec = if (pressed) spring(dampingRatio = 1f, stiffness = 2400f) else spring(dampingRatio = 1f, stiffness = 380f),
        label = "skipPress",
    )
    val shift = remember { Animatable(1f) }
    val tint = if (enabled) Color.White else Color(0x59FFFFFF)
    Box(
        Modifier.size(size).pressable(scale = 0.9f, haptic = enabled, label = label, source = interaction) {
            if (enabled) {
                onClick()
                scope.launch {
                    shift.snapTo(0f)
                    shift.animateTo(1f, spring(dampingRatio = 1f, stiffness = 300f))
                }
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(size * 0.86f)
                .graphicsLayer {
                    alpha = press
                    scaleX = 0.7f + 0.3f * press
                    scaleY = scaleX
                }
                .clip(CircleShape)
                .background(Color(0x24FFFFFF)),
        )
        Canvas(Modifier.size(iconSize)) {
            val unit = this.size.minDimension / 24f
            val t = shift.value
            val path = Path()
            fun triangle(left: Float, scale: Float, alpha: Float) {
                if (scale <= 0.02f) return
                val centerX = left + 3.6f
                val half = 4.6f * scale
                val width = 7.2f * scale
                path.rewind()
                path.moveTo((centerX - width / 2f) * unit, (12f - half) * unit)
                path.lineTo((centerX + width / 2f) * unit, 12f * unit)
                path.lineTo((centerX - width / 2f) * unit, (12f + half) * unit)
                path.close()
                val color = tint.copy(alpha = tint.alpha * alpha)
                drawPath(path, color)
                drawPath(path, color, style = Stroke(width = 1.8f * unit * scale, join = StrokeJoin.Round))
            }
            withTransform({ if (!forward) scale(-1f, 1f, center) }) {
                triangle(4.2f - 8.6f + 8.6f * t, t, t)
                triangle(4.2f + 8.6f * t, 1f, 1f)
                triangle(12.8f + 8.6f * t, 1f - t, 1f - t)
            }
        }
    }
}
