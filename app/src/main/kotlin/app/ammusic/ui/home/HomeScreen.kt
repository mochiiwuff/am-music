package app.ammusic.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ammusic.ui.HomeState
import app.ammusic.ui.HomeViewModel
import app.ammusic.ui.components.AmIcon
import app.ammusic.ui.components.AmIcons
import app.ammusic.ui.components.AmText
import app.ammusic.ui.components.Cover
import app.ammusic.ui.components.ErrorState
import app.ammusic.ui.components.IconButton
import app.ammusic.ui.components.ItemRow
import app.ammusic.ui.components.LargeTitleScreen
import app.ammusic.ui.components.SectionHeader
import app.ammusic.ui.components.pressable
import app.ammusic.ui.components.shimmer
import app.ammusic.ui.theme.AmTheme
import app.ammusic.youtube.model.YtAlbum
import app.ammusic.youtube.model.YtArtist
import app.ammusic.youtube.model.YtItem
import app.ammusic.youtube.model.YtMood
import app.ammusic.youtube.model.YtPlaylist
import app.ammusic.youtube.model.YtTrack

@Composable
fun HomeScreen(onProfile: () -> Unit, onPlay: (YtTrack) -> Unit, viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val onItem: (YtItem) -> Unit = { if (it is YtTrack) onPlay(it) }

    LargeTitleScreen(title = "Accueil", trailing = { ProfileButton(onProfile) }) {
        when (val current = state) {
            HomeState.Loading -> item(key = "skeleton") { HomeSkeleton() }

            HomeState.Error -> item(key = "error") {
                ErrorState(
                    title = "Accueil indisponible",
                    message = "Vérifiez votre connexion internet, puis réessayez.",
                    onRetry = viewModel::load,
                )
            }

            is HomeState.Content -> {
                val hero = current.sections.indexOfFirst { section -> section.items.none { it is YtTrack } }
                current.sections.forEachIndexed { index, section ->
                    item(key = "header$index", contentType = "header") {
                        SectionHeader(section.title, top = if (index == 0) 8.dp else 32.dp)
                    }
                    item(key = "section$index", contentType = "section") {
                        when {
                            section.items.all { it is YtTrack } -> TrackGrid(section.items, onItem)
                            index == hero -> HeroCarousel(section.items, onItem)
                            else -> SquareCarousel(section.items, onItem)
                        }
                    }
                }
                if (current.moods.isNotEmpty()) {
                    item(key = "moodsHeader", contentType = "header") { SectionHeader("Humeurs et genres") }
                    items(current.moods.take(12).chunked(2), key = { it.first().title }, contentType = { "tiles" }) { pair ->
                        MoodRow(pair)
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileButton(onClick: () -> Unit) {
    val colors = AmTheme.colors
    Box(Modifier.size(48.dp).pressable(scale = 0.88f, label = "Réglages", onClick = onClick), contentAlignment = Alignment.Center) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(colors.surfaceHigh), contentAlignment = Alignment.Center) {
            AmIcon(AmIcons.User, tint = colors.accent, size = 18.dp)
        }
    }
}

private fun YtItem.kind(): String = when (this) {
    is YtTrack -> "TITRE"
    is YtAlbum -> "ALBUM"
    is YtPlaylist -> "PLAYLIST"
    is YtArtist -> "ARTISTE"
}

@Composable
private fun HeroCarousel(items: List<YtItem>, onItem: (YtItem) -> Unit) {
    val state = rememberLazyListState()
    LazyRow(
        state = state,
        flingBehavior = rememberSnapFlingBehavior(state),
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items, key = { it.key }) { item ->
            Box(Modifier.width(284.dp).height(348.dp).pressable(scale = 0.97f) { onItem(item) }) {
                Cover(item.thumbnail, Modifier.fillMaxSize(), corner = 22.dp, requestSize = 720, elevated = true)
                Column(
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(22.dp))
                        .drawBehind {
                            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000)), startY = size.height * 0.4f))
                        }
                        .padding(20.dp),
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    AmText(
                        text = item.kind(),
                        style = AmTheme.type.caption.copy(letterSpacing = 0.1.em, fontWeight = FontWeight.SemiBold),
                        color = Color(0xCCFFFFFF),
                    )
                    Spacer(Modifier.height(6.dp))
                    AmText(item.title, style = AmTheme.type.title, color = Color.White, maxLines = 2)
                    Spacer(Modifier.height(4.dp))
                    AmText(item.subtitle, style = AmTheme.type.subhead, color = Color(0xCCFFFFFF), maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun SquareCarousel(items: List<YtItem>, onItem: (YtItem) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items(items, key = { it.key }) { item ->
            Column(Modifier.width(148.dp).pressable { onItem(item) }) {
                Cover(item.thumbnail, Modifier.fillMaxWidth().aspectRatio(1f), corner = 12.dp, elevated = true, circle = item is YtArtist)
                Spacer(Modifier.height(8.dp))
                AmText(item.title, style = AmTheme.type.subhead, maxLines = 1)
                AmText(item.subtitle, style = AmTheme.type.footnote, color = AmTheme.colors.textSecondary, maxLines = 1)
            }
        }
    }
}

@Composable
private fun TrackGrid(tracks: List<YtItem>, onItem: (YtItem) -> Unit) {
    val rows = tracks.size.coerceIn(1, 3)
    LazyHorizontalGrid(
        rows = GridCells.Fixed(rows),
        modifier = Modifier.fillMaxWidth().height((64 * rows).dp),
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(tracks, key = { it.key }) { track ->
            ItemRow(track, onClick = { onItem(track) }, modifier = Modifier.width(304.dp)) {
                IconButton(AmIcons.More, label = "Plus d'options", onClick = {}, tint = AmTheme.colors.textSecondary, iconSize = 20.dp)
            }
        }
    }
}

/** Rangée de deux tuiles aux couleurs fournies par YouTube Music. */
@Composable
fun MoodRow(moods: List<YtMood>, height: Int = 76) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        moods.forEach { mood ->
            val color = Color(mood.color.toInt()).copy(alpha = 1f)
            Box(
                Modifier
                    .weight(1f)
                    .height(height.dp)
                    .pressable {}
                    .clip(RoundedCornerShape(14.dp))
                    .background(AmTheme.colors.surface)
                    .drawBehind {
                        drawRect(Brush.linearGradient(listOf(color.copy(alpha = 0.85f), color.copy(alpha = 0.25f))))
                    },
            ) {
                AmText(
                    text = mood.title,
                    style = AmTheme.type.headline,
                    maxLines = 2,
                    modifier = Modifier.align(Alignment.BottomStart).padding(14.dp),
                )
            }
        }
        if (moods.size == 1) Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun HomeSkeleton() {
    Column(Modifier.padding(start = 20.dp, top = 12.dp)) {
        Box(Modifier.width(180.dp).height(24.dp).shimmer(RoundedCornerShape(8.dp)))
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(2) { Box(Modifier.width(284.dp).height(348.dp).shimmer(RoundedCornerShape(22.dp))) }
        }
        Spacer(Modifier.height(36.dp))
        Box(Modifier.width(220.dp).height(24.dp).shimmer(RoundedCornerShape(8.dp)))
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(3) { Box(Modifier.size(148.dp).shimmer(RoundedCornerShape(12.dp))) }
        }
    }
}
