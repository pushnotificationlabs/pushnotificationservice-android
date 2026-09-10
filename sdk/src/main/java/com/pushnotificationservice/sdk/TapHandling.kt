package com.pushnotificationservice.sdk

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Call from the Activity a notification tap launches. The url is always a
 * signed click-counting redirect (never a real destination — see the plan's
 * Global Constraints and the spec's "Tap handling" section) and is opened
 * verbatim, never resolved/followed client-side.
 */
fun PushNotificationService.onNotificationOpened(context: Context, intent: Intent) {
    val urlString = intent.getStringExtra(EXTRA_TAP_URL) ?: return
    val uri = Uri.parse(urlString)

    val handler = PushNotificationService.onNotificationTapped
    if (handler != null) {
        handler(uri)
        return
    }

    val opener = PushNotificationService.urlOpenerForTesting
    if (opener != null) {
        opener(context, uri)
        return
    }

    context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
