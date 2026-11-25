package com.example.running.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until

/** The app under test (`:app` has no flavours, and the benchmark variants keep the id). */
const val TARGET_PACKAGE = "com.example.running"

private const val TIMEOUT_MS = 10_000L

/** Content description of the Run detail overflow button (`detail_more`). */
private const val DETAIL_MORE = "More options"

private val RUNTIME_PERMISSIONS = listOf(
    "android.permission.ACCESS_FINE_LOCATION",
    "android.permission.ACCESS_COARSE_LOCATION",
    "android.permission.POST_NOTIFICATIONS",
    "android.permission.ACTIVITY_RECOGNITION",
)

/**
 * Grants the runtime permissions from the shell (no dialogs) and asks the benchmark-only
 * `BenchmarkSetupReceiver` (app/src/benchmarkHooks) to skip onboarding and seed the demo runs.
 * The broadcast starts the process, so this ends by killing it: the next launch is a cold start.
 */
fun MacrobenchmarkScope.prepareApp() {
    RUNTIME_PERMISSIONS.forEach { device.executeShellCommand("pm grant $packageName $it") }
    // 0x20 = FLAG_INCLUDE_STOPPED_PACKAGES: a freshly installed app is in the stopped state.
    val out = device.executeShellCommand(
        "am broadcast -f 0x20 -n $packageName/.benchmark.BenchmarkSetupReceiver",
    )
    check("result=1" in out) { "BenchmarkSetupReceiver did not report ready: $out" }
    killProcess()
}

/**
 * Cold start ends on the Record tab (start destination once onboarding is done). The Start button's
 * content description; its visible label is upper-cased.
 */
fun MacrobenchmarkScope.waitForRecordTab() {
    check(device.wait(Until.hasObject(By.desc("Start")), TIMEOUT_MS)) { "Record tab did not show" }
}

/** Record tab in warm-up: live map, GPS hint. Gives the map time to load its style and tiles. */
fun MacrobenchmarkScope.recordWarmUp() {
    clickTab("Record")
    waitForRecordTab()
    device.waitForIdle()
    Thread.sleep(3_000)
}

/** All four tabs and back to Record. */
fun MacrobenchmarkScope.switchAllTabs() {
    for (tab in listOf("Activities", "Progress", "Settings", "Record")) {
        clickTab(tab)
        device.waitForIdle()
    }
}

/** Activities list: fling down and back up (thumbnails, week strip). */
fun MacrobenchmarkScope.activitiesScroll() {
    clickTab("Activities")
    val list = waitFor(By.scrollable(true))
    list.setGestureMargin(device.displayWidth / 5)
    repeat(2) { list.fling(Direction.DOWN) }
    device.waitForIdle()
    list.fling(Direction.UP)
    device.waitForIdle()
}

/** Opens the first run on Activities: route draw-in, charts, splits; scrolls it, then back. */
fun MacrobenchmarkScope.openRunDetail() {
    clickTab("Activities")
    // Demo names end in " Run" / " Walk"; the "Run" filter chip is an exact match, so not picked.
    waitFor(By.textEndsWith(" Run")).click()
    waitFor(By.desc(DETAIL_MORE)) // overflow menu: the detail has loaded
    Thread.sleep(1_500) // draw-in
    val content = waitFor(By.scrollable(true))
    content.setGestureMargin(device.displayWidth / 5)
    repeat(3) { content.scroll(Direction.DOWN, 1f) }
    device.waitForIdle()
    content.fling(Direction.UP)
    device.pressBack()
    waitFor(By.textEndsWith(" Run"))
}

/** Progress tab: every filter chip, then scroll the charts. */
fun MacrobenchmarkScope.progressFilters() {
    clickTab("Progress")
    waitFor(By.text("All"))
    // Loading/empty and the chart list each draw their own chips and fade through, so a chip found a
    // moment ago can be gone: click by selector, retrying on a stale node.
    for (chip in listOf("Run", "Walk", "All")) {
        clickWithRetry(By.text(chip))
        device.waitForIdle()
    }
    device.findObject(By.scrollable(true))?.let { list ->
        list.setGestureMargin(device.displayWidth / 5)
        list.fling(Direction.DOWN)
        device.waitForIdle()
        list.fling(Direction.UP)
        device.waitForIdle()
    }
}

/** Tab labels repeat as screen titles ("Activities", "Progress"); the bottom bar is the lowest. */
private fun MacrobenchmarkScope.clickTab(label: String) {
    check(device.wait(Until.hasObject(By.text(label)), TIMEOUT_MS)) { "No \"$label\" tab" }
    retryStale { device.findObjects(By.text(label)).maxBy { it.visibleBounds.centerY() }.click() }
}

private fun MacrobenchmarkScope.clickWithRetry(selector: BySelector) = retryStale { waitFor(selector).click() }

private fun retryStale(action: () -> Unit) {
    repeat(4) {
        try {
            return action()
        } catch (_: StaleObjectException) {
            Thread.sleep(300)
        }
    }
    action()
}

private fun MacrobenchmarkScope.waitFor(selector: BySelector): UiObject2 =
    checkNotNull(device.wait(Until.findObject(selector), TIMEOUT_MS)) { "Not found: $selector" }
