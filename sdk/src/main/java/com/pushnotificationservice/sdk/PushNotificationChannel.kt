package com.pushnotificationservice.sdk

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

internal const val DEFAULT_CHANNEL_ID = "pushnotificationservice_default"

/** The channel the SDK posts to (required API 26+). */
object PushNotificationChannel {
    // Written from the host's main thread (configureChannel(), typically called at
    // app startup) and read from FCM's delivery thread (ensureCreated()) — @Volatile
    // guarantees a write on one thread is visible to a read on the other, without
    // which a thread could keep observing a stale/null value indefinitely.
    @Volatile
    private var channelName: String? = null
    @Volatile
    private var channelImportance: Int? = null

    /** Optional: call before the first message to customize name/importance. */
    fun configureChannel(name: String? = null, importance: Int? = null) {
        channelName = name
        channelImportance = importance
    }

    internal fun ensureCreated(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            DEFAULT_CHANNEL_ID,
            channelName ?: "Push notifications",
            channelImportance ?: NotificationManager.IMPORTANCE_DEFAULT
        )
        manager.createNotificationChannel(channel)
    }

    internal fun resetForTesting() {
        channelName = null
        channelImportance = null
    }
}
