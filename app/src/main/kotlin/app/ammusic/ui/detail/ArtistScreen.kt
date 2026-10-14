package app.ammusic.ui.detail

import app.ammusic.youtube.tr

import androidx.compose.animation.EnterExitState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ammusic.ui.ArtistRoute
import app.ammusic.ui.ArtistState
import app.ammusic.ui.ArtistViewModel
import app.ammusic.ui.LoadStatus
import app.ammusic.ui.components.AmIcon
import app.ammusic.ui.components.AmIcons
import app.ammusic.ui.components.AmText
import app.ammusic.ui.components.Cover
import app.ammusic.ui.components.ErrorState
import app.ammusic.ui.components.IconButton
import app.ammusic.ui.components.ItemCarousel
import app.ammusic.ui.components.ItemRow
import app.ammusic.ui.components.LocalActions
import app.ammusic.ui.components.LocalScreenScope
import app.ammusic.ui.components.RowsSkeleton
import app.ammusic.ui.components.SectionHeader
import app.ammusic.ui.components.cascade
import app.ammusic.ui.components.listRow
import app.ammusic.ui.components.pressable
import app.ammusic.ui.components.sharedCover
import app.ammusic.ui.theme.AmTheme
import app.ammusic.youtube.model.YtTrack
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private const val TracksShown = 5

@Composable
fun ArtistScreen(viewModel: ArtistViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val route = viewModel.route
    val actions = LocalActions.current
    val list = rememberLazyListState()
    val page = state.page
    val topTracks = remember(page) {
        page?.sections?.firstOrNull { section -> section.items.all { it is YtTrack } }?.items?.filterIsInstance<YtTrack>().orEmpty()
    }

    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(state.status) {
        if (state.status == LoadStatus.Ready) {
            delay(600)
            settled = true
        }
    }

    val savedIds by viewModel.savedIds.collectAsStateWithLifecycle()

    DetailScaffold(
        title = state.name,
        state = list,
        revealAfter = 250.dp,
        underStatusBar = true,
        trailing = { SaveButton(route.id in savedIds, addLabel = tr("Suivre l'artiste", "Follow artist"), removeLabel = tr("Ne plus suivre", "Unfollow"), onToggle = viewModel::toggleFollow) },
    ) {
        item(key = "hero", contentType = "hero") {
            ArtistHero(
                state = state,
                route = route,
                list = list,
                canPlay = topTracks.isNotEmpty(),
                onPlay = { actions.playAll(topTracks, 0, false) },
                onShuffle = { actions.playAll(topTracks, 0, true) },
            )
        }

        when {
            state.status == LoadStatus.Loading -> item(key = "skeleton") { RowsSkeleton(5, Modifier.padding(top = 28.dp)) }

            state.status == LoadStatus.Error || page == null -> item(key = "error") {
                ErrorState(
                    title = tr("Artiste indisponible", "Artist unavailable"),
                    message = tr("Vérifiez votre connexion.", "Check your connection."),
                    onRetry = viewModel::load,
                )
            }

            else -> {
                page.sections.forEachIndexed { index, section ->
                    val origin = "artist:${route.id}:$index"
                    val more = section.moreId?.let { id -> { actions.openMore(section.title, id, section.moreParams) } }
                    item(key = "header$index", contentType = "header") {
                        SectionHeader(section.title, top = if (index == 0) 14.dp else 32.dp, onMore = more)
                    }
                    if (section.items.all { it is YtTrack }) {
                        val tracks = section.items.filterIsInstance<YtTrack>()
                        itemsIndexed(tracks.take(TracksShown), key = { _, track -> "$index:${track.id}" }, contentType = { _, _ -> "track" }) { position, track ->
                            ItemRow(
                                item = track,
                                origin = origin,
                                modifier = Modifier.fillMaxWidth().cascade(position, enabled = !settled).listRow(),
                                onClick = { actions.playAll(tracks, position, null) },
                            )
                        }
                    } else {
                        item(key = "section$index", contentType = "carousel") { ItemCarousel(section.items, origin) }
                    }
                }
                page.description?.let { description ->
                    item(key = "aboutHeader", contentType = "header") { SectionHeader(tr("À propos", "About")) }
                    item(key = "about", contentType = "text") { About(description) }
                }
            }
        }
    }
}

