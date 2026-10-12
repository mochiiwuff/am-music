package app.ammusic

import android.app.Application
import app.ammusic.data.AccountStore
import app.ammusic.data.UserSettings
import app.ammusic.youtube.YouTubeMusic
import app.ammusic.youtube.isFrench
import java.util.Locale
import javax.inject.Inject
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.crossfade
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class AmApp : Application(), SingletonImageLoader.Factory {
    @Inject lateinit var account: AccountStore

    override fun onCreate() {
        super.onCreate()
        UserSettings.init(this)
        val locale = Locale.getDefault()
        YouTubeMusic.init(this, country = locale.country.ifEmpty { "US" }, language = if (isFrench) "fr" else "en")
        account.restore()
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context).crossfade(true).build()
}
