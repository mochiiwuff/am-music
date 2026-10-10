package app.ammusic.ui.player

import app.ammusic.youtube.tr

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import app.ammusic.ui.components.AmIcon
import app.ammusic.ui.components.AmIcons
import app.ammusic.ui.components.pressable
import app.ammusic.youtube.model.thumbnailAt
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Immutable
data class PlayerColors(val base: Color, val glow: Color, val shade: Color)

private val DefaultColors = PlayerColors(Color(0xFF17151A), Color(0xFF4A3A46), Color(0xFF2B2630))

private fun tone(rgb: Int, lightness: Float): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(rgb, hsl)
    if (hsl[1] > 0.08f) hsl[1] = hsl[1].coerceIn(0.35f, 0.8f)
    hsl[2] = lightness
    return Color(ColorUtils.HSLToColor(hsl))
}

@Composable
fun rememberPlayerColors(artworkUrl: String?, stiffness: Float = 18f): PlayerColors {
    val context = LocalContext.current
    var target by remember { mutableStateOf(DefaultColors) }
    LaunchedEffect(artworkUrl) {
        if (artworkUrl == null) return@LaunchedEffect
        val request = ImageRequest.Builder(context).data(artworkUrl.thumbnailAt(120)).size(120).allowHardware(false).build()
        val bitmap = (SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image?.toBitmap() ?: return@LaunchedEffect
        target = withContext(Dispatchers.Default) {
            val palette = Palette.from(bitmap).generate()
            val dominant = palette.getDominantColor(DefaultColors.glow.toArgb())
            PlayerColors(
                base = tone(palette.getDarkMutedColor(dominant), 0.12f),
                glow = tone(palette.getVibrantColor(dominant), 0.40f),
                shade = tone(palette.getMutedColor(palette.getLightVibrantColor(dominant)), 0.28f),
            )
        }
    }
    val slow = spring<Color>(stiffness = stiffness)
    val base by animateColorAsState(target.base, slow, label = "base")
    val glow by animateColorAsState(target.glow, slow, label = "glow")
    val shade by animateColorAsState(target.shade, slow, label = "shade")
    return PlayerColors(base, glow, shade)
}

@Composable
fun PlayerBackdrop(colors: PlayerColors, modifier: Modifier = Modifier) {
    val drift = rememberInfiniteTransition(label = "backdrop")
    val a by drift.animateFloat(0f, 1f, infiniteRepeatable(tween(19_000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "a")
    val b by drift.animateFloat(0f, 1f, infiniteRepeatable(tween(27_000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b")
    val current by rememberUpdatedState(colors)
    Canvas(modifier.fillMaxSize()) {
        val c = current
        drawRect(c.base)
        val radius = size.maxDimension * 0.75f
        val first = Offset(lerp(size.width * 0.1f, size.width * 0.9f, a), lerp(size.height * 0.05f, size.height * 0.35f, b))
        drawRect(Brush.radialGradient(listOf(c.glow, c.glow.copy(alpha = 0f)), first, radius))
        val second = Offset(lerp(size.width * 0.95f, size.width * 0.05f, b), lerp(size.height * 0.95f, size.height * 0.55f, a))
        drawRect(Brush.radialGradient(listOf(c.shade, c.shade.copy(alpha = 0f)), second, radius))
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color(0x52000000))))
    }
}

private val PauseShape = floatArrayOf(
    0.24f, 0.18f, 0.42f, 0.18f, 0.42f, 0.82f, 0.24f, 0.82f,
    0.58f, 0.18f, 0.76f, 0.18f, 0.76f, 0.82f, 0.58f, 0.82f,
)
private val PlayShape = floatArrayOf(
    0.28f, 0.16f, 0.56f, 0.33f, 0.56f, 0.67f, 0.28f, 0.84f,
    0.56f, 0.33f, 0.84f, 0.50f, 0.84f, 0.50f, 0.56f, 0.67f,
)

@Composable
fun PlayPauseIcon(playing: Boolean, color: Color, size: Dp, modifier: Modifier = Modifier) {
    val morph by animateFloatAsState(if (playing) 0f else 1f, spring(dampingRatio = 0.7f, stiffness = 380f), label = "playPause")
    val path = remember { Path() }
    Canvas(modifier.size(size)) {
        val side = this.size.minDimension
        path.rewind()
        for (quad in 0 until 2) {
            for (point in 0 until 4) {
                val i = (quad * 4 + point) * 2
                val x = lerp(PauseShape[i], PlayShape[i], morph) * side
                val y = lerp(PauseShape[i + 1], PlayShape[i + 1], morph) * side
                if (point == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
        }
        drawPath(path, color)
        drawPath(path, color, style = Stroke(width = side * 0.07f, join = StrokeJoin.Round))
    }
}

@Composable
fun HeartButton(liked: Boolean, onToggle: () -> Unit, tint: Color, likedTint: Color, modifier: Modifier = Modifier) {
    val scale = remember { Animatable(1f) }
    LaunchedEffect(liked) {
        if (liked) {
            scale.snapTo(0.6f)
            scale.animateTo(1f, spring(dampingRatio = 0.32f, stiffness = 420f))
        }
    }
    Box(
        modifier.size(48.dp).pressable(scale = 0.85f, haptic = true, label = if (liked) tr("Ne plus aimer", "Unlike") else tr("J'aime", "Like"), onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        AmIcon(
            icon = if (liked) AmIcons.HeartFilled else AmIcons.Heart,
            tint = if (liked) likedTint else tint,
            size = 26.dp,
            modifier = Modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value },
        )
    }
}

@Composable
fun ThinSlider(
    value: Float,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onRelease: (Float) -> Unit = {},
) {
    val haptics = LocalHapticFeedback.current
    var dragging by remember { mutableStateOf(false) }
    var local by remember { mutableFloatStateOf(value) }
    val change by rememberUpdatedState(onChange)
    val release by rememberUpdatedState(onRelease)
    val thickness by animateDpAsState(if (dragging) 12.dp else 5.dp, spring(dampingRatio = 0.6f, stiffness = 500f), label = "thickness")
    val fill by animateColorAsState(if (dragging) Color.White else Color(0xD9FFFFFF), label = "fill")
    val shown = if (dragging) local else value.coerceIn(0f, 1f)

    Canvas(
        modifier.fillMaxWidth().height(32.dp).pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown()
                dragging = true
                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                local = (down.position.x / size.width).coerceIn(0f, 1f)
                change(local)
                drag(down.id) { event ->
                    local = (event.position.x / size.width).coerceIn(0f, 1f)
                    change(local)
                    event.consume()
                }
                dragging = false
                release(local)
            }
        },
    ) {
        val height = thickness.toPx()
        val top = (size.height - height) / 2f
        val radius = CornerRadius(height / 2f)
        drawRoundRect(Color(0x38FFFFFF), Offset(0f, top), Size(size.width, height), radius)
        drawRoundRect(fill, Offset(0f, top), Size((size.width * shown).coerceAtLeast(height), height), radius)
    }
}

@Composable
fun rememberSystemVolume(): Pair<State<Float>, (Float) -> Unit> {
    val context = LocalContext.current
    val audio = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val max = remember { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    val volume = remember { mutableFloatStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC) / max.toFloat()) }
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                volume.floatValue = audio.getStreamVolume(AudioManager.STREAM_MUSIC) / max.toFloat()
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter("android.media.VOLUME_CHANGED_ACTION"), ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }
    val set: (Float) -> Unit = { fraction ->
        volume.floatValue = fraction
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, (fraction * max).roundToInt(), 0)
    }
    return volume to set
}

fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}
