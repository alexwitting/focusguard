package com.focusguard.core.system.foreground

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.focusguard.core.system.engine.WebContentFilterEngine
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowUsageStatsManager
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
class UsageStatsForegroundDetectorTest {
    private lateinit var context: Context
    private lateinit var detector: UsageStatsForegroundDetector

    @BeforeEach
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        detector = UsageStatsForegroundDetector(context)
    }

    @Suppress("DEPRECATION")
    private fun addEvent(
        pkg: String,
        type: Int,
        agoMs: Long,
    ) {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        shadowOf(usm).addEvent(
            ShadowUsageStatsManager.EventBuilder
                .buildEvent()
                .setPackage(pkg)
                .setEventType(type)
                .setTimeStamp(System.currentTimeMillis() - agoMs)
                .build(),
        )
    }

    @Test
    fun `reports a browser that was last resumed`() {
        addEvent(WebContentFilterEngine.CHROME, UsageEvents.Event.ACTIVITY_RESUMED, agoMs = 1_000)

        assertEquals(WebContentFilterEngine.CHROME, detector.currentForegroundBrowser())
    }

    @Test
    fun `older API events (MOVE_TO_FOREGROUND) count too`() {
        @Suppress("DEPRECATION")
        addEvent(WebContentFilterEngine.FIREFOX, UsageEvents.Event.MOVE_TO_FOREGROUND, agoMs = 1_000)

        assertEquals(WebContentFilterEngine.FIREFOX, detector.currentForegroundBrowser())
    }

    @Test
    fun `a browser that has since been paused is not reported`() {
        addEvent(WebContentFilterEngine.CHROME, UsageEvents.Event.ACTIVITY_RESUMED, agoMs = 2_000)
        addEvent(WebContentFilterEngine.CHROME, UsageEvents.Event.ACTIVITY_PAUSED, agoMs = 1_000)

        assertNull(detector.currentForegroundBrowser())
    }

    @Test
    fun `a pause for some other app leaves the browser in front`() {
        addEvent(WebContentFilterEngine.CHROME, UsageEvents.Event.ACTIVITY_RESUMED, agoMs = 2_000)
        @Suppress("DEPRECATION")
        addEvent("com.other.app", UsageEvents.Event.MOVE_TO_BACKGROUND, agoMs = 1_000)

        assertEquals(WebContentFilterEngine.CHROME, detector.currentForegroundBrowser())
    }

    @Test
    fun `a non-browser app in front is not reported`() {
        addEvent("com.instagram.android", UsageEvents.Event.ACTIVITY_RESUMED, agoMs = 1_000)

        assertNull(detector.currentForegroundBrowser())
    }

    @Test
    fun `nothing in the lookback window means no browser`() {
        assertNull(detector.currentForegroundBrowser())
    }
}
