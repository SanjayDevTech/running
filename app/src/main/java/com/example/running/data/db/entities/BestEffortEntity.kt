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

@Entity(
    tableName = "best_efforts",
    primaryKeys = ["run_id", "distance"],
    foreignKeys = [ForeignKey(RunEntity::class, ["id"], ["run_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["distance", "duration_millis"])],
)
data class BestEffortEntity(
    @ColumnInfo(name = "run_id") val runId: String,
    @ColumnInfo(name = "distance") val distance: BestEffortDistance,
    /** Moving time over the window. */
    @ColumnInfo(name = "duration_millis") val durationMillis: Long,
    @ColumnInfo(name = "start_millis") val startMillis: Long,
    @ColumnInfo(name = "end_millis") val endMillis: Long,
    @ColumnInfo(name = "start_distance_m") val startDistanceM: Double,
)
