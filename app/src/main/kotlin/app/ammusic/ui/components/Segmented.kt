package app.ammusic.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.ammusic.ui.theme.AmTheme

@Composable
fun SegmentedControl(options: List<String>, selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    val colors = AmTheme.colors
    val position = animateFloatAsState(selected.toFloat(), spring(dampingRatio = 0.8f, stiffness = 460f), label = "segment")
    Row(
        modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(colors.text.copy(alpha = 0.08f))
            .slidingPill({ position.value }, options.size, if (colors.isDark) Color.White.copy(alpha = 0.17f) else Color.White, corner = 8.dp, inset = 2.dp),
    ) {
        options.forEachIndexed { index, option ->
            Box(
                Modifier.weight(1f).fillMaxHeight().pressable(scale = 0.96f, haptic = true, label = option) { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                AmText(
                    text = option,
                    style = AmTheme.type.footnote.copy(fontWeight = if (index == selected) FontWeight.SemiBold else FontWeight.Medium),
                    color = if (index == selected) colors.text else colors.textSecondary,
                    maxLines = 1,
                )
            }
        }
    }
}
