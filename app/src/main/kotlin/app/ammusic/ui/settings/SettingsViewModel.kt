package app.ammusic.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ammusic.data.Settings
import app.ammusic.data.UserSettings
import app.ammusic.data.library.LibraryRepository
import app.ammusic.data.downloads.DownloadRepository
import app.ammusic.data.Backup
import android.net.Uri
import app.ammusic.youtube.tr
import app.ammusic.playback.PlayerController
import app.ammusic.playback.audioCacheBytes
import app.ammusic.playback.clearAudioCache
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val player: PlayerController,
    private val library: LibraryRepository,
    private val downloads: DownloadRepository,
    private val backup: Backup,
) : ViewModel() {
    val settings = UserSettings.state
    val sleep = player.sleep
    val downloadBytes = downloads.bytes

    private val _cacheBytes = MutableStateFlow(0L)
    val cacheBytes: StateFlow<Long> = _cacheBytes.asStateFlow()

    init {
        measureCache()
    }

    fun update(change: Settings.() -> Settings) = UserSettings.update(change)

    fun sleepIn(minutes: Int?) = player.sleepIn(minutes)

    fun sleepAtEndOfTrack(enabled: Boolean) = if (enabled) player.sleepAtEndOfTrack() else player.sleepIn(null)

    fun clearHistory() = library.clearHistory()

    fun clearPlays() = library.clearPlays()

    fun removeDownloads() = downloads.removeAll()

    fun backupName() = backup.suggestedName()

    fun exportTo(target: Uri) {
        viewModelScope.launch {
            backup.export(target).fold(
                onSuccess = { library.announce(tr("Sauvegarde enregistrée", "Backup saved")) },
                onFailure = { library.announce(tr("La sauvegarde a échoué", "Backup failed")) },
            )
        }
    }

    fun importFrom(source: Uri) {
        viewModelScope.launch {
            backup.import(source).fold(
                onSuccess = { summary ->
                    library.announce(tr("Restauré : ${summary.playlists} playlists, ${summary.liked} titres aimés", "Restored: ${summary.playlists} playlists, ${summary.liked} liked songs"))
                },
                onFailure = { library.announce(tr("Ce fichier n'a pas pu être lu", "This file could not be read")) },
            )
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { runCatching { clearAudioCache(context, keep = player.state.value.track?.id) } }
            measureCache()
        }
    }

    private fun measureCache() {
        viewModelScope.launch {
            _cacheBytes.value = withContext(Dispatchers.IO) { runCatching { audioCacheBytes(context) }.getOrDefault(0L) }
        }
    }
}
