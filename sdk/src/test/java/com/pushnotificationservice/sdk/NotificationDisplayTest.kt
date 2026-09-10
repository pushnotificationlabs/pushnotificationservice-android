package com.pushnotificationservice.sdk

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import androidx.test.core.app.ApplicationProvider
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowBitmapFactory
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class NotificationDisplayTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    // Robolectric's default target SDK for this module tracks compileSdk (34), and
    // POST_NOTIFICATIONS is a runtime permission on API 33+ that Robolectric does NOT
    // auto-grant just because the manifest declares it (matching real device behavior:
    // a dangerous permission needs an explicit runtime grant, not just a manifest entry).
    // Without this, every test below observes onMessageReceived's permission-gate no-op
    // rather than the posting behavior each test actually means to exercise.
    @Before
    fun grantNotificationPermission() {
        shadowOf(ApplicationProvider.getApplicationContext<Application>())
            .grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    // Robolectric's default test manifest has no <application android:icon=...>,
    // so context.applicationInfo.icon is 0 out of the box — every real host app
    // declares one. Give tests a stand-in so the icon==0 guard (finding #10) only
    // engages in the one test that means to exercise it.
    @Before
    fun setApplicationIcon() {
        context.applicationInfo.icon = android.R.drawable.ic_dialog_alert
    }

    @After
    fun tearDown() {
        PushNotificationChannel.resetForTesting()
        PushNotificationServiceDisplay.resetForTesting()
    }

    /** Robolectric's default test manifest declares no Activity at all, so
     * getLaunchIntentForPackage() returns null unless a launcher Activity is
     * registered — do so explicitly for tests that need a real PendingIntent
     * round-trip, mirroring what every real host app has. */
    private fun registerLauncherActivity() {
        val packageManager = context.packageManager
        val componentName = ComponentName(context.packageName, "com.pushnotificationservice.sdk.TestLauncherActivity")
        shadowOf(packageManager).addActivityIfNotPresent(componentName)
        val filter = IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        shadowOf(packageManager).addIntentFilterForActivity(componentName, filter)
    }

    @Test
    fun `onMessageReceived posts a notification with title and body`() {
        PushNotificationService.onMessageReceived(context, "Sale", "30% off", null, emptyMap())

        val manager = context.getSystemService(NotificationManager::class.java)
        val notifications = shadowOf(manager).activeNotifications
        assertEquals(1, notifications.size)
        val extras = notifications[0].notification.extras
        assertEquals("Sale", extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals("30% off", extras.getCharSequence(Notification.EXTRA_TEXT).toString())
    }

    @Test
    fun `onMessageReceived pings displayedUrl when present`() {
        var pinged: String? = null
        PushNotificationServiceDisplay.httpClientForTesting = { pinged = it }

        PushNotificationService.onMessageReceived(context, "Sale", "30% off", null, mapOf("displayedUrl" to "https://api.pushnotificationservice.com/d/campaign-1"))

        assertEquals("https://api.pushnotificationservice.com/d/campaign-1", pinged)
    }

    @Test
    fun `onMessageReceived without displayedUrl does not ping`() {
        var pinged = false
        PushNotificationServiceDisplay.httpClientForTesting = { pinged = true }

        PushNotificationService.onMessageReceived(context, "Sale", "30% off", null, emptyMap())

        assertTrue(!pinged)
    }

    @Test
    fun `configureChannel customizes the channel name`() {
        PushNotificationChannel.configureChannel(name = "Deals")

        PushNotificationService.onMessageReceived(context, "Sale", "30% off", null, emptyMap())

        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = manager.getNotificationChannel("pushnotificationservice_default")
        assertEquals("Deals", channel.name)
    }

    @Test
    fun `onMessageReceived no-ops without crashing when POST_NOTIFICATIONS is not granted`() {
        shadowOf(ApplicationProvider.getApplicationContext<Application>())
            .denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

        PushNotificationService.onMessageReceived(context, "Sale", "30% off", null, emptyMap())

        val manager = context.getSystemService(NotificationManager::class.java)
        assertTrue(shadowOf(manager).activeNotifications.isEmpty())
    }

    // --- Finding #1: tap PendingIntent must resolve to a real Activity and round-trip EXTRA_TAP_URL ---

    @Test
    fun `tapped notification's PendingIntent carries EXTRA_TAP_URL`() {
        registerLauncherActivity()

        PushNotificationService.onMessageReceived(
            context, "Sale", "30% off", null,
            mapOf("url" to "https://api.pushnotificationservice.com/c/abc123")
        )

        val manager = context.getSystemService(NotificationManager::class.java)
        val notification = shadowOf(manager).activeNotifications[0].notification
        val pendingIntent = notification.contentIntent
        assertNotNull(pendingIntent, "contentIntent should be set when a launcher Activity resolves")

        val savedIntent = shadowOf(pendingIntent).savedIntent
        assertEquals(
            "https://api.pushnotificationservice.com/c/abc123",
            savedIntent.getStringExtra(EXTRA_TAP_URL)
        )
    }

    @Test
    fun `onMessageReceived still posts notification when no launcher Activity resolves`() {
        // No registerLauncherActivity() call — Robolectric's default test manifest
        // declares no Activity, so getLaunchIntentForPackage() returns null here.
        PushNotificationService.onMessageReceived(context, "Sale", "30% off", null, emptyMap())

        val manager = context.getSystemService(NotificationManager::class.java)
        val notifications = shadowOf(manager).activeNotifications
        assertEquals(1, notifications.size)
        assertEquals(null, notifications[0].notification.contentIntent)
    }

    // --- Finding #7: notification id / PendingIntent request code scoped per campaign ---

    @Test
    fun `distinct campaigns get distinct notification ids`() {
        PushNotificationService.onMessageReceived(context, "A", "a", null, mapOf("campaign" to "campaign-1"))
        PushNotificationService.onMessageReceived(context, "B", "b", null, mapOf("campaign" to "campaign-2"))

        val manager = context.getSystemService(NotificationManager::class.java)
        val ids = shadowOf(manager).activeNotifications.map { it.id }.toSet()
        assertEquals(2, ids.size)
    }

    // --- Finding #6: image parameter wired up via BigPictureStyle ---

    @Test
    fun `onMessageReceived with image sets a big picture when the fetch succeeds`() {
        ShadowBitmapFactory.setAllowInvalidImageData(true)
        val server = MockWebServer()
        server.start()
        server.enqueue(MockResponse().setResponseCode(200).setBody("fake-image-bytes"))
        try {
            PushNotificationService.onMessageReceived(
                context, "Sale", "30% off", server.url("/photo.png").toString(), emptyMap()
            )

            val manager = context.getSystemService(NotificationManager::class.java)
            val extras = shadowOf(manager).activeNotifications[0].notification.extras
            assertTrue(extras.containsKey(Notification.EXTRA_PICTURE))
        } finally {
            server.shutdown()
            ShadowBitmapFactory.setAllowInvalidImageData(false)
        }
    }

    @Test
    fun `onMessageReceived without image does not set a big picture`() {
        PushNotificationService.onMessageReceived(context, "Sale", "30% off", null, emptyMap())

        val manager = context.getSystemService(NotificationManager::class.java)
        val extras = shadowOf(manager).activeNotifications[0].notification.extras
        assertFalse(extras.containsKey(Notification.EXTRA_PICTURE))
    }

    @Test
    fun `onMessageReceived with unreachable image url still posts the notification`() {
        PushNotificationService.onMessageReceived(context, "Sale", "30% off", "not a valid url", emptyMap())

        val manager = context.getSystemService(NotificationManager::class.java)
        val notifications = shadowOf(manager).activeNotifications
        assertEquals(1, notifications.size)
        val extras = notifications[0].notification.extras
        assertEquals("Sale", extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals("30% off", extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertFalse(extras.containsKey(Notification.EXTRA_PICTURE))
    }

    // --- Finding #10: a host app with no launcher icon must not crash notify() ---

    @Test
    fun `onMessageReceived no-ops without crashing when the host app has no icon`() {
        context.applicationInfo.icon = 0

        PushNotificationService.onMessageReceived(context, "Sale", "30% off", null, emptyMap())

        val manager = context.getSystemService(NotificationManager::class.java)
        assertTrue(shadowOf(manager).activeNotifications.isEmpty())
    }
}
