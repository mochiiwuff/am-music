package app.ammusic.ui.library

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.ammusic.data.MusicRepository
import app.ammusic.data.library.LibraryRepository
import app.ammusic.ui.AddSongsRoute
import app.ammusic.ui.components.AmIcon
import app.ammusic.ui.components.AmIcons
import app.ammusic.ui.components.AmText
import app.ammusic.ui.components.Cover
import app.ammusic.ui.components.EmptyState
import app.ammusic.ui.components.LargeTitleScreen
import app.ammusic.ui.components.LocalActions
import app.ammusic.ui.components.RowCoverSize
import app.ammusic.ui.components.RowsSkeleton
import app.ammusic.ui.components.SectionHeader
import app.ammusic.ui.components.hairlineBelow
import app.ammusic.ui.components.pressable
import app.ammusic.ui.theme.AmTheme
import app.ammusic.youtube.model.YtSearchFilter
import app.ammusic.youtube.model.YtTrack
import app.ammusic.youtube.tr
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

sealed interface SongResults {
    data object Idle : SongResults

    data object Loading : SongResults

    data object Failed : SongResults

    data class Found(val tracks: List<YtTrack>) : SongResults
}

@HiltViewModel
class AddSongsViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val library: LibraryRepository,
    music: MusicRepository,
) : ViewModel() {
    val route = savedState.toRoute<AddSongsRoute>()
    val query = MutableStateFlow("")
    private var added = 0

    val present: StateFlow<Set<String>> = library.playlistWithTracks(route.playlistId)
        .map { (_, tracks) -> tracks.map { it.id }.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val suggestions: StateFlow<List<YtTrack>> = combine(library.history, library.liked) { history, liked ->
        (history + liked).distinctBy { it.id }.take(40)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val results: StateFlow<SongResults> = query
        .map { it.trim() }
        .distinctUntilChanged()
        .debounce(350)
        .flatMapLatest { text ->
            flow {
                if (text.isEmpty()) {
                    emit(SongResults.Idle)
                    return@flow
                }
                emit(SongResults.Loading)
                val page = music.search(text, YtSearchFilter.Songs).getOrNull()
                emit(if (page == null) SongResults.Failed else SongResults.Found(page.items.filterIsInstance<YtTrack>()))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SongResults.Idle)

    fun add(track: YtTrack) {
        if (track.id in present.value) return
        added++
        library.addToPlaylist(route.playlistId, track, quiet = true)
    }

    override fun onCleared() {
        if (added > 0) {
            val title = route.title
            library.announce(
                if (added == 1) tr("1 titre ajouté à « $title »", "1 song added to “$title”") else tr("$added titres ajoutés à « $title »", "$added songs added to “$title”"),
            )
        }
    }
}

@Composable
fun AddSongsScreen(viewModel: AddSongsViewModel = hiltViewModel()) {
    val colors = AmTheme.colors
    val actions = LocalActions.current
    val query by viewModel.query.collectAsStateWithLifecycle()
    val present by viewModel.present.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()

    LargeTitleScreen(
        title = tr("Ajouter des titres", "Add songs"),
        leading = {
            Box(Modifier.height(48.dp).pressable(label = tr("Terminé", "Done"), onClick = actions.back).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                AmText(tr("OK", "Done"), style = AmTheme.type.headline, color = colors.accent)
            }
        },
    ) {
        item(key = "subtitle", contentType = "subtitle") {
            AmText(
                text = tr("Dans « ${viewModel.route.title} »", "To “${viewModel.route.title}”"),
                style = AmTheme.type.subhead,
                color = colors.textSecondary,
                maxLines = 1,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
            )
        }
        item(key = "field", contentType = "field") {
            FilterField(
                query = query,
                onChange = { viewModel.query.value = it },
                placeholder = tr("Titres, artistes…", "Songs, artists…"),
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
            )
        }
        val tracks = when (val current = results) {
            SongResults.Idle -> {
                if (suggestions.isEmpty()) {
                    item(key = "hint") { EmptyState(AmIcons.Search, tr("Cherchez un titre", "Search for a song")) }
                    return@LargeTitleScreen
                }
                item(key = "suggestionsHeader", contentType = "header") { SectionHeader(tr("Écoutés récemment", "Recently played"), top = 16.dp) }
                suggestions
            }

            SongResults.Loading -> {
                item(key = "loading") { RowsSkeleton(6, Modifier.padding(top = 12.dp)) }
                return@LargeTitleScreen
            }

            SongResults.Failed -> {
                item(key = "failed") { EmptyState(AmIcons.Search, tr("Recherche impossible", "Search failed"), text = tr("Vérifiez votre connexion.", "Check your connection.")) }
                return@LargeTitleScreen
            }

            is SongResults.Found -> {
                if (current.tracks.isEmpty()) {
                    item(key = "none") { EmptyState(AmIcons.Search, tr("Aucun résultat", "No results")) }
                    return@LargeTitleScreen
                }
                current.tracks
            }
        }
        items(tracks, key = { it.id }, contentType = { "track" }) { track ->
            AddRow(track, added = track.id in present, modifier = Modifier.animateItem()) { viewModel.add(track) }
        }
    }
}

@Composable
private fun AddRow(track: YtTrack, added: Boolean, modifier: Modifier = Modifier, onAdd: () -> Unit) {
    val colors = AmTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .pressable(scale = 0.99f, haptic = !added, label = if (added) tr("Déjà ajouté", "Added") else tr("Ajouter", "Add"), onClick = onAdd)
            .hairlineBelow(colors.hairline, 80.dp)
            .padding(start = 20.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(track.thumbnail, Modifier.size(48.dp), corner = 6.dp, requestSize = RowCoverSize)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            AmText(track.title, style = AmTheme.type.body.copy(fontSize = 16.sp, lineHeight = 21.sp), maxLines = 1)
            AmText(track.artists, style = AmTheme.type.footnote, color = colors.textSecondary, maxLines = 1)
        }
        val fill by animateFloatAsState(if (added) 1f else 0f, spring(dampingRatio = 1f, stiffness = 420f), label = "added")
        Box(
            Modifier
                .size(32.dp)
                .graphicsLayer {
                    val pop = 1f + 0.12f * fill * (1f - fill) * 4f
                    scaleX = pop
                    scaleY = pop
                }
                .clip(CircleShape)
                .background(if (added) colors.accent else colors.text.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = added,
                transitionSpec = { (fadeIn(spring(stiffness = 520f)) + scaleIn(spring(dampingRatio = 1f, stiffness = 520f), 0.6f)) togetherWith fadeOut(spring(stiffness = 700f)) },
                label = "addIcon",
            ) { done ->
                AmIcon(if (done) AmIcons.Check else AmIcons.Plus, tint = if (done) Color.White else colors.accent, size = 18.dp)
            }
        }
    }
}
