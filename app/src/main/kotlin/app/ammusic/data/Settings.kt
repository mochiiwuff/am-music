package app.ammusic.data

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import androidx.core.content.edit
import app.ammusic.youtube.YouTubeMusic
import app.ammusic.youtube.appLanguage
import app.ammusic.youtube.isFrench
import app.ammusic.youtube.stream.AudioQuality
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

data class Settings(
    val theme: Int = 0,
    val accent: Int = 0,
    val language: String = "",
    val wifiQuality: AudioQuality = AudioQuality.Auto,
    val mobileQuality: AudioQuality = AudioQuality.Auto,
    val autoplay: Boolean = true,
    val skipSegments: Boolean = false,
    val lyricsSize: Int = 1,
    val wordByWord: Boolean = true,
    val historyPaused: Boolean = false,
    val cacheMb: Int = 512,
)

val CacheChoicesMb = listOf(256, 512, 1024, 2048)

object UserSettings {
    private val _state = MutableStateFlow(Settings())
    val state: StateFlow<Settings> = _state.asStateFlow()
    val current: Settings get() = _state.value

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        val stored = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        prefs = stored
        _state.value = Settings(
            theme = stored.getInt("theme", 0).coerceIn(0, 2),
            accent = stored.getInt("accent", 0).coerceAtLeast(0),
            language = stored.getString("language", "").orEmpty(),
            wifiQuality = quality(stored.getString("wifiQuality", null)),
            mobileQuality = quality(stored.getString("mobileQuality", null)),
            autoplay = stored.getBoolean("autoplay", true),
            skipSegments = stored.getBoolean("skipSegments", false),
            lyricsSize = stored.getInt("lyricsSize", 1).coerceIn(0, 2),
            wordByWord = stored.getBoolean("wordByWord", true),
            historyPaused = stored.getBoolean("historyPaused", false),
            cacheMb = stored.getInt("cacheMb", 512).takeIf { it in CacheChoicesMb } ?: 512,
        )
        appLanguage = current.language.ifEmpty { null }
    }

    fun update(change: Settings.() -> Settings) {
        val next = current.change()
        if (next == current) return
        val languageChanged = next.language != current.language
        _state.value = next
        prefs?.edit {
            putInt("theme", next.theme)
            putInt("accent", next.accent)
            putString("language", next.language)
            putString("wifiQuality", next.wifiQuality.name)
            putString("mobileQuality", next.mobileQuality.name)
            putBoolean("autoplay", next.autoplay)
            putBoolean("skipSegments", next.skipSegments)
            putInt("lyricsSize", next.lyricsSize)
            putBoolean("wordByWord", next.wordByWord)
            putBoolean("historyPaused", next.historyPaused)
            putInt("cacheMb", next.cacheMb)
        }
        if (languageChanged) {
            appLanguage = next.language.ifEmpty { null }
            YouTubeMusic.setLanguage(Locale.getDefault().country.ifEmpty { "US" }, if (isFrench) "fr" else "en")
        }
    }

    fun streamQuality(context: Context): AudioQuality {
        val metered = context.getSystemService(ConnectivityManager::class.java)?.isActiveNetworkMetered == true
        return if (metered) current.mobileQuality else current.wifiQuality
    }

    private fun quality(name: String?): AudioQuality = AudioQuality.entries.firstOrNull { it.name == name } ?: AudioQuality.Auto
}
