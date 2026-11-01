package com.radwan.vdl

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var worker: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel()
        ServiceCompat.startForeground(
            this, NOTIF_ID, notification("جاري التحضير…", 0, 0),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
        if (worker?.isActive != true) {
            worker = scope.launch {
                DownloadManager.runAll(applicationContext) { done, total, current ->
                    getSystemService(NotificationManager::class.java)
                        .notify(NOTIF_ID, notification(current ?: "", done, total))
                }
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "التنزيلات", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun notification(text: String, done: Int, total: Int): Notification =
        NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(if (total > 0) "تنزيل $done / $total" else "Video Downloader")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(total, done, total == 0)
            .build()

    companion object {
        private const val CHANNEL = "downloads"
        private const val NOTIF_ID = 1

        fun start(ctx: Context) =
            ContextCompat.startForegroundService(ctx, Intent(ctx, DownloadService::class.java))
    }
}
