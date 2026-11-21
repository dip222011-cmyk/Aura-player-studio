package com.example.share

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.AuraApplication
import com.example.R

class ShareTransferService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val title = intent?.getStringExtra(EXTRA_TITLE) ?: "AURA Share Active"
        val text = intent?.getStringExtra(EXTRA_TEXT) ?: "Encrypted local transfer in progress"
        val notification = NotificationCompat.Builder(this, AuraApplication.CHANNEL_TRANSFER)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_TEXT = "extra_text"
        private const val NOTIFICATION_ID = 4401
    }
}
