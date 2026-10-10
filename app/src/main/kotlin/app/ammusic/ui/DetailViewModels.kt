package app.ammusic.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.ammusic.data.MusicRepository
import app.ammusic.data.library.LibraryRepository
import app.ammusic.youtube.model.YtAlbum
import app.ammusic.youtube.model.YtArtist
import app.ammusic.youtube.model.YtArtistPage
import app.ammusic.youtube.model.YtBrowsePage
import app.ammusic.youtube.model.YtPlaylist
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
    val playlistId: String = "",
    val editable: Boolean = false,
)

@HiltViewModel
class CollectionViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repository: MusicRepository,
    private val library: LibraryRepository,
) : ViewModel() {
    val route = savedState.toRoute<CollectionRoute>()
    private val local = !route.album && library.isLocal(route.id)

    private val _state = MutableStateFlow(CollectionState(route.album, route.title, route.subtitle, route.thumbnail))
    val state: StateFlow<CollectionState> = _state.asStateFlow()
    val savedIds = library.savedIds
    private var job: Job? = null

    init {
        load()
    }

    fun load() {
        job?.cancel()
        _state.update { it.copy(status = LoadStatus.Loading) }
        job = viewModelScope.launch {
            when {
                local -> watchLocal()
                route.album -> loadAlbum()
                else -> loadPlaylist()
            }
        }
    }

    private suspend fun watchLocal() {
        library.localPlaylist(route.id).collect { (playlist, tracks) ->
            _state.update {
                it.copy(
                    title = playlist?.title ?: it.title,
                    subtitle = playlist?.subtitle ?: it.subtitle,
                    thumbnail = playlist?.thumbnail ?: it.thumbnail,
                    tracks = tracks,
                    editable = true,
                    status = LoadStatus.Ready,
                )
            }
        }
    }

    fun toggleSaved() {
        val current = _state.value
        if (current.isAlbum) {
            library.toggleAlbum(YtAlbum(route.id, current.playlistId, current.title, current.subtitle, current.thumbnail, current.artistId, current.year))
        } else {
            library.togglePlaylist(YtPlaylist(route.id, current.title, current.subtitle, current.thumbnail))
        }
    }

    fun rename(title: String) {
        if (title.isBlank()) return
        _state.update { it.copy(title = title.trim()) }
        library.renamePlaylist(route.id, title.trim())
    }

    fun delete() = library.deletePlaylist(route.id)

    fun remove(track: YtTrack) {
        if (!local) _state.update { it.copy(tracks = it.tracks - track) }
        library.removeFromPlaylist(route.id, track)
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
                playlistId = page.album.playlistId,
                status = LoadStatus.Ready,
            )
        }
    }

    private suspend fun loadPlaylist() {
        val page = repository.playlist(route.id).getOrElse { return fail() }
        _state.update {
            it.copy(
                title = page.playlist.title,
                subtitle = page.playlist.subtitle.ifBlank { it.subtitle },
                thumbnail = it.thumbnail ?: page.playlist.thumbnail,
                description = page.description,
                editable = page.playlist.editable,
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
    private val library: LibraryRepository,
) : ViewModel() {
    val route = savedState.toRoute<ArtistRoute>()

    private val _state = MutableStateFlow(ArtistState(route.name, route.thumbnail))
    val state: StateFlow<ArtistState> = _state.asStateFlow()
    val savedIds = library.savedIds

    fun toggleFollow() = library.toggleArtist(YtArtist(route.id, _state.value.name, _state.value.thumbnail))

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
