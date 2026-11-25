package com.example.running.recording

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.running.MainActivity
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A notification action that outlives its run (double-tapped Finish, a tap racing the notification's
 * removal) restarts RecordingService with no run to end it. It must shut itself down (foreground,
 * wake lock and all) instead of waiting forever (architecture.md §10.10).
 */
@RunWith(AndroidJUnit4::class)
class RecordingServiceStaleActionTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before fun grantLocation() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.POST_NOTIFICATIONS)
            .forEach { automation.grantRuntimePermission(context.packageName, it) }
    }

    @Suppress("DEPRECATION") // still returns the caller's own services
    private fun serviceRunning(): Boolean = context.getSystemService(ActivityManager::class.java)
        .getRunningServices(Int.MAX_VALUE).any { it.service.className == RecordingService::class.java.name }

    private fun waitUntil(timeoutMillis: Long, condition: () -> Boolean): Boolean {
        val end = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < end) {
            if (condition()) return true
            Thread.sleep(100)
        }
        return condition()
    }

    @Test fun staleActionsShutTheServiceDown() {
        ActivityScenario.launch(MainActivity::class.java).use {
            for (action in listOf(RecordingService.ACTION_STOP, RecordingService.ACTION_PAUSE, RecordingService.ACTION_RESUME)) {
                context.startForegroundService(RecordingService.actionIntent(context, action))
                assertThat(waitUntil(10_000) { !serviceRunning() }).isTrue()
                Thread.sleep(1_000)
                assertThat(serviceRunning()).isFalse()
            }
        }
    }
}
