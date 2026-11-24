package com.example.running.core

import com.example.running.core.util.formatAccuracy
import com.example.running.core.util.formatCadence
import com.example.running.core.util.formatCalories
import com.example.running.core.util.formatDistance
import com.example.running.core.util.formatDistanceKm
import com.example.running.core.util.formatDuration
import com.example.running.core.util.formatDurationShort
import com.example.running.core.util.formatElevation
import com.example.running.core.util.formatFileSize
import com.example.running.core.util.formatMeters
import com.example.running.core.util.formatMonthLabel
import com.example.running.core.util.formatPace
import com.example.running.core.util.formatPaceWithUnit
import com.example.running.core.util.formatPercent
import com.example.running.core.util.formatRelativeDay
import com.example.running.core.util.formatRunDate
import com.example.running.core.util.formatRunDateTime
import com.example.running.core.util.formatSecondsPrecise
import com.example.running.core.util.formatWeekLabel
import com.example.running.core.util.formatWeightKg
import com.example.running.testing.FakeClock
import com.google.common.truth.Truth.assertThat
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Test

class FormatTest {
    private val zone = "Asia/Kolkata"

    @Test fun distance() {
        assertThat(formatDistanceKm(5_021.0)).isEqualTo("5.02")
        assertThat(formatDistance(5_021.0)).isEqualTo("5.02 km")
        assertThat(formatDistanceKm(12_345.0, decimals = 1)).isEqualTo("12.3")
        assertThat(formatDistance(0.0)).isEqualTo("0.00 km")
    }

    @Test fun pace() {
        assertThat(formatPace(332.0)).isEqualTo("5:32")
        assertThat(formatPace(59.6)).isEqualTo("1:00")
        assertThat(formatPace(null)).isEqualTo("--:--")
        assertThat(formatPace(3600.0)).isEqualTo("--:--")
        assertThat(formatPace(Double.POSITIVE_INFINITY)).isEqualTo("--:--")
        assertThat(formatPaceWithUnit(332.0)).isEqualTo("5:32 /km")
    }

    @Test fun duration() {
        assertThat(formatDuration(32 * 60_000L + 12_000)).isEqualTo("32:12")
        assertThat(formatDuration(3_723_000)).isEqualTo("1:02:03")
        assertThat(formatDuration(-5)).isEqualTo("0:00")
        assertThat(formatDurationShort(2 * 3_600_000L + 19 * 60_000L)).isEqualTo("2h 19m")
        assertThat(formatDurationShort(45 * 60_000L)).isEqualTo("45m 0s")
        assertThat(formatDurationShort(38_000)).isEqualTo("38s")
        // Totals no longer truncate: 5:59 is not "5m".
        assertThat(formatDurationShort(5 * 60_000L + 59_000)).isEqualTo("5m 59s")
        assertThat(formatDurationShort(3_600_000L + 5 * 60_000L + 30_000)).isEqualTo("1h 05m")
        assertThat(formatDurationShort(0)).isEqualTo("0s")
        // Whole-minute axis style.
        assertThat(formatDurationShort(5 * 60_000L, withSeconds = false)).isEqualTo("5m")
        assertThat(formatDurationShort(0, withSeconds = false)).isEqualTo("0m")
        assertThat(formatDurationShort(2 * 3_600_000L, withSeconds = false)).isEqualTo("2h 00m")
    }

    @Test fun misc() {
        assertThat(formatElevation(105.6)).isEqualTo("106 m")
        assertThat(formatElevation(null)).isEqualTo("–")
        assertThat(formatCalories(412.4)).isEqualTo("412")
        assertThat(formatCalories(null)).isEqualTo("–")
        assertThat(formatCadence(171.7)).isEqualTo("172")
        assertThat(formatWeightKg(72.0)).isEqualTo("72")
        assertThat(formatWeightKg(72.5)).isEqualTo("72.5")
        assertThat(formatWeightKg(72.25)).isEqualTo("72.3")
        assertThat(formatFileSize(1)).isEqualTo("1 KB")
        assertThat(formatFileSize(12_345)).isEqualTo("13 KB")
        assertThat(formatFileSize(1_250_000)).isEqualTo("1.3 MB")
    }

    @Test fun dates() {
        val t = ZonedDateTime.of(2026, 11, 24, 7, 2, 0, 0, ZoneId.of(zone)).toInstant().toEpochMilli()
        assertThat(formatRunDateTime(t, zone)).isEqualTo("Tue, 24 Nov 2026 · 7:02 AM")
        assertThat(formatRunDate(t, zone)).isEqualTo("24 Nov 2026")
    }

    @Test fun relativeDay() {
        val now = ZonedDateTime.of(2026, 11, 24, 20, 0, 0, 0, ZoneId.of(zone)).toInstant().toEpochMilli()
        val clock = FakeClock(now)
        val day = 24 * 3_600_000L
        assertThat(formatRelativeDay(now - 3_600_000L, zone, clock)).isEqualTo("Today")
        assertThat(formatRelativeDay(now - day, zone, clock)).isEqualTo("Yesterday")
        assertThat(formatRelativeDay(now - 2 * day, zone, clock)).isEqualTo("Sun")
        assertThat(formatRelativeDay(now - 12 * day, zone, clock)).isEqualTo("12 Nov")
    }

    @Test fun gpsAndDevFormatters() {
        assertThat(formatAccuracy(4.6f)).isEqualTo("±5 m")
        assertThat(formatAccuracy(null)).isEqualTo("–")
        assertThat(formatAccuracy(Float.NaN)).isEqualTo("–")
        assertThat(formatMeters(4.24)).isEqualTo("4.2 m")
        assertThat(formatMeters(312.4)).isEqualTo("312 m")
        assertThat(formatMeters(null)).isEqualTo("–")
        assertThat(formatSecondsPrecise(1_016.0)).isEqualTo("1.02 s")
        assertThat(formatSecondsPrecise(null)).isEqualTo("–")
        assertThat(formatPercent(0.426)).isEqualTo("43 %")
    }

    @Test fun periodLabels() {
        val z = ZoneId.of(zone)
        val t = ZonedDateTime.of(2026, 11, 24, 0, 0, 0, 0, z).toInstant().toEpochMilli()
        assertThat(formatWeekLabel(t, z)).isEqualTo("24 Nov")
        assertThat(formatMonthLabel(t, z)).isEqualTo("Nov")
        assertThat(formatMonthLabel(t, z, withYear = true)).isEqualTo("Nov 2026")
    }
}
