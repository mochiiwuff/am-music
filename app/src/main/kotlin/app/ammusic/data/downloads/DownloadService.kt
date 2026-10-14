package app.ammusic.data.downloads

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import app.ammusic.MainActivity
import app.ammusic.R
import app.ammusic.youtube.tr
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val Channel = "downloads"
private const val NotificationId = 7

@AndroidEntryPoint
class DownloadService : Service() {
    @Inject lateinit var downloads: DownloadRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var lastPercent = -1

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26 && manager.getNotificationChannel(Channel) == null) {
            manager.createNotificationChannel(NotificationChannel(Channel, tr("Téléchargements", "Downloads"), NotificationManager.IMPORTANCE_LOW))
        }
        ServiceCompat.startForeground(
            this,
            NotificationId,
            notification(tr("Préparation…", "Preparing…"), null, 0f),
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
        )
        if (job?.isActive != true) {
            job = scope.launch {
                downloads.runQueue { progress ->
                    val percent = (progress.fraction * 100).toInt()
                    if (percent != lastPercent) {
                        lastPercent = percent
                        val count = tr("${progress.done + 1} sur ${progress.total}", "${progress.done + 1} of ${progress.total}")
                        manager.notify(NotificationId, notification(progress.title, count, progress.fraction))
                    }
                }
                ServiceCompat.stopForeground(this@DownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun notification(title: String, detail: String?, fraction: Float): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, Channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(detail ?: tr("Téléchargement", "Downloading"))
            .setProgress(100, (fraction * 100).toInt(), detail == null)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(open)
            .build()
    }
}
