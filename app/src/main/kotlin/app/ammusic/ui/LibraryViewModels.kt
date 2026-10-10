package app.ammusic.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ammusic.data.AccountStore
import app.ammusic.data.library.LibraryRepository
import app.ammusic.youtube.model.YtItem
import app.ammusic.youtube.model.YtPlaylist
import app.ammusic.youtube.model.YtSession
import app.ammusic.youtube.model.YtTrack
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Les listes de la bibliothèque, dans l'ordre où l'écran les propose. */
object LibraryKind {
    const val Playlists = "playlists"
    const val Artists = "artists"
    const val Albums = "albums"
    const val Liked = "liked"
    const val History = "history"
}

@HiltViewModel
class AccountViewModel @Inject constructor(private val store: AccountStore) : ViewModel() {
    val account = store.account

    fun signIn(session: YtSession, onDone: () -> Unit) {
        viewModelScope.launch {
            store.signIn(session)
            onDone()
        }
    }

    fun signOut() {
        viewModelScope.launch { store.signOut() }
    }
}

@HiltViewModel
class LibraryViewModel @Inject constructor(private val library: LibraryRepository, store: AccountStore) : ViewModel() {
    val account = store.account
    val liked = library.liked.shared()
    val history = library.history.shared()
    val albums = library.albums.shared()
    val artists = library.artists.shared()
    val playlists = library.playlists.shared()
    val likedIds = library.likedIds

    /** Derniers albums, playlists et artistes ajoutés, pour la grille de l'écran Bibliothèque. */
    val recent: StateFlow<List<YtItem>> = combine(library.albums, library.playlists, library.artists) { albums, playlists, artists ->
        // Chaque liste est déjà triée du plus récent au plus ancien : on les entrelace pour un aperçu varié.
        val lists = listOf(albums, playlists, artists)
        List(lists.maxOf { it.size }) { index -> lists.mapNotNull { it.getOrNull(index) } }.flatten().take(12)
    }.shared()

    private fun <T> Flow<List<T>>.shared(): StateFlow<List<T>> = stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Recharge la bibliothèque du compte si la dernière copie date un peu. */
    fun refresh() {
        viewModelScope.launch { library.sync(force = false) }
    }

    fun toggleLike(track: YtTrack) = library.toggleLike(track)

    fun addToPlaylist(playlist: YtPlaylist, track: YtTrack) = library.addToPlaylist(playlist.id, track)

    /** Crée la playlist [title] ; [then] reçoit son identifiant si la création a réussi. */
    fun createPlaylist(title: String, then: (String) -> Unit = {}) {
        viewModelScope.launch { library.createPlaylist(title)?.let(then) }
    }

    fun addToNewPlaylist(title: String, track: YtTrack) = createPlaylist(title) { id -> library.addToPlaylist(id, track) }

    fun clearHistory() = library.clearHistory()
}