@Composable
private fun ArtistHero(state: ArtistState, route: ArtistRoute, list: LazyListState, canPlay: Boolean, onPlay: () -> Unit, onShuffle: () -> Unit) {
    val colors = AmTheme.colors
    val rounding = LocalScreenScope.current?.takeIf { route.sharedKey.isNotEmpty() }?.transition?.animateFloat(
        transitionSpec = { spring(dampingRatio = 0.9f, stiffness = 320f) },
        label = "rounding",
    ) { if (it == EnterExitState.Visible) 0f else 50f }

    Box(Modifier.fillMaxWidth().aspectRatio(0.94f).clipToBounds()) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val scrolled = if (list.firstVisibleItemIndex == 0) list.firstVisibleItemScrollOffset.toFloat() else size.height
                    translationY = scrolled * 0.42f
                    alpha = 1f - (scrolled / size.height).coerceIn(0f, 1f) * 0.7f
                }
                .sharedCover(route.sharedKey)
                .graphicsLayer {
                    clip = true
                    shape = RoundedCornerShape((rounding?.value ?: 0f).roundToInt().coerceIn(0, 50))
                },
        ) {
            Cover(state.thumbnail, Modifier.fillMaxSize(), corner = 0.dp, requestSize = route.coverSize)
            Cover(state.thumbnail, Modifier.fillMaxSize(), corner = 0.dp, requestSize = 1080, placeholder = false)
        }
        Box(
            Modifier.fillMaxSize().drawBehind {
                val ground = colors.background
                drawRect(Brush.verticalGradient(listOf(ground.copy(alpha = 0.5f), Color.Transparent), endY = 110.dp.toPx()))
                drawRect(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.72f to ground.copy(alpha = 0.82f),
                        1f to ground,
                        startY = size.height * 0.4f,
                        endY = size.height,
                    ),
                )
            },
        )
        Column(Modifier.align(Alignment.BottomStart).padding(start = 20.dp, end = 20.dp)) {
            AmText(state.name, style = AmTheme.type.largeTitle.copy(fontSize = 40.sp, lineHeight = 43.sp), maxLines = 2)
            Row(Modifier.height(60.dp), verticalAlignment = Alignment.CenterVertically) {
                AmText(
                    text = state.page?.listeners.orEmpty(),
                    style = AmTheme.type.footnote,
                    color = colors.textSecondary,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                if (canPlay) {
                    IconButton(AmIcons.Shuffle, label = tr("Lecture aléatoire", "Shuffle"), onClick = onShuffle)
                    Box(
                        Modifier
                            .padding(start = 4.dp)
                            .size(52.dp)
                            .pressable(scale = 0.9f, haptic = true, label = tr("Lecture", "Play"), onClick = onPlay)
                            .clip(CircleShape)
                            .background(colors.accent),
                        contentAlignment = Alignment.Center,
                    ) {
                        AmIcon(AmIcons.Play, tint = Color.White, size = 24.dp, modifier = Modifier.padding(start = 2.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun About(description: String) {
    var expanded by remember { mutableStateOf(false) }
    AmText(
        text = description,
        style = AmTheme.type.subhead.copy(fontWeight = FontWeight.Normal, lineHeight = 22.sp),
        color = AmTheme.colors.textSecondary,
        maxLines = if (expanded) Int.MAX_VALUE else 4,
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .pressable(scale = 0.99f, label = if (expanded) tr("Réduire", "Show less") else tr("Lire la suite", "Read more")) { expanded = !expanded }
            .animateContentSize(spring(dampingRatio = 0.9f, stiffness = 300f)),
    )
}
