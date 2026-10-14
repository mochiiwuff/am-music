package app.ammusic.playback

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlin.math.pow

object PlaybackFlags {
    @Volatile
    var stopAtEnd = false
}

object Loudness {
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) prefs = context.applicationContext.getSharedPreferences("loudness", Context.MODE_PRIVATE)
    }

    fun put(trackId: String, decibels: Double?) {
        if (decibels == null) return
        prefs?.edit { putFloat(trackId, decibels.toFloat()) }
    }

    fun gain(trackId: String?): Float {
        val decibels = trackId?.let { prefs?.getFloat(it, Float.NaN) } ?: return 1f
        if (decibels.isNaN()) return 1f
        return 10f.pow(-decibels / 20f).coerceIn(0.1f, 1f)
    }
}
