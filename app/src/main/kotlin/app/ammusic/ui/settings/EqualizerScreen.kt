package app.ammusic.ui.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ammusic.data.EqualizerBandsHz
import app.ammusic.data.EqualizerPresets
import app.ammusic.data.EqualizerRangeDb
import app.ammusic.data.UserSettings
import app.ammusic.data.equalizerGains
import app.ammusic.ui.components.AmIcon
import app.ammusic.ui.components.AmIcons
import app.ammusic.ui.components.AmText
import app.ammusic.ui.components.IconButton
import app.ammusic.ui.components.LargeTitleScreen
import app.ammusic.ui.components.LocalActions
import app.ammusic.ui.components.hairlineBelow
import app.ammusic.ui.components.pressable
import app.ammusic.ui.theme.AmTheme
import app.ammusic.youtube.tr
import kotlin.math.roundToInt

@Composable
fun EqualizerScreen() {
    val colors = AmTheme.colors
    val actions = LocalActions.current
    val settings by UserSettings.state.collectAsStateWithLifecycle()
    val gains = settings.equalizerGains()
    val shown = gains ?: List(EqualizerBandsHz.size) { 0 }
    val choices = listOf("off" to tr("Désactivé", "Off")) + EqualizerPresets.map { it.id to it.label } + ("custom" to tr("Personnalisé", "Custom"))

    LargeTitleScreen(
        title = tr("Égaliseur", "Equalizer"),
        leading = { IconButton(AmIcons.ChevronLeft, label = tr("Retour", "Back"), onClick = actions.back, tint = colors.accent, iconSize = 26.dp) },
    ) {
        item(key = "curve", contentType = "curve") {
            CurveCard(
                gains = shown,
                enabled = gains != null,
                onChange = { band, value ->
                    val base = settings.equalizerGains() ?: List(EqualizerBandsHz.size) { 0 }
                    UserSettings.update { copy(eqPreset = "custom", eqCustom = base.toMutableList().also { it[band] = value }) }
                },
            )
        }
        item(key = "presets", contentType = "presets") {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp)) {
                AmText(
                    text = tr("Réglage", "Preset").uppercase(),
                    style = AmTheme.type.footnote,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
                )
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surface)) {
                    choices.forEachIndexed { index, (id, label) ->
                        val selected = settings.eqPreset == id
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .pressable(scale = 0.99f, haptic = true, label = label) { UserSettings.update { copy(eqPreset = id) } }
                                .hairlineBelow(if (index < choices.lastIndex) colors.hairline else Color.Transparent, 16.dp)
                                .heightIn(min = 48.dp)
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AmText(label, modifier = Modifier.weight(1f), maxLines = 1)
                            val check by animateFloatAsState(if (selected) 1f else 0f, spring(dampingRatio = 1f, stiffness = 520f), label = "check")
                            AmIcon(
                                AmIcons.Check,
                                tint = colors.accent,
                                size = 20.dp,
                                modifier = Modifier.graphicsLayer {
                                    alpha = check
                                    scaleX = 0.6f + 0.4f * check
                                    scaleY = scaleX
                                },
                            )
                        }
                    }
                }
                AmText(
                    text = tr("Faites glisser les points pour créer votre propre réglage.", "Drag the points to make your own preset."),
                    style = AmTheme.type.footnote,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun CurveCard(gains: List<Int>, enabled: Boolean, onChange: (Int, Int) -> Unit) {
    val colors = AmTheme.colors
    val haptics = LocalHapticFeedback.current
    val current by rememberUpdatedState(gains)
    val change by rememberUpdatedState(onChange)
    val animated = gains.mapIndexed { index, gain ->
        animateFloatAsState(gain.toFloat(), spring(dampingRatio = 1f, stiffness = 420f), label = "band$index").value
    }
    val presence by animateFloatAsState(if (enabled) 1f else 0.35f, spring(dampingRatio = 1f, stiffness = 300f), label = "presence")
    val accent = colors.accent
    val grid = colors.hairline
    val bands = EqualizerBandsHz.size

    Column(
        Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(colors.surface)
            .padding(top = 18.dp, bottom = 14.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(220.dp).padding(horizontal = 18.dp)) {
            fun valueAt(y: Float, height: Float): Int {
                val ratio = (1f - (y / height)).coerceIn(0f, 1f)
                return (-EqualizerRangeDb + ratio * 2 * EqualizerRangeDb).roundToInt()
            }
            fun bandAt(x: Float, width: Float): Int = ((x / width) * bands).toInt().coerceIn(0, bands - 1)
            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures { point ->
                            val band = bandAt(point.x, size.width.toFloat())
                            val value = valueAt(point.y, size.height.toFloat())
                            if (value != current[band]) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                change(band, value)
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        var band = 0
                        detectDragGestures(
                            onDragStart = { point -> band = bandAt(point.x, size.width.toFloat()) },
                        ) { pointer, _ ->
                            val value = valueAt(pointer.position.y, size.height.toFloat())
                            if (value != current[band]) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                change(band, value)
                            }
                        }
                    },
            ) {
                val column = size.width / bands
                val zero = size.height / 2f
                drawLine(grid, Offset(0f, zero), Offset(size.width, zero), 1.dp.toPx())
                listOf(0.25f, 0.75f).forEach { drawLine(grid.copy(alpha = grid.alpha * 0.5f), Offset(0f, size.height * it), Offset(size.width, size.height * it), 1.dp.toPx()) }
                val points = animated.mapIndexed { index, gain ->
                    Offset(column * (index + 0.5f), zero - (gain / EqualizerRangeDb) * (size.height / 2f - 10.dp.toPx()))
                }
                points.forEach { point ->
                    drawLine(grid, Offset(point.x, 0f), Offset(point.x, size.height), 1.5.dp.toPx(), cap = StrokeCap.Round)
                }
                val curve = Path().apply {
                    moveTo(0f, points.first().y)
                    lineTo(points.first().x, points.first().y)
                    for (index in 1 until points.size) {
                        val from = points[index - 1]
                        val to = points[index]
                        val middle = (from.x + to.x) / 2f
                        cubicTo(middle, from.y, middle, to.y, to.x, to.y)
                    }
                    lineTo(size.width, points.last().y)
                }
                val fill = Path().apply {
                    addPath(curve)
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                }
                drawPath(fill, Brush.verticalGradient(listOf(accent.copy(alpha = 0.32f * presence), accent.copy(alpha = 0f))))
                drawPath(curve, accent.copy(alpha = presence), style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                points.forEach { point ->
                    drawCircle(Color.White.copy(alpha = presence), 9.dp.toPx(), point)
                    drawCircle(accent.copy(alpha = presence), 5.dp.toPx(), point)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)) {
            EqualizerBandsHz.forEachIndexed { index, hz ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    AmText(
                        text = (if (gains[index] > 0) "+" else "") + gains[index] + " dB",
                        style = AmTheme.type.footnote.copy(fontFeatureSettings = "tnum"),
                        color = if (enabled) colors.text else colors.textTertiary,
                        textAlign = TextAlign.Center,
                    )
                    AmText(
                        text = if (hz >= 1000) "${hz / 1000}${if (hz % 1000 >= 100) "," + (hz % 1000) / 100 else ""} kHz" else "$hz Hz",
                        style = AmTheme.type.caption.copy(fontSize = 11.sp),
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
