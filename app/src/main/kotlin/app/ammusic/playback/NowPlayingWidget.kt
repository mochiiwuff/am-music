package app.ammusic.playback

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.view.KeyEvent
import android.widget.RemoteViews
import androidx.palette.graphics.Palette
import app.ammusic.MainActivity
import app.ammusic.R
import app.ammusic.youtube.model.thumbnailAt
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class WidgetContent(val title: String?, val artist: String?, val playing: Boolean, val cover: Bitmap?, val tint: Int?)

class NowPlayingWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val shown = last
        if (shown != null) {
            manager.updateAppWidget(ids, views(context, shown))
            return
        }
        val saved = QueueStore.load(context)?.current
        val idle = WidgetContent(saved?.title, saved?.artists, false, null, null)
        manager.updateAppWidget(ids, views(context, idle))
        val thumbnail = saved?.thumbnail ?: return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val (cover, tint) = artwork(context, thumbnail.thumbnailAt(320))
                manager.updateAppWidget(ids, views(context, WidgetContent(idle.title, idle.artist, false, cover, tint)))
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        @Volatile
        private var last: WidgetContent? = null

        fun push(context: Context, content: WidgetContent) {
            last = content
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, NowPlayingWidget::class.java))
            if (ids.isNotEmpty()) manager.updateAppWidget(ids, views(context, content))
        }

        fun hasWidgets(context: Context): Boolean =
            AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, NowPlayingWidget::class.java)).isNotEmpty()

        suspend fun artwork(context: Context, url: String): Pair<Bitmap?, Int?> {
            val request = ImageRequest.Builder(context).data(url).size(320).allowHardware(false).build()
            val bitmap = (SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image?.toBitmap() ?: return null to null
            val swatch = Palette.from(bitmap).generate()
            val tint = swatch.getVibrantColor(swatch.getDominantColor(0)).takeIf { it != 0 }
            return bitmap to tint
        }

        private fun views(context: Context, content: WidgetContent): RemoteViews =
            RemoteViews(context.packageName, R.layout.widget_now_playing).apply {
                setTextViewText(R.id.widget_title, content.title ?: context.getString(R.string.app_name))
                setTextViewText(R.id.widget_artist, content.artist?.takeIf { it.isNotBlank() } ?: context.getString(R.string.widget_idle))
                if (content.cover != null) setImageViewBitmap(R.id.widget_cover, content.cover) else setImageViewResource(R.id.widget_cover, R.mipmap.ic_launcher)
                setImageViewResource(R.id.widget_play, if (content.playing) R.drawable.ic_widget_pause else R.drawable.ic_widget_play)
                setInt(R.id.widget_glow, "setColorFilter", content.tint ?: 0xFFFF3B5C.toInt())
                setInt(R.id.widget_glow, "setImageAlpha", if (content.tint != null) 150 else 70)
                val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                setOnClickPendingIntent(R.id.widget_root, open)
                setOnClickPendingIntent(R.id.widget_play, if (content.title == null) open else media(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, foreground = true))
                setOnClickPendingIntent(R.id.widget_next, media(context, KeyEvent.KEYCODE_MEDIA_NEXT, foreground = false))
                setOnClickPendingIntent(R.id.widget_previous, media(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS, foreground = false))
            }

        private fun media(context: Context, keyCode: Int, foreground: Boolean): PendingIntent {
            val intent = Intent(Intent.ACTION_MEDIA_BUTTON)
                .setComponent(ComponentName(context, PlaybackService::class.java))
                .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            return if (foreground && Build.VERSION.SDK_INT >= 26) {
                PendingIntent.getForegroundService(context, keyCode, intent, flags)
            } else {
                PendingIntent.getService(context, keyCode, intent, flags)
            }
        }
    }
}
