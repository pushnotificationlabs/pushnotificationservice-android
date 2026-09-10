// sdk/src/test/java/com/pushnotificationservice/sdk/TapHandlingTest.kt
package com.pushnotificationservice.sdk

import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
class TapHandlingTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @After
    fun tearDown() {
        PushNotificationService.resetForTesting()
    }

    @Test
    fun `calls custom handler with tracking url when set`() {
        var received: Uri? = null
        PushNotificationService.onNotificationTapped = { received = it }
        val intent = Intent().putExtra(EXTRA_TAP_URL, "https://api.pushnotificationservice.com/c/campaign-1")

        PushNotificationService.onNotificationOpened(context, intent)

        assertEquals("https://api.pushnotificationservice.com/c/campaign-1", received.toString())
    }

    @Test
    fun `uses default opener when no custom handler set`() {
        var opened: Uri? = null
        PushNotificationService.urlOpenerForTesting = { _, uri -> opened = uri }
        val intent = Intent().putExtra(EXTRA_TAP_URL, "https://api.pushnotificationservice.com/c/campaign-1")

        PushNotificationService.onNotificationOpened(context, intent)

        assertEquals("https://api.pushnotificationservice.com/c/campaign-1", opened.toString())
    }

    @Test
    fun `missing tap url extra does nothing`() {
        var called = false
        PushNotificationService.urlOpenerForTesting = { _, _ -> called = true }

        PushNotificationService.onNotificationOpened(context, Intent())

        assertNull(if (called) Uri.EMPTY else null)
    }
}
