package io.github.viyh.freedrift

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

class FreeDriftApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_PLAYBACK,
            getString(R.string.channel_playback),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
        }
        nm.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_PLAYBACK = "playback"
        const val NOTIF_ID = 1001
    }
}
