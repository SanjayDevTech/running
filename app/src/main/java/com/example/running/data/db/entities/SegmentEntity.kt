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
    tableName = "segments",
    foreignKeys = [ForeignKey(RunEntity::class, ["id"], ["run_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["run_id", "start_millis"])],
)
data class SegmentEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "run_id") val runId: String,
    @ColumnInfo(name = "kind") val kind: SegmentKind,
    @ColumnInfo(name = "start_millis") val startMillis: Long,
    @ColumnInfo(name = "end_millis") val endMillis: Long,
    @ColumnInfo(name = "start_distance_m") val startDistanceM: Double,
    @ColumnInfo(name = "end_distance_m") val endDistanceM: Double,
    @ColumnInfo(name = "avg_speed_mps") val avgSpeedMps: Double?,
    @ColumnInfo(name = "avg_cadence_spm") val avgCadenceSpm: Double?,
)
