package app.ammusic.playback

import android.content.Context
import android.content.Intent
import android.media.MediaRouter2
import android.os.Build
import android.provider.Settings

fun showAudioOutputs(context: Context) {
    if (Build.VERSION.SDK_INT >= 34 && runCatching { MediaRouter2.getInstance(context).showSystemOutputSwitcher() }.getOrDefault(false)) return
    if (Build.VERSION.SDK_INT >= 30) {
        val dialog = Intent("com.android.systemui.action.LAUNCH_MEDIA_OUTPUT_DIALOG")
            .setPackage("com.android.systemui")
            .putExtra("package_name", context.packageName)
        if (runCatching { context.sendBroadcast(dialog) }.isSuccess) return
    }
    runCatching { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
