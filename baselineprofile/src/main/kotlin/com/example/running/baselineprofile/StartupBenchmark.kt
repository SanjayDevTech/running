package com.example.running.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cold start to the Record tab without and with the bundled baseline profile, on the minified
 * `benchmarkRelease` variant. Run with
 * `./gradlew :baselineprofile:pixel6Api34BenchmarkReleaseAndroidTest
 *   -Pandroid.testInstrumentationRunnerArguments.class=com.example.running.baselineprofile.StartupBenchmark
 *   -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.suppressErrors=EMULATOR`
 * and compare `timeToInitialDisplayMs` (architecture.md §7.1). Emulator numbers are indicative only.
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun startupNoCompilation() = startup(CompilationMode.None())

    @Test
    fun startupBaselineProfile() = startup(CompilationMode.Partial(BaselineProfileMode.Require))

    private fun startup(mode: CompilationMode) = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = mode,
        startupMode = StartupMode.COLD,
        iterations = 10,
        setupBlock = {
            prepareApp()
            pressHome()
        },
    ) {
        startActivityAndWait()
        waitForRecordTab()
    }
}
