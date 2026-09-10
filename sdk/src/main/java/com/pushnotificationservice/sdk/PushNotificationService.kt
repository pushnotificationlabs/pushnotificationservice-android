package com.pushnotificationservice.sdk

import android.content.Context
import android.net.Uri
import android.os.Build
import java.util.Locale

object PushNotificationService {
    private const val DEFAULT_BASE_URL = "https://api.pushnotificationservice.com"

    private data class Configuration(
        val siteId: String,
        val api: DeviceTokenApi,
        val tokenStore: TokenStoring,
        val appContext: Context,
    )

    // Written from the host's main thread (configure(), typically called from
    // Application.onCreate()) and read from FCM's delivery thread (onNewToken()/
    // onMessageReceived()) — @Volatile guarantees a write on one thread is visible
    // to a read on the other, without which a thread could keep observing a stale/
    // null value indefinitely.
    @Volatile
    private var configuration: Configuration? = null

    /** Optional override — if set, called instead of the default open-via-Intent behavior. */
    var onNotificationTapped: ((Uri) -> Unit)? = null

    /** Injectable for tests; production default opens via an ACTION_VIEW Intent. */
    internal var urlOpenerForTesting: ((Context, Uri) -> Unit)? = null

    fun configure(context: Context, siteId: String) {
        configureForTesting(context, siteId, DEFAULT_BASE_URL, SharedPreferencesTokenStore(context.applicationContext))
    }

    internal fun configureForTesting(context: Context, siteId: String, baseUrl: String, tokenStore: TokenStoring) {
        configuration = Configuration(siteId, DeviceTokenApi(baseUrl), tokenStore, context.applicationContext)
    }

    internal fun resetForTesting() {
        configuration = null
        onNotificationTapped = null
        urlOpenerForTesting = null
    }

    /** Call from FirebaseMessagingService.onNewToken. */
    suspend fun onNewToken(token: String) {
        val config = configuration ?: throw PushNotificationServiceError.NotConfigured
        val oldToken = config.tokenStore.load()

        config.api.register(
            siteId = config.siteId, platform = "android", token = token,
            oldToken = if (oldToken == token) null else oldToken,
            bundleId = null, meta = currentMeta(config.appContext)
        )

        config.tokenStore.save(token)
    }

    /** e.g. on logout / opt-out. */
    suspend fun unregister() {
        val config = configuration ?: throw PushNotificationServiceError.NotConfigured
        val token = config.tokenStore.load() ?: return

        config.api.unregister(siteId = config.siteId, platform = "android", token = token)
        config.tokenStore.clear()
    }

    private fun currentMeta(context: Context): Map<String, String> {
        val meta = mutableMapOf<String, String>()
        try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            // Omit when absent/blank, consistent with oldToken/bundleId elsewhere in
            // this SDK, rather than writing an empty string into the backend field.
            info.versionName?.takeIf { it.isNotBlank() }?.let { meta["app_version"] = it }
        } catch (e: Exception) {
            // Package info unavailable in this environment — omit rather than crash.
        }
        meta["os_version"] = Build.VERSION.RELEASE ?: ""
        // toLanguageTag() (e.g. "en-US") is shorter and standard vs. toString()'s
        // legacy format (e.g. "en_US_#Latn"), which can exceed the backend's 16-char limit.
        meta["locale"] = Locale.getDefault().toLanguageTag()
        return meta
    }
}
