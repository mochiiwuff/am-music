package app.ammusic.ui.player

import android.app.Activity
import androidx.activity.compose.PredictiveBackHandler
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.core.view.WindowCompat
import app.ammusic.playback.PlayerState
import app.ammusic.playback.RepeatMode
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
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private val SheetSpring = spring<Float>(dampingRatio = 0.9f, stiffness = 260f)
private val PanelSpring = spring<Float>(dampingRatio = 0.88f, stiffness = 300f)
private val Soft = Color(0x99FFFFFF)

/** Ouverture du lecteur : 0 = mini-lecteur, 1 = plein écran. Partagé entre le mini-lecteur et [PlayerSheet]. */
@Stable
class PlayerSheetState(private val scope: CoroutineScope) {
    val progress = Animatable(0f)
    var miniBounds by mutableStateOf(Rect.Zero)
    var miniArtBounds by mutableStateOf(Rect.Zero)
    var travelPx = 1f
    val isOpen by derivedStateOf { progress.value > 0f }

    /** Suit le doigt : [upPx] positif quand il monte. */
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

/** Lecteur plein écran. Il grandit depuis le mini-lecteur et la pochette voyage de l'un à l'autre. */
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
            playing = state.isPlaying || state.isLoading,
            fullArt = fullArt,
            queueArt = queueArt,
            screenWidth = width,
            canSkip = state.hasNext,
            onNext = player::next,
            onPrevious = player::previous,
        )
    }
}

/** Sur le fond sombre du lecteur, les icônes de la barre d'état doivent rester claires, même en thème clair. */
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
    val active = state.isPlaying || state.isLoading
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 28.dp)) {
        Box(
            Modifier.fillMaxWidth().height(48.dp).pressable(label = "Réduire le lecteur", onClick = onCollapse),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(width = 38.dp, height = 5.dp).clip(CircleShape).background(Color(0x66FFFFFF)))
        }
        Spacer(Modifier.height(12.dp + artSpace))

        // Les commandes s'effacent quand les paroles prennent leur place.
        Column(
            Modifier.weight(1f).fillMaxWidth().graphicsLayer { alpha = 1f - fade().coerceIn(0f, 1f) },
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    AmText(track.title, style = AmTheme.type.section, color = Color.White, maxLines = 1)
                    AmText(
                        text = if (state.failed) "Lecture impossible, nouvel essai…" else track.artists,
                        color = Color(0xB3FFFFFF),
                        maxLines = 1,
                    )
                }
                HeartButton(state.liked, player::toggleLike, tint = Color.White, likedTint = accent)
            }

            ProgressSection(track.id, state.isPlaying, state.durationMs, player)

            Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly, Alignment.CenterVertically) {
                BigButton(AmIcons.Previous, "Titre précédent", player::previous)
                Box(
                    Modifier.size(84.dp).pressable(scale = 0.86f, haptic = true, label = if (active) "Pause" else "Lecture", onClick = player::toggle),
                    contentAlignment = Alignment.Center,
                ) {
                    PlayPauseIcon(playing = active, color = Color.White, size = 60.dp)
                }
                BigButton(AmIcons.Next, "Titre suivant", player::next, enabled = state.hasNext)
            }

            val (volume, setVolume) = rememberSystemVolume()
            Row(verticalAlignment = Alignment.CenterVertically) {
                AmIcon(AmIcons.VolumeLow, tint = Soft, size = 20.dp)
                ThinSlider(volume.value, onChange = setVolume, modifier = Modifier.weight(1f).padding(horizontal = 10.dp))
                AmIcon(AmIcons.VolumeHigh, tint = Soft, size = 20.dp)
            }

            Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly, Alignment.CenterVertically) {
                ToggleButton(AmIcons.Shuffle, "Lecture aléatoire", state.shuffle, player::toggleShuffle)
                ToggleButton(AmIcons.Lyrics, "Paroles", false, onLyrics)
                ToggleButton(AmIcons.Queue, "File d'attente", false, onQueue)
                ToggleButton(
                    icon = if (state.repeat == RepeatMode.One) AmIcons.RepeatOne else AmIcons.Repeat,
                    label = "Répétition",
                    active = state.repeat != RepeatMode.Off,
                    onClick = player::cycleRepeat,
                )
            }
        }
    }
}

