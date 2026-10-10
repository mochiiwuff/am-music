package app.ammusic.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.ammusic.ui.theme.AmTheme
import app.ammusic.youtube.model.YtArtist
import app.ammusic.youtube.model.YtItem
import app.ammusic.youtube.model.thumbnailAt
import coil3.compose.AsyncImage

/** Pochette chargée depuis YouTube. [requestSize] est la taille demandée au serveur, en pixels. */
@Composable
fun Cover(
    url: String?,
    modifier: Modifier = Modifier,
    corner: Dp = 10.dp,
    requestSize: Int = 400,
    elevated: Boolean = false,
    circle: Boolean = false,
    placeholder: Boolean = true,
) {
    val shape: Shape = if (circle) CircleShape else RoundedCornerShape(corner)
    val fill = if (placeholder) Modifier.background(AmTheme.colors.surface) else Modifier
    val shadow = if (elevated) {
        Modifier.shadow(14.dp, shape, ambientColor = Color(0x40000000), spotColor = Color(0x59000000))
    } else {
        Modifier
    }
    AsyncImage(
        model = url?.thumbnailAt(requestSize),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.then(shadow).clip(shape).then(fill),
    )
}

/** Squelette animé affiché pendant un chargement. */
fun Modifier.shimmer(shape: Shape): Modifier = composed {
    val colors = AmTheme.colors
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "shimmer")
    clip(shape).drawBehind {
        val band = size.width.coerceAtLeast(1f) * 1.5f
        val x = -band + progress * (size.width + 2 * band)
        drawRect(
            Brush.linearGradient(
                colors = listOf(colors.surface, colors.surfaceHigh, colors.surface),
                start = Offset(x, 0f),
                end = Offset(x + band, size.height),
            ),
        )
    }
}

@Composable
fun ErrorState(title: String, message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AmTheme.colors
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AmText(title, style = AmTheme.type.section, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        AmText(message, color = colors.textSecondary, style = AmTheme.type.subhead, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Box(
            Modifier
                .height(48.dp)
                .pressable(haptic = true, label = "Réessayer", onClick = onRetry)
                .clip(CircleShape)
                .background(colors.accent)
                .padding(horizontal = 28.dp),
            contentAlignment = Alignment.Center,
        ) {
            AmText("Réessayer", style = AmTheme.type.headline, color = Color.White)
        }
    }
}

/** Ligne de liste : pochette, titre, sous-titre. Les artistes ont une pochette ronde. */
@Composable
fun ItemRow(item: YtItem, onClick: () -> Unit, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(modifier.pressable(scale = 0.98f, onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        Cover(item.thumbnail, Modifier.size(52.dp), corner = 8.dp, requestSize = 160, circle = item is YtArtist)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            AmText(item.title, style = AmTheme.type.subhead, maxLines = 1)
            AmText(item.subtitle, style = AmTheme.type.footnote, color = AmTheme.colors.textSecondary, maxLines = 1)
        }
        trailing?.invoke()
    }
}
