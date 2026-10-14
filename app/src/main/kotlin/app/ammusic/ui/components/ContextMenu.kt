package app.ammusic.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import app.ammusic.ui.theme.AmTheme

class Anchor {
    var bounds: Rect? = null
}

class PreviewRequest(val menu: MenuRequest, val from: Rect?)

@Composable
fun ContextMenu(request: PreviewRequest?, onDismiss: () -> Unit) {
    val colors = AmTheme.colors
    var shown by remember { mutableStateOf<PreviewRequest?>(null) }
    val progress = remember { Animatable(0f) }

    LaunchedEffect(request) {
        if (request != null) {
            shown = request
            progress.snapTo(0f)
            progress.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 380f))
        } else {
            progress.animateTo(0f, spring(dampingRatio = 1f, stiffness = 520f))
            shown = null
        }
    }

    val current = shown ?: return
    var target by remember(current) { mutableStateOf<Rect?>(null) }
    BackHandler(enabled = request != null, onBack = onDismiss)

    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { onDismiss() } }) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = progress.value.coerceIn(0f, 1f) }
                .glass(RectangleShape, tintAlpha = 0.45f, bordered = false)
                .drawBehind { drawRect(Color.Black, alpha = if (colors.isDark) 0.28f else 0.08f) },
        )
        BoxWithConstraints(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            val art = minOf(maxWidth - 40.dp, maxHeight * 0.32f, 248.dp)
            val menuWidth = minOf(maxWidth, 264.dp)
            Column(Modifier.heightIn(max = maxHeight), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Column(
                    Modifier
                        .width(art)
                        .onGloballyPositioned { target = it.boundsInRoot() }
                        .graphicsLayer {
                            val p = progress.value
                            val from = current.from
                            val to = target
                            if (from != null && to != null && to.width > 0f) {
                                transformOrigin = TransformOrigin(0f, 0f)
                                val scale = lerp(from.width / to.width, 1f, p)
                                scaleX = scale
                                scaleY = scale
                                translationX = (from.left - to.left) * (1f - p)
                                translationY = (from.top - to.top) * (1f - p)
                                alpha = (p * 3f).coerceIn(0f, 1f)
                            } else {
                                val scale = 0.86f + 0.14f * p
                                scaleX = scale
                                scaleY = scale
                                alpha = p.coerceIn(0f, 1f)
                            }
                        }
                        .shadow(28.dp, RoundedCornerShape(16.dp), ambientColor = Color(0x40000000), spotColor = Color(0x66000000))
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.surfaceHigh)
                        .pointerInput(Unit) { detectTapGestures {} },
                ) {
                    Cover(current.menu.thumbnail, Modifier.fillMaxWidth().aspectRatio(1f), corner = 0.dp, requestSize = 544, placeholder = false)
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
                        AmText(current.menu.title, style = AmTheme.type.headline, maxLines = 1)
                        AmText(current.menu.subtitle, style = AmTheme.type.subhead, color = colors.textSecondary, maxLines = 1)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Column(
                    Modifier
                        .weight(1f, fill = false)
                        .width(menuWidth)
                        .graphicsLayer {
                            val reveal = ((progress.value - 0.15f) / 0.85f).coerceIn(0f, 1.1f)
                            alpha = reveal.coerceIn(0f, 1f)
                            val scale = 0.7f + 0.3f * reveal
                            scaleX = scale
                            scaleY = scale
                            transformOrigin = TransformOrigin(0.5f, 0f)
                        }
                        .shadow(20.dp, RoundedCornerShape(14.dp), ambientColor = Color(0x33000000), spotColor = Color(0x4D000000))
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.surfaceHigh)
                        .verticalScroll(rememberScrollState()),
                ) {
                    current.menu.actions.forEachIndexed { index, action ->
                        val tint = if (action.destructive) colors.accent else colors.text
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .pressable(scale = 0.98f, haptic = true, label = action.label) {
                                    onDismiss()
                                    action.onClick()
                                }
                                .hairlineBelow(if (index < current.menu.actions.lastIndex) colors.hairline else Color.Transparent, 0.dp)
                                .height(46.dp)
                                .padding(start = 16.dp, end = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AmText(action.label, color = tint, maxLines = 1, modifier = Modifier.weight(1f).padding(end = 12.dp))
                            AmIcon(action.icon, tint = tint, size = 20.dp)
                        }
                    }
                }
            }
        }
    }
}

fun Modifier.anchor(anchor: Anchor): Modifier = onGloballyPositioned { anchor.bounds = it.boundsInRoot() }
