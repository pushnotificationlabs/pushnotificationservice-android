package com.pushnotificationservice.sdk

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DeviceTokenApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: DeviceTokenApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = DeviceTokenApi(server.url("/").toString().trimEnd('/'))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `register posts to correct path with expected body`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"deviceToken":"dt_abc123"}"""))

        val id = api.register(
            siteId = "site-123", platform = "android", token = "tok", oldToken = "old-tok",
            bundleId = null, meta = mapOf("app_version" to "1.0")
        )

        assertEquals("dt_abc123", id)
        val request = server.takeRequest()
        assertEquals("/v1/sites/site-123/device-tokens", request.path)
        assertEquals("POST", request.method)
        assertEquals("application/json", request.getHeader("Accept"))
        val json = JSONObject(request.body.readUtf8())
        assertEquals("android", json.getString("platform"))
        assertEquals("old-tok", json.getString("old_token"))
        assertEquals("1.0", json.getJSONObject("meta").getString("app_version"))
    }

    @Test
    fun `register omits old_token and bundle_id when null`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"deviceToken":"dt_abc123"}"""))

        api.register(siteId = "site-123", platform = "android", token = "tok", oldToken = null, bundleId = null, meta = emptyMap())

        val json = JSONObject(server.takeRequest().body.readUtf8())
        assertEquals(false, json.has("old_token"))
        assertEquals(false, json.has("bundle_id"))
    }

    @Test
    fun `register throws ServerError on non-2xx`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(422))

        val error = assertFailsWith<PushNotificationServiceError.ServerError> {
            api.register(siteId = "site-123", platform = "android", token = "tok", oldToken = null, bundleId = null, meta = emptyMap())
        }
        assertEquals(422, error.statusCode)
    }

    @Test
    fun `unregister posts to unregister path`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))

        api.unregister(siteId = "site-123", platform = "android", token = "tok")

        assertEquals("/v1/sites/site-123/device-tokens/unregister", server.takeRequest().path)
    }
}
