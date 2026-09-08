package com.e2s.kospiscreener

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.e2s.kospiscreener.alerts.DailyCheckWorker

class ScreenerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.channel_name), NotificationManager.IMPORTANCE_HIGH)
                .apply { description = getString(R.string.channel_desc) }
        )
        DailyCheckWorker.schedule(this)
    }

    companion object {
        const val CHANNEL_ID = "screener_alerts"
    }
}
