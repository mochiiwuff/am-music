package app.ammusic.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ammusic.data.AccountStore
import app.ammusic.data.library.LibraryRepository
import app.ammusic.data.downloads.DownloadRepository
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

object LibraryKind {
    const val Playlists = "playlists"
    const val Artists = "artists"
    const val Albums = "albums"
    const val Liked = "liked"
    const val History = "history"
    const val Downloads = "downloads"
    const val Recap = "recap"
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
class LibraryViewModel @Inject constructor(
    private val library: LibraryRepository,
    private val downloads: DownloadRepository,
    store: AccountStore,
) : ViewModel() {
    val account = store.account
    val liked = library.liked.shared()
    val history = library.history.shared()
    val albums = library.albums.shared()
    val artists = library.artists.shared()
    val playlists = library.playlists.shared()
    val likedIds = library.likedIds
    val notices = library.notices
    val downloadStates = downloads.states
    val downloadRunning = downloads.running
    val downloadBytes = downloads.bytes
    val downloaded = downloads.tracks.shared()

    val recent: StateFlow<List<YtItem>> = combine(library.albums, library.playlists, library.artists) { albums, playlists, artists ->
        val lists = listOf(albums, playlists, artists)
        List(lists.maxOf { it.size }) { index -> lists.mapNotNull { it.getOrNull(index) } }.flatten().take(12)
    }.shared()

    private fun <T> Flow<List<T>>.shared(): StateFlow<List<T>> = stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun refresh() {
        viewModelScope.launch { library.sync(force = false) }
    }

    fun toggleLike(track: YtTrack) = library.toggleLike(track)

    fun addToPlaylist(playlist: YtPlaylist, track: YtTrack) = library.addToPlaylist(playlist.id, track)

    fun createPlaylist(title: String, then: (String) -> Unit = {}) {
        viewModelScope.launch { library.createPlaylist(title)?.let(then) }
    }

    fun addToNewPlaylist(title: String, track: YtTrack) = createPlaylist(title) { id -> library.addToPlaylist(id, track) }

    fun clearHistory() = library.clearHistory()

    fun download(tracks: List<YtTrack>) = downloads.download(tracks)

    fun removeDownloads(ids: Collection<String>) = downloads.remove(ids)
}
