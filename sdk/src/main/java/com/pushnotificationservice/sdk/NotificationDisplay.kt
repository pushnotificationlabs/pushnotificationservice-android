package com.pushnotificationservice.sdk

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.net.HttpURLConnection
import java.net.URL

private const val TAG = "PushNotificationService"

/** Used only when the payload carries no `campaign` id to derive a per-campaign
 * notification id from — see FALLBACK_ID usage below. */
private const val FALLBACK_ID = 1
internal const val EXTRA_TAP_URL = "com.pushnotificationservice.sdk.TAP_URL"

/**
 * Call from your FirebaseMessagingService.onMessageReceived, passing fields
 * extracted from RemoteMessage.notification (title/body/image) and
 * RemoteMessage.data (url/displayedUrl/campaign) separately — the backend's
 * FCM payload puts them in different blocks. Only reached while the app is
 * foregrounded; see the plan's Global Constraints for why background
 * delivery can't call this.
 *
 * Requires the host app to have been granted android.permission.POST_NOTIFICATIONS
 * on API 33+ (see README) — if it hasn't, this silently no-ops (logs a warning,
 * does not throw).
 */
fun PushNotificationService.onMessageReceived(context: Context, title: String?, body: String?, image: String?, data: Map<String, String>) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) {
        Log.w(TAG, "Skipping notification: POST_NOTIFICATIONS permission not granted")
        return // host app hasn't been granted permission yet — no-op, don't crash
    }

    if (context.applicationInfo.icon == 0) {
        // NotificationCompat.Builder.setSmallIcon(0) / notify() throws
        // IllegalArgumentException uncaught, which would propagate out of
        // FirebaseMessagingService.onMessageReceived and take down that call.
        // This SDK's posture everywhere else is no-op-not-crash; match it here.
        Log.w(TAG, "Skipping notification: host app has no launcher icon (applicationInfo.icon == 0)")
        return
    }

    PushNotificationChannel.ensureCreated(context)

    // Scope both the notification id and its PendingIntent's request code to the
    // campaign so distinct campaigns get distinct tray entries and non-colliding
    // pending intents, matching the backend's per-campaign FCM collapse_key.
    val notificationId = data["campaign"]?.hashCode() ?: FALLBACK_ID

    // getLaunchIntentForPackage reliably resolves to the host app's launcher
    // Activity — a data-less ACTION_VIEW intent (the old approach) won't match
    // any intent-filter on a real device and fails to resolve to anything.
    val pendingIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { launchIntent ->
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        data["url"]?.let { launchIntent.putExtra(EXTRA_TAP_URL, it) }
        PendingIntent.getActivity(
            context, notificationId, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    } // null only if the host app has no launcher Activity at all (rare) — no-op the tap action, still post the notification

    val builder = NotificationCompat.Builder(context, DEFAULT_CHANNEL_ID)
        .setContentTitle(title)
        .setContentText(body)
        .setSmallIcon(context.applicationInfo.icon)
        .setAutoCancel(true)

    pendingIntent?.let { builder.setContentIntent(it) }

    // Zero-dependency synchronous image fetch — see downloadBitmap's doc for why
    // a blocking call is acceptable here.
    image?.let { downloadBitmap(it) }?.let { bitmap ->
        builder.setStyle(NotificationCompat.BigPictureStyle().bigPicture(bitmap))
    }

    NotificationManagerCompat.from(context).notify(notificationId, builder.build())

    data["displayedUrl"]?.let { PushNotificationServiceDisplay.ping(it) }
}

/**
 * Synchronous, best-effort image download for BigPictureStyle. This SDK has no
 * third-party image-loading dependency (no Glide/Coil), so it fetches on the
 * calling thread the same zero-dependency way DeviceTokenApi/PushNotificationServiceDisplay
 * do. Safe only because onMessageReceived is documented as reached exclusively from
 * FirebaseMessagingService.onMessageReceived, which Firebase itself invokes off the
 * main thread. Never throws — any failure (bad URL, unreachable host, decode failure)
 * falls back to posting the notification without an image.
 */
private fun downloadBitmap(urlString: String): Bitmap? = try {
    val connection = URL(urlString).openConnection() as HttpURLConnection
    try {
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000
        connection.requestMethod = "GET"
        connection.inputStream.use { BitmapFactory.decodeStream(it) }
    } finally {
        connection.disconnect()
    }
} catch (e: Exception) {
    Log.w(TAG, "Failed to download notification image, posting without it: ${e.message}")
    null
}
