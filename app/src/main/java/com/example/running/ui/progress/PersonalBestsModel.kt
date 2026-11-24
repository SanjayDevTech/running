package com.example.running.ui.progress

import com.example.running.core.model.BestEffortDistance
import com.example.running.core.model.PersonalBests
import com.example.running.core.util.formatDistance
import com.example.running.core.util.formatDuration
import com.example.running.core.util.formatPaceWithUnit
import com.example.running.core.util.formatRunDate
import com.example.running.core.util.paceSecPerKm

internal enum class PbId(val distance: BestEffortDistance?) {
    K1(BestEffortDistance.K1),
    K5(BestEffortDistance.K5),
    K10(BestEffortDistance.K10),
    HALF(BestEffortDistance.HALF),
    LONGEST_DISTANCE(null),
    LONGEST_DURATION(null),
}

/** One record line. [runId] null means the record is not set yet. */
internal data class PbRow(
    val id: PbId,
    val value: String?,
    /** Average pace for efforts; null for the "longest" records. */
    val pace: String?,
    val runName: String?,
    val date: String?,
    val runId: String?,
)

/** Pure: fixed order 1K, 5K, 10K, half, longest distance, longest time. */
internal fun buildPbRows(pb: PersonalBests, zoneId: String): List<PbRow> {
    val rows = BestEffortDistance.entries.map { d ->
        val rec = pb.efforts[d]
        if (rec == null) {
            PbRow(PbId.valueOf(d.name), null, null, null, null, null)
        } else PbRow(
            id = PbId.valueOf(d.name),
            value = formatDuration(rec.effort.durationMillis),
            pace = formatPaceWithUnit(paceSecPerKm(d.meters, rec.effort.durationMillis)),
            runName = rec.runName,
            date = formatRunDate(rec.runStartedAtMillis, zoneId),
            runId = rec.runId,
        )
    }
    val longestDistance = pb.longestDistance?.takeIf { it.distanceM > 0.0 }?.let {
        PbRow(PbId.LONGEST_DISTANCE, formatDistance(it.distanceM), null, it.runName, formatRunDate(it.runStartedAtMillis, zoneId), it.runId)
    } ?: PbRow(PbId.LONGEST_DISTANCE, null, null, null, null, null)
    val longestDuration = pb.longestDuration?.takeIf { it.movingMillis > 0L }?.let {
        PbRow(PbId.LONGEST_DURATION, formatDuration(it.movingMillis), null, it.runName, formatRunDate(it.runStartedAtMillis, zoneId), it.runId)
    } ?: PbRow(PbId.LONGEST_DURATION, null, null, null, null, null)
    return rows + longestDistance + longestDuration
}
