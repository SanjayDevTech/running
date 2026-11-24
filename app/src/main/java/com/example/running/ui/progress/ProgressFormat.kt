package com.example.running.ui.progress

import com.example.running.core.stats.PeriodTotals
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// SCR-E-1: chart axis / period captions belong in core/util/Format.kt next to formatRunDate.
// Proposed: fun formatWeekLabel(startMillis: Long, zone: ZoneId): String            // "24 Nov"
//           fun formatMonthLabel(startMillis: Long, zone: ZoneId, withYear: Boolean = false): String // "Nov" / "Nov 2026"
// Stand-in below uses the same locale (en) as Format.kt; remove after integration.
private val EN = Locale.ENGLISH
private val WEEK = DateTimeFormatter.ofPattern("d MMM", EN)
private val MONTH = DateTimeFormatter.ofPattern("MMM", EN)
private val MONTH_YEAR = DateTimeFormatter.ofPattern("MMM yyyy", EN)

internal fun formatWeekLabel(startMillis: Long, zone: ZoneId): String =
    WEEK.format(Instant.ofEpochMilli(startMillis).atZone(zone))

internal fun formatMonthLabel(startMillis: Long, zone: ZoneId, withYear: Boolean = false): String =
    (if (withYear) MONTH_YEAR else MONTH).format(Instant.ofEpochMilli(startMillis).atZone(zone))

/**
 * Bottom-axis labels. Weekly bars label every second week counting back from the current one so
 * 12 labels never collide; the others are a single space.
 */
internal fun chartLabels(buckets: List<PeriodTotals>, period: ProgressPeriod, zone: ZoneId): List<String> =
    buckets.mapIndexed { i, b ->
        when (period) {
            ProgressPeriod.WEEKLY ->
                if ((buckets.lastIndex - i) % 2 == 0) formatWeekLabel(b.startMillis, zone) else " "
            ProgressPeriod.MONTHLY -> formatMonthLabel(b.startMillis, zone)
        }
    }

/** Which caption the selected bar gets; the screen maps it to a string resource. */
internal sealed interface Caption {
    data object ThisWeek : Caption
    data object ThisMonth : Caption
    data class WeekOf(val date: String) : Caption
    data class Month(val label: String) : Caption
}

internal fun selectedCaption(t: PeriodTotals, period: ProgressPeriod, isCurrent: Boolean, zone: ZoneId): Caption =
    when (period) {
        ProgressPeriod.WEEKLY ->
            if (isCurrent) Caption.ThisWeek else Caption.WeekOf(formatWeekLabel(t.startMillis, zone))
        ProgressPeriod.MONTHLY ->
            if (isCurrent) Caption.ThisMonth else Caption.Month(formatMonthLabel(t.startMillis, zone, withYear = true))
    }
