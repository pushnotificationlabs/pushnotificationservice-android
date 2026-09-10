package com.pushnotificationservice.sdk

import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
class SharedPreferencesTokenStoreTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun `load returns null when nothing saved`() {
        assertNull(SharedPreferencesTokenStore(context).load())
    }

    @Test
    fun `save then load round trips`() {
        val store = SharedPreferencesTokenStore(context)
        store.save("abc123")
        assertEquals("abc123", store.load())
    }

    @Test
    fun `save twice overwrites`() {
        val store = SharedPreferencesTokenStore(context)
        store.save("first")
        store.save("second")
        assertEquals("second", store.load())
    }

    @Test
    fun `clear removes the stored token`() {
        val store = SharedPreferencesTokenStore(context)
        store.save("abc123")
        store.clear()
        assertNull(store.load())
    }
}
