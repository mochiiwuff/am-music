package app.ammusic.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.ammusic.R

enum class ThemeMode { System, Dark, Light }

/** Couleurs d'accent proposées dans les réglages. La première est celle par défaut. */
val AccentChoices = listOf(
    Color(0xFFFF3B5C),
    Color(0xFFFF7A3D),
    Color(0xFFF2B63C),
    Color(0xFF3DD68C),
    Color(0xFF4C9BFF),
    Color(0xFFA67BFF),
)

@Immutable
data class AmColors(
    val background: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val text: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val hairline: Color,
    val accent: Color,
    val glass: Color,
    val isDark: Boolean,
)

private fun darkColors(accent: Color) = AmColors(
    background = Color(0xFF0B0A0C),
    surface = Color(0xFF18161A),
    surfaceHigh = Color(0xFF232026),
    text = Color(0xFFF6F3F4),
    textSecondary = Color(0xFFA7A2AA),
    textTertiary = Color(0xFF6F6A73),
    hairline = Color(0x14FFFFFF),
    accent = accent,
    glass = Color(0xFF1C1A1E),
    isDark = true,
)

private fun lightColors(accent: Color) = AmColors(
    background = Color(0xFFFBFAFA),
    surface = Color(0xFFF1EFF0),
    surfaceHigh = Color(0xFFE6E3E6),
    text = Color(0xFF151316),
    textSecondary = Color(0xFF6A656D),
    textTertiary = Color(0xFF9C97A0),
    hairline = Color(0x14000000),
    accent = accent,
    glass = Color(0xFFF7F5F6),
    isDark = false,
)

private fun inter(opticalSize: Float) = FontFamily(
    listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold).map { weight ->
        Font(
            resId = R.font.inter,
            weight = weight,
            variationSettings = FontVariation.Settings(
                FontVariation.weight(weight.weight),
                FontVariation.Setting("opsz", opticalSize),
            ),
        )
    },
)

private val InterDisplay = inter(32f)
private val InterText = inter(14f)

@Immutable
data class AmType(
    val largeTitle: TextStyle,
    val title: TextStyle,
    val section: TextStyle,
    val headline: TextStyle,
    val body: TextStyle,
    val subhead: TextStyle,
    val footnote: TextStyle,
    val caption: TextStyle,
)

private val Type = AmType(
    largeTitle = TextStyle(fontFamily = InterDisplay, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.03).em),
    title = TextStyle(fontFamily = InterDisplay, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 33.sp, letterSpacing = (-0.03).em),
    section = TextStyle(fontFamily = InterDisplay, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 27.sp, letterSpacing = (-0.025).em),
    headline = TextStyle(fontFamily = InterText, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = (-0.02).em),
    body = TextStyle(fontFamily = InterText, fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 23.sp, letterSpacing = (-0.015).em),
    subhead = TextStyle(fontFamily = InterText, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = (-0.01).em),
    footnote = TextStyle(fontFamily = InterText, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 17.sp, letterSpacing = (-0.005).em),
    caption = TextStyle(fontFamily = InterText, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.01.em),
)

private val LocalColors = staticCompositionLocalOf { darkColors(AccentChoices.first()) }

object AmTheme {
    val colors: AmColors
        @Composable @ReadOnlyComposable get() = LocalColors.current
    val type: AmType get() = Type
}

@Composable
private fun animated(target: Color): Color {
    val value by animateColorAsState(target, spring(stiffness = 220f), label = "themeColor")
    return value
}

@Composable
fun AmTheme(dark: Boolean, accent: Color, content: @Composable () -> Unit) {
    val target = if (dark) darkColors(accent) else lightColors(accent)
    val colors = AmColors(
        background = animated(target.background),
        surface = animated(target.surface),
        surfaceHigh = animated(target.surfaceHigh),
        text = animated(target.text),
        textSecondary = animated(target.textSecondary),
        textTertiary = animated(target.textTertiary),
        hairline = animated(target.hairline),
        accent = animated(target.accent),
        glass = animated(target.glass),
        isDark = dark,
    )
    CompositionLocalProvider(LocalColors provides colors, content = content)
}
