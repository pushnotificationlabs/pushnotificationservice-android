package com.pushnotificationservice.sdk

import android.content.Context

interface TokenStoring {
    fun load(): String?
    fun save(token: String)
    fun clear()
}

/**
 * Persists the last successfully-registered device token so the SDK can
 * automatically supply it as old_token on the next registration call — the
 * host app never manages rotation bookkeeping itself.
 */
internal class SharedPreferencesTokenStore(context: Context) : TokenStoring {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun load(): String? = prefs.getString(KEY_TOKEN, null)

    override fun save(token: String) {
        prefs.edit().putString(KEY_TOKEN, token).apply()
    }

    override fun clear() {
        prefs.edit().remove(KEY_TOKEN).apply()
    }

    companion object {
        private const val PREFS_NAME = "com.pushnotificationservice.sdk"
        private const val KEY_TOKEN = "device_token"
    }
}

internal class InMemoryTokenStore(private var token: String? = null) : TokenStoring {
    override fun load(): String? = token
    override fun save(token: String) { this.token = token }
    override fun clear() { token = null }
}
