package com.example.running.benchmark

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.running.RunningApp
import com.example.running.core.demo.SyntheticRuns
import kotlinx.coroutines.launch

/**
 * Test hook for the `:baselineprofile` module: puts the app past onboarding and seeds the ~40
 * demo runs (the same data as the debug-only `seed_demo` extra, architecture.md §6.5), so the
 * profile journeys and the startup benchmark see the tab shell with data.
 *
 * It lives in `src/benchmarkHooks/` and is compiled only into the plugin's `nonMinifiedRelease`
 * and `benchmarkRelease` variants, so the shipped release APK has neither the class nor the
 * receiver. Idempotent: once onboarding is complete it returns at once (the import itself uses
 * fixed ids). Called with
 * `am broadcast -f 0x20 -n com.example.running/.benchmark.BenchmarkSetupReceiver`;
 * result code 1 means ready.
 */
class BenchmarkSetupReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as RunningApp).container
        val pending = goAsync()
        container.core.appScope.launch {
            try {
                val settings = container.settings
                if (!settings.current().onboardingComplete) {
                    val clock = container.core.clock
                    SyntheticRuns.demoGraphs(clock.nowMillis(), clock.zoneId()).forEach {
                        container.runRepository.importRunGraph(it)
                    }
                    settings.setBatteryPromptDismissed(true)
                    settings.setOnboardingComplete(true)
                }
                pending.setResult(RESULT_READY, "ready", null)
            } catch (t: Throwable) {
                pending.setResult(RESULT_FAILED, t.toString(), null)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val RESULT_READY = 1
        const val RESULT_FAILED = 2
    }
}