/** Isolé du reste : cette partie se redessine quatre fois par seconde pendant la lecture. */
@Composable
private fun ProgressSection(trackId: String, playing: Boolean, durationMs: Long, player: PlayerViewModel) {
    var position by remember(trackId) { mutableLongStateOf(player.positionMs) }
    var seeking by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(trackId, playing) {
        while (true) {
            position = player.positionMs
            delay(250)
        }
    }
    val shown = seeking?.let { (it * durationMs).toLong() } ?: position.coerceAtMost(durationMs.coerceAtLeast(position))
    val times = AmTheme.type.caption.copy(fontFeatureSettings = "tnum")
    Column {
        ThinSlider(
            value = if (durationMs > 0) shown / durationMs.toFloat() else 0f,
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
private fun BigButton(icon: ImageVector, label: String, onClick: () -> Unit, enabled: Boolean = true) {
    Box(Modifier.size(72.dp).pressable(scale = 0.82f, haptic = true, label = label, onClick = onClick), contentAlignment = Alignment.Center) {
        AmIcon(icon, tint = if (enabled) Color.White else Color(0x59FFFFFF), size = 42.dp)
    }
}

@Composable
private fun ToggleButton(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    val glow by animateFloatAsState(if (active) 1f else 0f, spring(dampingRatio = 0.7f, stiffness = 400f), label = "toggle")
    Box(Modifier.size(56.dp).pressable(scale = 0.86f, haptic = true, label = label, onClick = onClick), contentAlignment = Alignment.Center) {
        Box(Modifier.size(44.dp).graphicsLayer { alpha = glow; scaleX = 0.7f + 0.3f * glow; scaleY = 0.7f + 0.3f * glow }.clip(CircleShape).background(Color(0x33FFFFFF)))
        AmIcon(icon, tint = if (active) Color.White else Soft, size = 24.dp)
    }
}

/**
 * La pochette, dessinée au-dessus de tout : elle passe du rond du mini-lecteur au grand carré,
 * rétrécit en pause, se range dans l'en-tête quand la file s'ouvre, et se balaie pour changer de titre.
 */
@Composable
private fun SharedArtwork(
    sheet: PlayerSheetState,
    queue: () -> Float,
    track: YtTrack,
    playing: Boolean,
    fullArt: Rect,
    queueArt: Rect,
    screenWidth: Float,
    canSkip: Boolean,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val breathe by animateFloatAsState(if (playing) 1f else 0.84f, spring(dampingRatio = 0.62f, stiffness = 240f), label = "breathe")
    val swipe = remember { Animatable(0f) }

    val p = sheet.progress.value.coerceIn(0f, 1f)
    val q = queue().coerceIn(0f, 1f)
    val mini = sheet.miniArtBounds
    val resting = Rect(fullArt.center - Offset(fullArt.width, fullArt.height) * (breathe / 2f), fullArt.size * breathe)
    val rect = lerp(mini, lerp(resting, queueArt, q), p)
    val corner = with(density) { lerp(mini.width / 2f, lerp(18.dp.toPx(), 10.dp.toPx(), q), p) }
    val swipeSpring = spring<Float>(dampingRatio = 0.8f, stiffness = 340f)

    Box(
        Modifier
            .offset { IntOffset((rect.left + swipe.value).roundToInt(), rect.top.roundToInt()) }
            .requiredSize(with(density) { rect.width.toDp() }, with(density) { rect.height.toDp() })
            .graphicsLayer {
                shadowElevation = 28.dp.toPx() * p * (1f - q)
                shape = RoundedCornerShape(corner)
                clip = true
                alpha = 1f - (abs(swipe.value) / screenWidth).coerceIn(0f, 1f) * 0.7f
            }
            .draggable(
                state = rememberDraggableState { delta -> scope.launch { swipe.snapTo(swipe.value + delta) } },
                orientation = Orientation.Horizontal,
                enabled = p == 1f && q == 0f,
                onDragStopped = { velocity ->
                    val threshold = fullArt.width * 0.28f
                    when {
                        (swipe.value < -threshold || velocity < -1400f) && canSkip -> {
                            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                            swipe.animateTo(-screenWidth, swipeSpring, velocity)
                            onNext()
                            swipe.snapTo(screenWidth)
                            swipe.animateTo(0f, swipeSpring)
                        }
                        swipe.value > threshold || velocity > 1400f -> {
                            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                            swipe.animateTo(screenWidth, swipeSpring, velocity)
                            onPrevious()
                            swipe.snapTo(-screenWidth)
                            swipe.animateTo(0f, swipeSpring)
                        }
                        else -> swipe.animateTo(0f, swipeSpring, velocity)
                    }
                },
            ),
    ) {
        // La petite image est déjà en mémoire (mini-lecteur) : elle évite un trou pendant que la grande charge.
        Cover(track.thumbnail, Modifier.fillMaxSize(), corner = 0.dp, requestSize = 160)
        Cover(track.thumbnail, Modifier.fillMaxSize(), corner = 0.dp, requestSize = 1080, placeholder = false)
    }
}
