package app.ammusic.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ammusic.data.MusicRepository
import app.ammusic.playback.PlayerController
import app.ammusic.youtube.model.YtMood
import app.ammusic.youtube.model.YtSection
import app.ammusic.youtube.model.YtTrack
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(private val controller: PlayerController) : ViewModel() {
    val state = controller.state

    fun play(track: YtTrack) = controller.play(track)

    fun toggle() = controller.toggle()

    fun next() = controller.next()
}

sealed interface HomeState {
    data object Loading : HomeState
    data object Error : HomeState
    data class Content(val sections: List<YtSection>, val moods: List<YtMood>) : HomeState
}

@HiltViewModel
class HomeViewModel @Inject constructor(private val repository: MusicRepository) : ViewModel() {
    private val _state = MutableStateFlow<HomeState>(HomeState.Loading)
    val state: StateFlow<HomeState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = HomeState.Loading
        viewModelScope.launch {
            val moods = async { repository.moods().getOrDefault(emptyList()) }
            _state.value = repository.home().fold(
                onSuccess = { HomeState.Content(it, moods.await()) },
                onFailure = { HomeState.Error },
            )
        }
    }
}

sealed interface SearchResults {
    data object None : SearchResults
    data object Loading : SearchResults
    data object Error : SearchResults
    data class Content(val sections: List<YtSection>) : SearchResults
}

data class SearchState(
    val query: String = "",
    val suggestions: List<String> = emptyList(),
    val results: SearchResults = SearchResults.None,
    val moods: List<YtMood> = emptyList(),
)

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(private val repository: MusicRepository) : ViewModel() {
    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()

    private val typed = MutableStateFlow("")
    private var search: Job? = null

    init {
        viewModelScope.launch {
            val moods = repository.moods().getOrDefault(emptyList())
            _state.update { it.copy(moods = moods) }
        }
        viewModelScope.launch {
            typed.debounce(250).collectLatest { query ->
                val suggestions = if (query.isBlank()) emptyList() else repository.suggestions(query).getOrDefault(emptyList())
                _state.update { if (it.query == query) it.copy(suggestions = suggestions) else it }
            }
        }
    }

    fun onQuery(query: String) {
        search?.cancel()
        typed.value = query
        _state.update { it.copy(query = query, results = SearchResults.None, suggestions = if (query.isBlank()) emptyList() else it.suggestions) }
    }

    fun submit(query: String = _state.value.query) {
        if (query.isBlank()) return
        search?.cancel()
        typed.value = query
        _state.update { it.copy(query = query, results = SearchResults.Loading) }
        search = viewModelScope.launch {
            val results = repository.search(query).fold(
                onSuccess = { SearchResults.Content(it) },
                onFailure = { SearchResults.Error },
            )
            _state.update { it.copy(results = results) }
        }
    }
}
