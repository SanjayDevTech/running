package com.example.running

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.running.core.demo.SyntheticRuns
import com.example.running.di.LocalAppContainer
import com.example.running.ui.navigation.AppNavHost
import com.example.running.ui.navigation.DeepLinks
import com.example.running.ui.theme.Black
import com.example.running.ui.theme.RunningTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    /** Set by a notification tap (cold start or onNewIntent); AppNavHost consumes and resets it. */
    private val openRecordingRequest = MutableStateFlow(false)

    /** Read once per activity: the nav graph's start destination must not change under it. */
    private val startOnboarded = MutableStateFlow<Boolean?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must run before super.onCreate so the splash theme hands over cleanly.
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            // Always-dark app: bar icons pinned light. `SystemBarStyle.dark` = dark bar background.
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        splash.setKeepOnScreenCondition { startOnboarded.value == null }

        val container = (application as RunningApp).container
        handleIntent(intent)

        lifecycleScope.launch {
            startOnboarded.value = container.settings.settings.first().onboardingComplete
        }
        // Once per UI launch (not per process start: workers also start the process).
        lifecycleScope.launch {
            container.setup.backupScheduler.applyFromSettings()
            container.recording.controller.checkForInterruptedRun()
        }
        if (BuildConfig.DEBUG && savedInstanceState == null && intent.getBooleanExtra(DeepLinks.EXTRA_SEED_DEMO, false)) {
            container.core.appScope.launch {
                val clock = container.core.clock
                SyntheticRuns.demoGraphs(clock.nowMillis(), clock.zoneId()).forEach {
                    container.runRepository.importRunGraph(it)
                }
            }
        }

        setContent {
            val onboarded by startOnboarded.collectAsStateWithLifecycle()
            val openRecording by openRecordingRequest.collectAsStateWithLifecycle()
            RunningTheme {
                CompositionLocalProvider(LocalAppContainer provides container) {
                    Box(Modifier.fillMaxSize().background(Black)) {
                        onboarded?.let { done ->
                            AppNavHost(
                                onboardingComplete = done,
                                openRecordingRequest = openRecording,
                                onOpenRecordingHandled = { openRecordingRequest.value = false },
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == DeepLinks.ACTION_OPEN_RECORDING) openRecordingRequest.value = true
    }
}
