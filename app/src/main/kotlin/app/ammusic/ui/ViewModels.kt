package app.ammusic.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ammusic.data.MusicRepository
import app.ammusic.data.SearchHistory
import app.ammusic.playback.PlayerController
import app.ammusic.youtube.model.YtItem
import app.ammusic.youtube.model.YtMood
import app.ammusic.youtube.model.YtSearchFilter
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

    fun playAll(tracks: List<YtTrack>, startIndex: Int = 0, shuffle: Boolean? = null) = controller.playAll(tracks, startIndex, shuffle)

    fun playNext(track: YtTrack) = controller.playNext(track)

    fun addToQueue(track: YtTrack) = controller.addToQueue(track)

    val positionMs: Long get() = controller.positionMs

    fun toggle() = controller.toggle()

    fun next() = controller.next()

    fun previous() = controller.previous()

    fun seekTo(positionMs: Long) = controller.seekTo(positionMs)

    fun playAt(index: Int) = controller.playAt(index)

    fun move(from: Int, to: Int) = controller.move(from, to)

    fun remove(index: Int) = controller.remove(index)

    fun toggleShuffle() = controller.toggleShuffle()

    fun cycleRepeat() = controller.cycleRepeat()

    fun toggleLike() = controller.toggleLike()
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

    /** Résultats d'un seul type ; [continuation] est le jeton de la page suivante, s'il en reste. */
    data class Filtered(val items: List<YtItem>, val continuation: String?) : SearchResults
}

/** [filter] vaut `null` pour « Tout » (résultats groupés). */
data class SearchState(
    val query: String = "",
    val suggestions: List<String> = emptyList(),
    val results: SearchResults = SearchResults.None,
    val filter: YtSearchFilter? = null,
    val history: List<String> = emptyList(),
    val moods: List<YtMood> = emptyList(),
)

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: MusicRepository,
    private val history: SearchHistory,
) : ViewModel() {
    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()

    private val typed = MutableStateFlow("")
    private var search: Job? = null
    private var more: Job? = null

    init {
        viewModelScope.launch {
            history.entries.collect { entries -> _state.update { it.copy(history = entries) } }
        }
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
        _state.update {
            it.copy(
                query = query,
                results = SearchResults.None,
                suggestions = if (query.isBlank()) emptyList() else it.suggestions,
                filter = if (query.isBlank()) null else it.filter,
            )
        }
    }

    fun submit(query: String = _state.value.query) {
        if (query.isBlank()) return
        history.add(query)
        typed.value = query
        _state.update { it.copy(query = query) }
        launchSearch()
    }

    fun onFilter(filter: YtSearchFilter?) {
        if (_state.value.filter == filter) return
        _state.update { it.copy(filter = filter) }
        launchSearch()
    }

    fun forget(query: String) = history.remove(query)

    fun clearHistory() = history.clear()

    private fun launchSearch() {
        val (query, filter) = _state.value.let { it.query to it.filter }
        search?.cancel()
        more?.cancel()
        _state.update { it.copy(results = SearchResults.Loading) }
        search = viewModelScope.launch {
            val results = if (filter == null) {
                repository.search(query).fold({ SearchResults.Content(it) }, { SearchResults.Error })
            } else {
                repository.search(query, filter).fold({ SearchResults.Filtered(it.items, it.continuation) }, { SearchResults.Error })
            }
            _state.update { it.copy(results = results) }
        }
    }

    /** Charge la page suivante des résultats filtrés (appelé quand on arrive en bas de la liste). */
    fun loadMore() {
        val current = _state.value.results as? SearchResults.Filtered ?: return
        val token = current.continuation ?: return
        if (more?.isActive == true) return
        more = viewModelScope.launch {
            val next = repository.searchMore(token).fold(
                onSuccess = { page -> SearchResults.Filtered((current.items + page.items).distinctBy { it.key }, page.continuation) },
                onFailure = { current.copy(continuation = null) },
            )
            _state.update { if (it.results === current) it.copy(results = next) else it }
        }
    }
}
