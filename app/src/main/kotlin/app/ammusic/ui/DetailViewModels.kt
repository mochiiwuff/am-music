package app.ammusic.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.ammusic.data.MusicRepository
import app.ammusic.youtube.model.YtAlbum
import app.ammusic.youtube.model.YtArtistPage
import app.ammusic.youtube.model.YtBrowsePage
import app.ammusic.youtube.model.YtTrack
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class LoadStatus { Loading, Ready, Error }

/** Au-delà, on arrête de demander la suite d'une playlist (20 pages d'une centaine de titres). */
private const val MaxPlaylistPages = 20

data class CollectionState(
    val isAlbum: Boolean,
    val title: String,
    val subtitle: String,
    val thumbnail: String?,
    val artistId: String? = null,
    val year: Int? = null,
    val description: String? = null,
    val tracks: List<YtTrack> = emptyList(),
    val otherVersions: List<YtAlbum> = emptyList(),
    val status: LoadStatus = LoadStatus.Loading,
)

@HiltViewModel
class CollectionViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repository: MusicRepository,
) : ViewModel() {
    val route = savedState.toRoute<CollectionRoute>()

    private val _state = MutableStateFlow(CollectionState(route.album, route.title, route.subtitle, route.thumbnail))
    val state: StateFlow<CollectionState> = _state.asStateFlow()
    private var job: Job? = null

    init {
        load()
    }

    fun load() {
        job?.cancel()
        _state.update { it.copy(status = LoadStatus.Loading) }
        job = viewModelScope.launch { if (route.album) loadAlbum() else loadPlaylist() }
    }

    private suspend fun loadAlbum() {
        val page = repository.album(route.id).getOrElse { return fail() }
        _state.update {
            it.copy(
                title = page.album.title,
                subtitle = page.album.subtitle.ifBlank { it.subtitle },
                thumbnail = it.thumbnail ?: page.album.thumbnail,
                artistId = page.album.artistId,
                year = page.album.year,
                tracks = page.tracks,
                otherVersions = page.otherVersions,
                status = LoadStatus.Ready,
            )
        }
    }

    /** Affiche la première page tout de suite, puis complète la liste en arrière-plan. */
    private suspend fun loadPlaylist() {
        val page = repository.playlist(route.id).getOrElse { return fail() }
        _state.update {
            it.copy(
                title = page.playlist.title,
                subtitle = page.playlist.subtitle.ifBlank { it.subtitle },
                thumbnail = it.thumbnail ?: page.playlist.thumbnail,
                description = page.description,
                tracks = page.tracks,
                status = LoadStatus.Ready,
            )
        }
        var token = page.continuation
        var pages = 0
        while (token != null && pages++ < MaxPlaylistPages) {
            val (tracks, next) = repository.playlistMore(token).getOrNull() ?: break
            _state.update { it.copy(tracks = (it.tracks + tracks).distinctBy(YtTrack::id)) }
            token = next
        }
    }

    private fun fail() = _state.update { it.copy(status = LoadStatus.Error) }
}

data class ArtistState(
    val name: String,
    val thumbnail: String?,
    val page: YtArtistPage? = null,
    val status: LoadStatus = LoadStatus.Loading,
)

@HiltViewModel
class ArtistViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repository: MusicRepository,
) : ViewModel() {
    val route = savedState.toRoute<ArtistRoute>()

    private val _state = MutableStateFlow(ArtistState(route.name, route.thumbnail))
    val state: StateFlow<ArtistState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(status = LoadStatus.Loading) }
        viewModelScope.launch {
            val page = repository.artist(route.id).getOrNull()
            _state.update {
                if (page == null) {
                    it.copy(status = LoadStatus.Error)
                } else {
                    it.copy(
                        name = page.artist.title.ifBlank { it.name },
                        thumbnail = it.thumbnail ?: page.artist.thumbnail,
                        page = page,
                        status = LoadStatus.Ready,
                    )
                }
            }
        }
    }
}

data class BrowseState(val title: String, val page: YtBrowsePage? = null, val status: LoadStatus = LoadStatus.Loading)

@HiltViewModel
class BrowseViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repository: MusicRepository,
) : ViewModel() {
    private val route = savedState.toRoute<BrowseRoute>()

    private val _state = MutableStateFlow(BrowseState(route.title))
    val state: StateFlow<BrowseState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(status = LoadStatus.Loading) }
        viewModelScope.launch {
            val result = if (route.artist) repository.artistItems(route.browseId, route.params) else repository.mood(route.browseId, route.params)
            _state.update { state ->
                result.fold(
                    onSuccess = { state.copy(title = state.title.ifBlank { it.title }, page = it, status = LoadStatus.Ready) },
                    onFailure = { state.copy(status = LoadStatus.Error) },
                )
            }
        }
    }
}
