package app.ammusic.data

import app.ammusic.youtube.tr
import kotlin.math.ln

val EqualizerBandsHz = listOf(60, 230, 910, 3_600, 14_000)

const val EqualizerRangeDb = 12

class EqualizerPreset(val id: String, val label: String, val gains: List<Int>)

val EqualizerPresets: List<EqualizerPreset>
    get() = listOf(
        EqualizerPreset("bass", tr("Basses renforcées", "Bass booster"), listOf(6, 4, 0, 0, 0)),
        EqualizerPreset("treble", tr("Aigus renforcés", "Treble booster"), listOf(0, 0, 0, 3, 6)),
        EqualizerPreset("vocal", tr("Voix", "Vocal booster"), listOf(-2, -1, 3, 3, 0)),
        EqualizerPreset("pop", "Pop", listOf(-1, 2, 4, 2, -1)),
        EqualizerPreset("rock", "Rock", listOf(4, 2, -1, 2, 4)),
        EqualizerPreset("electronic", tr("Électro", "Electronic"), listOf(5, 3, 0, 2, 4)),
        EqualizerPreset("acoustic", tr("Acoustique", "Acoustic"), listOf(3, 1, 1, 2, 3)),
        EqualizerPreset("late", tr("Soirée calme", "Late night"), listOf(-3, -1, 1, 0, -3)),
    )

fun Settings.equalizerGains(): List<Int>? = when (eqPreset) {
    "off" -> null
    "custom" -> eqCustom
    else -> EqualizerPresets.firstOrNull { it.id == eqPreset }?.gains
}

fun gainAt(gains: List<Int>, hz: Float): Float {
    val points = EqualizerBandsHz.map { ln(it.toFloat()) }
    val x = ln(hz.coerceAtLeast(1f))
    if (x <= points.first()) return gains.first().toFloat()
    if (x >= points.last()) return gains.last().toFloat()
    val right = points.indexOfFirst { it >= x }
    val left = right - 1
    val t = (x - points[left]) / (points[right] - points[left])
    return gains[left] + (gains[right] - gains[left]) * t
}
