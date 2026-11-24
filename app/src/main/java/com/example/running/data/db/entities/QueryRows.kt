package com.example.running.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.running.core.model.BestEffortDistance
import com.example.running.core.model.SegmentKind
import com.example.running.core.model.SensorType

/** Query result: one effort with its all-time rank among FINISHED runs of the same type. */
data class RankedEffortRow(
    @Embedded val effort: BestEffortEntity,
    @ColumnInfo(name = "effort_rank") val rank: Int,
)

/** Query result: the fastest effort for one distance plus its run's name and start. */
data class BestEffortRunRow(
    @Embedded val effort: BestEffortEntity,
    @ColumnInfo(name = "run_name") val runName: String,
    @ColumnInfo(name = "run_started_at_millis") val runStartedAtMillis: Long,
)
