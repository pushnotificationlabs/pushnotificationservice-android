package com.pushnotificationservice.sdk

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
class PushNotificationServiceRegistrationTest {
    private lateinit var server: MockWebServer
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
        PushNotificationService.resetForTesting()
    }

    @Test
    fun `onNewToken registers with backend`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"deviceToken":"dt_1"}"""))
        PushNotificationService.configureForTesting(context, "site-123", server.url("/").toString().trimEnd('/'), InMemoryTokenStore())

        PushNotificationService.onNewToken("token-1")

        val json = JSONObject(server.takeRequest().body.readUtf8())
        assertEquals("token-1", json.getString("token"))
    }

    @Test
    fun `onNewToken second call sends first token as old_token`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"deviceToken":"dt_1"}"""))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"deviceToken":"dt_1"}"""))
        PushNotificationService.configureForTesting(context, "site-123", server.url("/").toString().trimEnd('/'), InMemoryTokenStore())

        PushNotificationService.onNewToken("token-1")
        server.takeRequest()
        PushNotificationService.onNewToken("token-2")
        val second = JSONObject(server.takeRequest().body.readUtf8())

        assertEquals("token-2", second.getString("token"))
        assertEquals("token-1", second.getString("old_token"))
    }

    @Test
    fun `onNewToken throws NotConfigured without configure`() {
        assertFailsWith<PushNotificationServiceError.NotConfigured> {
            runBlocking { PushNotificationService.onNewToken("token-1") }
        }
    }

    @Test
    fun `unregister clears persisted token`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))
        val tokenStore = InMemoryTokenStore("existing")
        PushNotificationService.configureForTesting(context, "site-123", server.url("/").toString().trimEnd('/'), tokenStore)

        PushNotificationService.unregister()

        assertNull(tokenStore.load())
    }

    @Test
    fun `unregister with nothing persisted does not call the API`() = runBlocking {
        PushNotificationService.configureForTesting(context, "site-123", server.url("/").toString().trimEnd('/'), InMemoryTokenStore())

        PushNotificationService.unregister()

        assertEquals(0, server.requestCount)
    }
}
