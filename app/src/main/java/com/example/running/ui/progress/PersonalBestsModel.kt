package com.example.running.ui.progress

import com.example.running.core.model.BestEffortDistance
import com.example.running.core.model.PersonalBests
import com.example.running.core.util.formatDistance
import com.example.running.core.util.formatDuration
import com.example.running.core.util.formatPaceWithUnit
import com.example.running.core.util.formatRunDate
import com.example.running.core.util.paceSecPerKm

/** What a PB row records: a best effort over one of the [BestEffortDistance]s, or a "longest" record. */
internal sealed interface PbKind {
    data class Effort(val distance: BestEffortDistance) : PbKind
    data object LongestDistance : PbKind
    data object LongestDuration : PbKind
}

/** One record line. [runId] null means the record is not set yet. */
internal data class PbRow(
    val kind: PbKind,
    val value: String?,
    /** Average pace for efforts; null for the "longest" records. */
    val pace: String?,
    val runName: String?,
    val date: String?,
    val runId: String?,
)

/** Pure: every [BestEffortDistance] in declaration order, then longest distance, longest time. */
internal fun buildPbRows(pb: PersonalBests, zoneId: String): List<PbRow> {
    val rows = BestEffortDistance.entries.map { d ->
        val rec = pb.efforts[d]
        if (rec == null) {
            PbRow(PbKind.Effort(d), null, null, null, null, null)
        } else PbRow(
            kind = PbKind.Effort(d),
            value = formatDuration(rec.effort.durationMillis),
            pace = formatPaceWithUnit(paceSecPerKm(d.meters, rec.effort.durationMillis)),
            runName = rec.runName,
            date = formatRunDate(rec.runStartedAtMillis, zoneId),
            runId = rec.runId,
        )
    }
    val longestDistance = pb.longestDistance?.takeIf { it.distanceM > 0.0 }?.let {
        PbRow(PbKind.LongestDistance, formatDistance(it.distanceM), null, it.runName, formatRunDate(it.runStartedAtMillis, zoneId), it.runId)
    } ?: PbRow(PbKind.LongestDistance, null, null, null, null, null)
    val longestDuration = pb.longestDuration?.takeIf { it.movingMillis > 0L }?.let {
        PbRow(PbKind.LongestDuration, formatDuration(it.movingMillis), null, it.runName, formatRunDate(it.runStartedAtMillis, zoneId), it.runId)
    } ?: PbRow(PbKind.LongestDuration, null, null, null, null, null)
    return rows + longestDistance + longestDuration
}
