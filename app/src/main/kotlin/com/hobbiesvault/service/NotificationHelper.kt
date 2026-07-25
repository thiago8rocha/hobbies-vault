package com.hobbiesvault.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

object NotificationHelper {
    private const val CHANNEL_ID = "manga_status_changes"
    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
        val channel = NotificationChannel(CHANNEL_ID, "Atualizações de mangás", NotificationManager.IMPORTANCE_DEFAULT)
        manager.createNotificationChannel(channel)
    }

    private val manager get() = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun notifyStatusChange(itemId: Int, title: String, message: String) {
        if (!::appContext.isInitialized || !manager.areNotificationsEnabled()) return
        val notification = Notification.Builder(appContext, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .build()
        manager.notify(itemId, notification)
    }
}
