package com.example.running.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates the app's baseline and startup profiles. Run with
 * `./gradlew :app:generateBaselineProfile` (architecture.md §7); the plugin runs it on the
 * `pixel6Api34` managed device against the `nonMinifiedRelease` variant and writes the merged result
 * to app/src/release/generated/baselineProfiles.
 *
 * Two tests so only cold start feeds the startup profile (dex layout); both feed the baseline profile.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun startup() = rule.collect(packageName = TARGET_PACKAGE, includeInStartupProfile = true) {
        prepareApp()
        pressHome()
        startActivityAndWait()
        waitForRecordTab()
    }

    @Test
    fun journeys() = rule.collect(packageName = TARGET_PACKAGE, includeInStartupProfile = false) {
        prepareApp()
        pressHome()
        startActivityAndWait()
        recordWarmUp()
        switchAllTabs()
        activitiesScroll()
        openRunDetail()
        progressFilters()
    }
}
