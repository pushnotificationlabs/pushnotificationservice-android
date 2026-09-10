package com.pushnotificationservice.sdk

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

internal class DeviceTokenApi(private val baseUrl: String) {

    suspend fun register(siteId: String, platform: String, token: String, oldToken: String?, bundleId: String?, meta: Map<String, String>): String {
        val body = JSONObject().apply {
            put("platform", platform)
            put("token", token)
            oldToken?.let { put("old_token", it) }
            bundleId?.let { put("bundle_id", it) }
            if (meta.isNotEmpty()) put("meta", JSONObject(meta))
        }

        val responseBody = post("$baseUrl/v1/sites/$siteId/device-tokens", body)
        return try {
            JSONObject(responseBody).getString("deviceToken")
        } catch (e: Exception) {
            throw PushNotificationServiceError.InvalidResponse
        }
    }

    suspend fun unregister(siteId: String, platform: String, token: String) {
        val body = JSONObject().apply {
            put("platform", platform)
            put("token", token)
        }
        post("$baseUrl/v1/sites/$siteId/device-tokens/unregister", body)
    }

    private suspend fun post(urlString: String, body: JSONObject): String = withContext(Dispatchers.IO) {
        val connection: HttpURLConnection
        try {
            connection = URL(urlString).openConnection() as HttpURLConnection
        } catch (e: Exception) {
            throw PushNotificationServiceError.Transport
        }
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            connection.doOutput = true
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val statusCode = connection.responseCode
            if (statusCode !in 200..299) {
                throw PushNotificationServiceError.ServerError(statusCode)
            }
            connection.inputStream.bufferedReader().use { it.readText() }
        } catch (e: PushNotificationServiceError) {
            throw e
        } catch (e: Exception) {
            throw PushNotificationServiceError.Transport
        } finally {
            connection.disconnect()
        }
    }
}
