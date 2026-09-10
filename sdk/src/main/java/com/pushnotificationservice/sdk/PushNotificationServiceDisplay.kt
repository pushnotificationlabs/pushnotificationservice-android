package com.pushnotificationservice.sdk

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL

internal object PushNotificationServiceDisplay {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Injectable for tests. */
    var httpClientForTesting: ((String) -> Unit)? = null

    /** Fire-and-forget displayedUrl receipt ping — must never block or delay
     * showing the notification, and a failure here is silently swallowed
     * (best-effort, same posture as the iOS extension's ping). */
    fun ping(url: String) {
        httpClientForTesting?.let { it(url); return }
        scope.launch {
            var connection: HttpURLConnection? = null
            try {
                connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 15_000
                connection.readTimeout = 15_000
                connection.requestMethod = "GET"
                connection.responseCode // triggers the request
            } catch (e: Exception) {
                // best-effort — nothing to recover, nothing to surface
            } finally {
                connection?.disconnect()
            }
        }
    }

    internal fun resetForTesting() {
        httpClientForTesting = null
    }
}
