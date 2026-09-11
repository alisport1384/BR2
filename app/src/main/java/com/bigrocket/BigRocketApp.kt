package com.bigrocket

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import com.bigrocket.service.AppLogger

class BigRocketApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLogger.init(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel("bigrocket_vpn_channel", "BigRocket", NotificationManager.IMPORTANCE_LOW).apply {
                    setShowBadge(false)
                }
            )
        }
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            AppLogger.logError("Crash", "FATAL on thread '${thread.name}'", throwable)
            previous?.uncaughtException(thread, throwable)
        }
    }
}
