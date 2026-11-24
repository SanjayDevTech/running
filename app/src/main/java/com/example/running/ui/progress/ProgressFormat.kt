package com.example.running.ui.progress

import com.example.running.core.stats.PeriodTotals
import com.example.running.core.util.formatMonthLabel
import com.example.running.core.util.formatWeekLabel
import java.time.ZoneId

/**
 * Bottom-axis labels. Weekly bars label every second week counting back from the current one so
 * 12 labels never collide; the others are a single space.
 */
internal fun chartLabels(buckets: List<PeriodTotals>, period: ProgressPeriod, zone: ZoneId): List<String> =
    // Every bar gets its label; AppBarChart thins them out (Vico rejects blank labels).
    buckets.map { b ->
        when (period) {
            ProgressPeriod.WEEKLY -> formatWeekLabel(b.startMillis, zone)
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
