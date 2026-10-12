package app.ammusic.ui.components

import app.ammusic.youtube.tr

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.ammusic.ui.theme.AmTheme
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur

val LocalHazeState = staticCompositionLocalOf<HazeState> { error("HazeState manquant") }

val LocalBottomInset = compositionLocalOf { 0.dp }

@Composable
fun AmText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = AmTheme.type.body,
    color: Color = AmTheme.colors.text,
    maxLines: Int = Int.MAX_VALUE,
    textAlign: TextAlign = TextAlign.Unspecified,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = if (textAlign == TextAlign.Unspecified) style else style.copy(textAlign = textAlign),
        color = { color },
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun AmIcon(icon: ImageVector, modifier: Modifier = Modifier, tint: Color = AmTheme.colors.text, size: Dp = 22.dp) {
    Image(icon, contentDescription = null, modifier = modifier.size(size), colorFilter = ColorFilter.tint(tint))
}

@Composable
fun IconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = AmTheme.colors.text,
    iconSize: Dp = 22.dp,
) {
    Box(modifier.size(48.dp).pressable(scale = 0.88f, label = label, onClick = onClick), contentAlignment = Alignment.Center) {
        AmIcon(icon, tint = tint, size = iconSize)
    }
}

fun Modifier.pressable(
    scale: Float = 0.96f,
    haptic: Boolean = false,
    label: String? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val current by animateFloatAsState(
        targetValue = if (pressed) scale else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 520f),
        label = "press",
    )
    val haptics = LocalHapticFeedback.current
    val click = {
        if (haptic) haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
        onClick()
    }
    val scaled = graphicsLayer { scaleX = current; scaleY = current }
    if (onLongClick == null) {
        scaled.clickable(interactionSource = interaction, indication = null, onClickLabel = label, onClick = click)
    } else {
        scaled.combinedClickable(
            interactionSource = interaction,
            indication = null,
            onClickLabel = label,
            onLongClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onLongClick()
            },
            onClick = click,
        )
    }
}

fun Modifier.cascade(index: Int, enabled: Boolean): Modifier = composed {
    val animate = remember { enabled }
    val progress = remember { Animatable(if (animate) 0f else 1f) }
    if (animate) {
        LaunchedEffect(Unit) {
            delay(index.coerceAtMost(12) * 26L)
            progress.animateTo(1f, spring(dampingRatio = 0.9f, stiffness = 240f))
        }
    }
    graphicsLayer {
        alpha = progress.value.coerceIn(0f, 1f)
        translationY = (1f - progress.value) * 16.dp.toPx()
    }
}

@Composable
fun Modifier.glass(shape: Shape, tintAlpha: Float = 0.6f, bordered: Boolean = true): Modifier {
    val colors = AmTheme.colors
    val style = HazeBlurStyle {
        blurRadius(28.dp)
        backgroundColor(colors.glass)
        colorEffects(listOf(HazeColorEffect.tint(colors.glass.copy(alpha = tintAlpha))))
        fallbackColorEffect(HazeColorEffect.tint(colors.glass.copy(alpha = 0.96f)))
    }
    val blurred = clip(shape).hazeBlur(HazeInput.Sources(LocalHazeState.current), style)
    return if (bordered) blurred.border(0.5.dp, colors.hairline, shape) else blurred
}

fun Modifier.slidingPill(position: () -> Float, count: Int, color: Color, corner: Dp, inset: Dp = 4.dp): Modifier =
    drawBehind {
        val cell = size.width / count
        val pad = inset.toPx()
        drawRoundRect(
            color = color,
            topLeft = Offset(position() * cell + pad, pad),
            size = Size(cell - 2 * pad, size.height - 2 * pad),
            cornerRadius = CornerRadius(corner.toPx()),
        )
    }

private fun artworkColors(seed: Int): List<Color> {
    val hue = (((seed * 47) % 360) + 360) % 360f
    return listOf(
        Color.hsl(hue, 0.62f, 0.44f),
        Color.hsl((hue + 38f) % 360f, 0.70f, 0.20f),
        Color.hsl((hue + 318f) % 360f, 0.85f, 0.66f),
    )
}

@Composable
fun Artwork(seed: Int, modifier: Modifier = Modifier, corner: Dp = 10.dp, elevated: Boolean = false) {
    val colors = remember(seed) { artworkColors(seed) }
    val shape = RoundedCornerShape(corner)
    val shadow = if (elevated) {
        Modifier.shadow(14.dp, shape, ambientColor = Color(0x40000000), spotColor = Color(0x59000000))
    } else {
        Modifier
    }
    Box(
        modifier
            .then(shadow)
            .clip(shape)
            .drawBehind {
                drawRect(Brush.linearGradient(listOf(colors[0], colors[1]), Offset.Zero, Offset(size.width, size.height)))
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(colors[2].copy(alpha = 0.85f), Color.Transparent),
                        center = Offset(size.width * 0.82f, size.height * 0.12f),
                        radius = size.maxDimension * 0.8f,
                    ),
                )
            },
    )
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, top: Dp = 32.dp, onMore: (() -> Unit)? = null) {
    val more = if (onMore != null) Modifier.pressable(scale = 0.98f, label = tr("Tout voir", "See all"), onClick = onMore) else Modifier
    Row(
        modifier.fillMaxWidth().padding(top = top).then(more).padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AmText(title, style = AmTheme.type.section, maxLines = 2, modifier = Modifier.weight(1f, fill = false))
        if (onMore != null) {
            AmIcon(AmIcons.ChevronRight, tint = AmTheme.colors.textTertiary, size = 20.dp, modifier = Modifier.padding(start = 2.dp, top = 2.dp))
        }
    }
}

fun Modifier.hairlineBelow(color: Color, inset: Dp): Modifier = drawBehind {
    val y = size.height - 0.5.dp.toPx()
    drawLine(color, Offset(inset.toPx(), y), Offset(size.width, y), 1.dp.toPx())
}
