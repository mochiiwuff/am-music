package app.ammusic.ui.home

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
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import app.ammusic.data.fake.FakeData
import app.ammusic.domain.model.MediaCard
import app.ammusic.ui.components.AmIcon
import app.ammusic.ui.components.AmIcons
import app.ammusic.ui.components.AmText
import app.ammusic.ui.components.Artwork
import app.ammusic.ui.components.IconButton
import app.ammusic.ui.components.LargeTitleScreen
import app.ammusic.ui.components.SectionHeader
import app.ammusic.ui.components.pressable
import app.ammusic.ui.theme.AmTheme

@Composable
fun HomeScreen(onProfile: () -> Unit) {
    LargeTitleScreen(title = "Accueil", trailing = { ProfileButton(onProfile) }) {
        item(key = "mixes", contentType = "hero") { MixCarousel(FakeData.mixes) }

        item(key = "recentHeader", contentType = "header") { SectionHeader("Écoutés récemment") }
        item(key = "recent", contentType = "squares") { SquareCarousel(FakeData.recent, size = 148) }

        item(key = "recommendedHeader", contentType = "header") { SectionHeader("Recommandés pour vous") }
        item(key = "recommended", contentType = "rows") { TrackGrid(FakeData.recommended) }

        item(key = "newHeader", contentType = "header") { SectionHeader("Nouveautés") }
        item(key = "new", contentType = "squares") { SquareCarousel(FakeData.newReleases, size = 176) }

        item(key = "moodsHeader", contentType = "header") { SectionHeader("Humeurs et genres") }
        items(FakeData.moods.chunked(2), key = { it.first().id }, contentType = { "tiles" }) { pair ->
            TileRow(pair, height = 76)
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

@Composable
private fun MixCarousel(mixes: List<MediaCard>) {
    val state = rememberLazyListState()
    LazyRow(
        state = state,
        flingBehavior = rememberSnapFlingBehavior(state),
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(top = 8.dp),
    ) {
        items(mixes, key = { it.id }) { mix ->
            Box(Modifier.width(284.dp).height(348.dp).pressable(scale = 0.97f) {}) {
                Artwork(mix.artSeed, Modifier.fillMaxSize(), corner = 22.dp, elevated = true)
                Column(
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(22.dp))
                        .drawBehind {
                            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color(0xB3000000)), startY = size.height * 0.45f))
                        }
                        .padding(20.dp),
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    AmText(
                        text = "MIX POUR VOUS",
                        style = AmTheme.type.caption.copy(letterSpacing = 0.1.em, fontWeight = FontWeight.SemiBold),
                        color = Color(0xCCFFFFFF),
                    )
                    Spacer(Modifier.height(6.dp))
                    AmText(mix.title, style = AmTheme.type.title, color = Color.White, maxLines = 2)
                    Spacer(Modifier.height(4.dp))
                    AmText(mix.subtitle, style = AmTheme.type.subhead, color = Color(0xCCFFFFFF), maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun SquareCarousel(cards: List<MediaCard>, size: Int) {
    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items(cards, key = { it.id }) { card ->
            Column(Modifier.width(size.dp).pressable {}) {
                Artwork(card.artSeed, Modifier.fillMaxWidth().aspectRatio(1f), corner = 12.dp, elevated = true)
                Spacer(Modifier.height(8.dp))
                AmText(card.title, style = AmTheme.type.subhead, maxLines = 1)
                AmText(card.subtitle, style = AmTheme.type.footnote, color = AmTheme.colors.textSecondary, maxLines = 1)
            }
        }
    }
}

@Composable
private fun TrackGrid(tracks: List<MediaCard>) {
    LazyHorizontalGrid(
        rows = GridCells.Fixed(3),
        modifier = Modifier.fillMaxWidth().height(192.dp),
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(tracks, key = { it.id }) { track ->
            Row(Modifier.width(304.dp).pressable(scale = 0.98f) {}, verticalAlignment = Alignment.CenterVertically) {
                Artwork(track.artSeed, Modifier.size(52.dp), corner = 8.dp)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    AmText(track.title, style = AmTheme.type.subhead, maxLines = 1)
                    AmText(track.subtitle, style = AmTheme.type.footnote, color = AmTheme.colors.textSecondary, maxLines = 1)
                }
                IconButton(AmIcons.More, label = "Plus d'options", onClick = {}, tint = AmTheme.colors.textSecondary, iconSize = 20.dp)
            }
        }
    }
}

/** Rangée de deux tuiles colorées (humeurs, genres, catégories). */
@Composable
fun TileRow(cards: List<MediaCard>, height: Int) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        cards.forEach { card ->
            Box(Modifier.weight(1f).height(height.dp).pressable {}) {
                Artwork(card.artSeed, Modifier.fillMaxSize(), corner = 14.dp)
                AmText(
                    text = card.title,
                    style = AmTheme.type.headline,
                    color = Color.White,
                    maxLines = 1,
                    modifier = Modifier.align(Alignment.BottomStart).padding(14.dp),
                )
            }
        }
        if (cards.size == 1) Spacer(Modifier.weight(1f))
    }
}
