package app.ammusic.ui.library

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.ammusic.data.library.LibraryRepository
import app.ammusic.ui.components.AmIcons
import app.ammusic.ui.components.AmText
import app.ammusic.ui.components.Cover
import app.ammusic.ui.components.EmptyState
import app.ammusic.ui.components.IconButton
import app.ammusic.ui.components.LargeTitleScreen
import app.ammusic.ui.components.LocalActions
import app.ammusic.ui.components.RowCoverSize
import app.ammusic.ui.components.SectionHeader
import app.ammusic.ui.components.SegmentedControl
import app.ammusic.ui.components.TileCoverSize
import app.ammusic.ui.components.cascade
import app.ammusic.ui.components.hairlineBelow
import app.ammusic.ui.components.pressable
import app.ammusic.ui.player.PlayerBackdrop
import app.ammusic.ui.player.rememberPlayerColors
import app.ammusic.ui.theme.AmTheme
import app.ammusic.youtube.model.YtTrack
import app.ammusic.youtube.tr
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar
import javax.inject.Inject

enum class RecapPeriod { Month, Year, All }

class RankedTrack(val track: YtTrack, val plays: Int, val minutes: Int)

class RankedArtist(val name: String, val artistId: String?, val cover: String?, val plays: Int, val minutes: Int)

class Recap(val minutes: Int, val tracks: List<RankedTrack>, val artists: List<RankedArtist>)

@HiltViewModel
class RecapViewModel @Inject constructor(private val library: LibraryRepository) : ViewModel() {
    val period = MutableStateFlow(RecapPeriod.Month)

