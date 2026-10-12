package app.ammusic.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.ammusic.data.library.LibraryNotice
import app.ammusic.ui.theme.AmTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest

@Composable
fun NoticeHost(notices: Flow<LibraryNotice>, modifier: Modifier = Modifier) {
    val colors = AmTheme.colors
    val haptics = LocalHapticFeedback.current
    var current by remember { mutableStateOf<LibraryNotice?>(null) }
    val progress = remember { Animatable(0f) }

    LaunchedEffect(notices) {
        notices.collectLatest { notice ->
            current = notice
            haptics.performHapticFeedback(if (notice.failed) HapticFeedbackType.LongPress else HapticFeedbackType.ContextClick)
            progress.animateTo(1f, spring(dampingRatio = 1f, stiffness = 440f))
            delay(if (notice.failed) 3_600 else 2_200)
            progress.animateTo(0f, spring(dampingRatio = 1f, stiffness = 520f))
            current = null
        }
    }

    val notice = current ?: return
    Row(
        modifier
            .statusBarsPadding()
            .padding(start = 24.dp, end = 24.dp, top = 10.dp)
            .graphicsLayer {
                val shown = progress.value
                alpha = shown.coerceIn(0f, 1f)
                translationY = (1f - shown) * -28.dp.toPx()
                scaleX = 0.9f + 0.1f * shown
                scaleY = scaleX
            }
            .shadow(18.dp, CircleShape, ambientColor = Color(0x40000000), spotColor = Color(0x59000000))
            .clip(CircleShape)
            .background(colors.surfaceHigh)
            .border(0.5.dp, colors.hairline, CircleShape)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(start = 8.dp, end = 18.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(28.dp).clip(CircleShape).background(if (notice.failed) colors.textTertiary else colors.accent),
            contentAlignment = Alignment.Center,
        ) {
            AmIcon(if (notice.failed) AmIcons.Close else AmIcons.Check, tint = Color.White, size = 16.dp)
        }
        AmText(notice.text, style = AmTheme.type.subhead, maxLines = 2, modifier = Modifier.padding(start = 10.dp))
    }
}
