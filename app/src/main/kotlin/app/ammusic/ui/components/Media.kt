@file:OptIn(ExperimentalSharedTransitionApi::class)

package app.ammusic.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.ammusic.ui.theme.AmTheme
import app.ammusic.youtube.model.YtAlbum
import app.ammusic.youtube.model.YtArtist
import app.ammusic.youtube.model.YtItem
import app.ammusic.youtube.model.YtTrack
import app.ammusic.youtube.model.thumbnailAt
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import kotlin.math.PI
import kotlin.math.sin

val LocalSharedScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalScreenScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

val SharedBounds = BoundsTransform { _, _ -> spring(dampingRatio = 0.86f, stiffness = 320f) }

@Composable
fun Modifier.sharedCover(key: String?): Modifier {
    val shared = LocalSharedScope.current
    val screen = LocalScreenScope.current
    if (key.isNullOrEmpty() || shared == null || screen == null) return this
    return with(shared) {
        this@sharedCover.sharedElement(rememberSharedContentState(key), screen, boundsTransform = SharedBounds)
    }
}

const val RowCoverSize = 160
const val TileCoverSize = 400

@Composable
fun Cover(
    url: String?,
    modifier: Modifier = Modifier,
    corner: Dp = 10.dp,
    requestSize: Int = TileCoverSize,
    elevated: Boolean = false,
    circle: Boolean = false,
    placeholder: Boolean = true,
    sharedKey: String? = null,
) {
    val shape: Shape = if (circle) CircleShape else RoundedCornerShape(corner)
    val fill = if (placeholder) Modifier.background(AmTheme.colors.surface) else Modifier
    val shadow = if (elevated) {
        Modifier.shadow(14.dp, shape, ambientColor = Color(0x40000000), spotColor = Color(0x59000000))
    } else {
        Modifier
    }
    val context = LocalPlatformContext.current
    var resizedFailed by remember(url, requestSize) { mutableStateOf(false) }
    val request = remember(url, requestSize, resizedFailed) {
        val data = if (resizedFailed) url else url?.thumbnailAt(requestSize)
        ImageRequest.Builder(context).data(data).size(coil3.size.Size.ORIGINAL).build()
    }
    AsyncImage(
        model = request,
        onError = { resizedFailed = true },
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.sharedCover(sharedKey).then(shadow).clip(shape).then(fill),
    )
}

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

@Composable
fun ItemRow(
    item: YtItem,
    origin: String,
    modifier: Modifier = Modifier,
    extras: List<MenuAction> = emptyList(),
    onClick: (() -> Unit)? = null,
) {
    val actions = LocalActions.current
    val colors = AmTheme.colors
    val track = item as? YtTrack
    val current = track != null && LocalNowPlaying.current.id == track.id
    Row(
        modifier.pressable(
            scale = 0.98f,
            onLongClick = track?.let { { actions.menu(it, extras) } },
            onClick = onClick ?: { actions.open(item, origin, RowCoverSize) },
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(
            url = item.thumbnail,
            modifier = Modifier.size(52.dp),
            corner = 8.dp,
            requestSize = RowCoverSize,
            circle = item is YtArtist,
            sharedKey = if (track == null) sharedKey(origin, item) else null,
        )
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            AmText(item.title, style = AmTheme.type.subhead, color = if (current) colors.accent else colors.text, maxLines = 1)
            AmText(item.caption(), style = AmTheme.type.footnote, color = colors.textSecondary, maxLines = 1)
        }
        if (track != null) {
            IconButton(AmIcons.More, label = "Plus d'options", onClick = { actions.menu(track, extras) }, tint = colors.textSecondary, iconSize = 20.dp)
        } else {
            AmIcon(AmIcons.ChevronRight, tint = colors.textTertiary, size = 18.dp)
        }
    }
}

private fun YtItem.caption(): String = subtitle.ifBlank { (this as? YtAlbum)?.year?.toString().orEmpty() }

@Composable
fun ItemCarousel(items: List<YtItem>, origin: String, modifier: Modifier = Modifier) {
    val actions = LocalActions.current
    LazyRow(modifier, contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items(items, key = { it.key }) { item ->
            ItemTile(item, origin, Modifier.width(148.dp)) { actions.open(item, origin, TileCoverSize) }
        }
    }
}

@Composable
fun ItemTile(item: YtItem, origin: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val track = item as? YtTrack
    val actions = LocalActions.current
    Column(modifier.pressable(onLongClick = track?.let { { actions.menu(it, emptyList()) } }, onClick = onClick)) {
        Cover(
            url = item.thumbnail,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            corner = 12.dp,
            elevated = true,
            circle = item is YtArtist,
            sharedKey = if (track == null) sharedKey(origin, item) else null,
        )
        Spacer(Modifier.height(8.dp))
        AmText(item.title, style = AmTheme.type.subhead, maxLines = 1)
        AmText(item.caption(), style = AmTheme.type.footnote, color = AmTheme.colors.textSecondary, maxLines = 1)
    }
}

@Composable
fun NowPlayingBars(playing: Boolean, color: Color, modifier: Modifier = Modifier) {
    val phase = if (playing) {
        rememberInfiniteTransition(label = "bars").animateFloat(0f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "bars")
    } else {
        remember { mutableFloatStateOf(0f) }
    }
    val level by animateFloatAsState(if (playing) 1f else 0f, spring(stiffness = 180f), label = "level")
    Canvas(modifier.size(16.dp)) {
        val bar = size.width / 5f
        repeat(3) { index ->
            val wave = (sin((phase.value + index * 0.31f) * 2f * PI.toFloat()) + 1f) / 2f
            val height = size.height * (0.24f + (0.2f + 0.56f * wave) * level)
            drawRoundRect(color, Offset(index * 2f * bar, size.height - height), Size(bar, height), CornerRadius(bar / 2f))
        }
    }
}

@Composable
fun RowsSkeleton(count: Int, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        repeat(count) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(52.dp).shimmer(RoundedCornerShape(8.dp)))
                Column(Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.fillMaxWidth(0.6f).height(14.dp).shimmer(RoundedCornerShape(6.dp)))
                    Box(Modifier.fillMaxWidth(0.35f).height(12.dp).shimmer(RoundedCornerShape(6.dp)))
                }
            }
        }
    }
}