    @OptIn(ExperimentalCoroutinesApi::class)
    val recap: StateFlow<Recap?> = period.flatMapLatest { chosen ->
        library.trackPlays(since(chosen)).map { rows ->
            val tracks = rows.map { (track, stats) -> RankedTrack(track, stats.plays, (stats.listened / 60_000L).toInt()) }
            val artists = rows.groupBy { (track, _) -> track.artists.substringBefore(", ").ifBlank { tr("Artiste inconnu", "Unknown artist") } }
                .map { (name, entries) ->
                    val best = entries.maxBy { it.second.plays }.first
                    RankedArtist(
                        name = name,
                        artistId = entries.firstNotNullOfOrNull { it.first.artistId },
                        cover = best.thumbnail,
                        plays = entries.sumOf { it.second.plays },
                        minutes = (entries.sumOf { it.second.listened } / 60_000L).toInt(),
                    )
                }
                .sortedWith(compareByDescending<RankedArtist> { it.plays }.thenByDescending { it.minutes })
            Recap((rows.sumOf { it.second.listened } / 60_000L).toInt(), tracks, artists)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private fun since(period: RecapPeriod): Long {
        if (period == RecapPeriod.All) return 0L
        return Calendar.getInstance().apply {
            if (period == RecapPeriod.Year) set(Calendar.MONTH, Calendar.JANUARY)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}

@Composable
fun RecapScreen(viewModel: RecapViewModel = hiltViewModel()) {
    val actions = LocalActions.current
    val period by viewModel.period.collectAsStateWithLifecycle()
    val recap by viewModel.recap.collectAsStateWithLifecycle()
    val labels = listOf(tr("Ce mois-ci", "This month"), tr("Cette année", "This year"), tr("Depuis toujours", "All time"))

    LargeTitleScreen(
        title = tr("Récap", "Recap"),
        leading = { IconButton(AmIcons.ChevronLeft, label = tr("Retour", "Back"), onClick = actions.back) },
    ) {
        item(key = "period", contentType = "period") {
            SegmentedControl(labels, period.ordinal, Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 16.dp)) {
                viewModel.period.value = RecapPeriod.entries[it]
            }
        }
        val current = recap ?: return@LargeTitleScreen
        if (current.tracks.isEmpty()) {
            item(key = "empty") {
                EmptyState(AmIcons.Chart, tr("Pas encore de récap", "No recap yet"), text = tr("Il se remplit au fil de tes écoutes.", "It fills up as you listen."))
            }
            return@LargeTitleScreen
        }
        item(key = "hero", contentType = "hero") { Hero(current, period) }
        current.artists.firstOrNull()?.let { top ->
            item(key = "topArtist", contentType = "topArtist") { TopArtist(top) }
        }
        item(key = "tracksHeader", contentType = "header") { SectionHeader(tr("Titres les plus écoutés", "Top songs"), top = 28.dp) }
        val top = current.tracks.take(10)
        itemsIndexed(top, key = { _, ranked -> "t:" + ranked.track.id }, contentType = { _, _ -> "rankedTrack" }) { index, ranked ->
            RankedTrackRow(index, ranked, Modifier.cascade(index, enabled = true)) { actions.playAll(top.map { it.track }, index, null) }
        }
        if (current.artists.size > 1) {
            item(key = "artistsHeader", contentType = "header") { SectionHeader(tr("Artistes les plus écoutés", "Top artists"), top = 28.dp) }
            item(key = "artists", contentType = "artists") { ArtistStrip(current.artists.take(10)) }
        }
    }
}

@Composable
private fun Hero(recap: Recap, period: RecapPeriod) {
    val palette = rememberPlayerColors(recap.tracks.firstOrNull()?.track?.thumbnail, stiffness = 60f)
    val counter = remember { Animatable(0f) }
    LaunchedEffect(recap.minutes, period) {
        counter.snapTo(0f)
        counter.animateTo(recap.minutes.toFloat(), tween(1_100, easing = FastOutSlowInEasing))
    }
    val caption = when (period) {
        RecapPeriod.Month -> tr("ce mois-ci", "this month")
        RecapPeriod.Year -> tr("cette année", "this year")
        RecapPeriod.All -> tr("depuis le début", "since the start")
    }
    Box(
        Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .aspectRatio(1.35f)
            .shadow(20.dp, RoundedCornerShape(24.dp), ambientColor = Color(0x40000000), spotColor = Color(0x59000000))
            .clip(RoundedCornerShape(24.dp)),
    ) {
        PlayerBackdrop(palette, Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.SpaceBetween) {
            AmText(
                text = tr("Minutes d'écoute", "Minutes listened").uppercase(),
                style = AmTheme.type.caption.copy(letterSpacing = 0.1.em, fontWeight = FontWeight.SemiBold),
                color = Color(0xCCFFFFFF),
            )
            Column {
                AmText(
                    text = "%,d".format(counter.value.toInt()).replace(',', ' '),
                    style = AmTheme.type.largeTitle.copy(fontSize = 64.sp, lineHeight = 66.sp, letterSpacing = (-0.04).em, fontFeatureSettings = "tnum"),
                    color = Color.White,
                )
                AmText(caption, style = AmTheme.type.subhead, color = Color(0xCCFFFFFF))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                Stat("${recap.tracks.size}", tr("titres", "songs"))
                Stat("${recap.artists.size}", tr("artistes", "artists"))
                Stat("${recap.tracks.sumOf { it.plays }}", tr("écoutes", "plays"))
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column {
        AmText(value, style = AmTheme.type.section.copy(fontFeatureSettings = "tnum"), color = Color.White)
        AmText(label, style = AmTheme.type.footnote, color = Color(0xB3FFFFFF))
    }
}

@Composable
private fun TopArtist(artist: RankedArtist) {
    val colors = AmTheme.colors
    val actions = LocalActions.current
    val open = artist.artistId?.let { id -> Modifier.pressable(scale = 0.98f, label = artist.name) { actions.openArtist(id, artist.name) } } ?: Modifier
    Row(
        Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp).fillMaxWidth().then(open),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(artist.cover, Modifier.size(84.dp), circle = true, requestSize = TileCoverSize)
        Column(Modifier.padding(start = 16.dp).weight(1f)) {
            AmText(tr("Artiste n°1", "Top artist").uppercase(), style = AmTheme.type.caption.copy(letterSpacing = 0.1.em, fontWeight = FontWeight.SemiBold), color = colors.accent)
            AmText(artist.name, style = AmTheme.type.title, maxLines = 1, modifier = Modifier.padding(top = 2.dp))
            AmText(
                text = tr("${artist.plays} écoutes · ${artist.minutes} min", "${artist.plays} plays · ${artist.minutes} min"),
                style = AmTheme.type.subhead,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun RankedTrackRow(index: Int, ranked: RankedTrack, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = AmTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .pressable(scale = 0.98f, onClick = onClick)
            .hairlineBelow(colors.hairline, 116.dp)
            .padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AmText(
            text = "${index + 1}",
            style = AmTheme.type.headline.copy(fontFeatureSettings = "tnum"),
            color = if (index < 3) colors.accent else colors.textTertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(28.dp),
        )
        Cover(ranked.track.thumbnail, Modifier.padding(start = 8.dp).size(48.dp), corner = 6.dp, requestSize = RowCoverSize)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            AmText(ranked.track.title, style = AmTheme.type.body.copy(fontSize = 16.sp, lineHeight = 21.sp), maxLines = 1)
            AmText(ranked.track.artists, style = AmTheme.type.footnote, color = colors.textSecondary, maxLines = 1)
        }
        AmText(tr("${ranked.plays}×", "${ranked.plays}×"), style = AmTheme.type.subhead.copy(fontFeatureSettings = "tnum"), color = colors.textSecondary)
    }
}

@Composable
private fun ArtistStrip(artists: List<RankedArtist>) {
    val colors = AmTheme.colors
    val actions = LocalActions.current
    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        itemsIndexed(artists, key = { _, artist -> artist.name }) { index, artist ->
            val open = artist.artistId?.let { id -> Modifier.pressable(label = artist.name) { actions.openArtist(id, artist.name) } } ?: Modifier
            Column(Modifier.width(112.dp).then(open), horizontalAlignment = Alignment.CenterHorizontally) {
                Box {
                    Cover(artist.cover, Modifier.size(112.dp), circle = true, requestSize = TileCoverSize, elevated = true)
                    Box(
                        Modifier.align(Alignment.BottomStart).size(30.dp).clip(CircleShape).background(colors.background).padding(2.dp).clip(CircleShape).background(if (index < 3) colors.accent else colors.surfaceHigh),
                        contentAlignment = Alignment.Center,
                    ) {
                        AmText("${index + 1}", style = AmTheme.type.footnote.copy(fontWeight = FontWeight.Bold), color = if (index < 3) Color.White else colors.text)
                    }
                }
                AmText(artist.name, style = AmTheme.type.subhead, maxLines = 1, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
                AmText(tr("${artist.plays} écoutes", "${artist.plays} plays"), style = AmTheme.type.footnote, color = colors.textSecondary, maxLines = 1)
            }
        }
    }
}
