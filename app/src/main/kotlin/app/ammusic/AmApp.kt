package app.ammusic

import android.app.Application
import app.ammusic.data.AccountStore
import app.ammusic.youtube.YouTubeMusic
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
        YouTubeMusic.init(this)
        account.restore()
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context).crossfade(true).build()
}
