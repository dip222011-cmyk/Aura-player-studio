package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.example.data.local.AuraSeedData
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class AuraApplication : Application() {

    @Inject
    lateinit var seedData: AuraSeedData

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        appScope.launch {
            runCatching { seedData.ensureSeeded() }
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java) ?: return
            val playbackChannel = NotificationChannel(
                CHANNEL_PLAYBACK,
                getString(R.string.channel_playback_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.channel_playback_desc)
            }
            val transferChannel = NotificationChannel(
                CHANNEL_TRANSFER,
                getString(R.string.channel_transfer_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.channel_transfer_desc)
            }
            val exportChannel = NotificationChannel(
                CHANNEL_EXPORT,
                getString(R.string.channel_export_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.channel_export_desc)
            }
            manager.createNotificationChannels(
                listOf(playbackChannel, transferChannel, exportChannel)
            )
        }
    }

    companion object {
        const val CHANNEL_PLAYBACK = "aura_playback_channel"
        const val CHANNEL_TRANSFER = "aura_transfer_channel"
        const val CHANNEL_EXPORT = "aura_export_channel"
    }
}
