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
    tableName = "sensor_samples",
    foreignKeys = [ForeignKey(RunEntity::class, ["id"], ["run_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["run_id", "type", "time_millis"])],
)
data class SensorSampleEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "run_id") val runId: String,
    @ColumnInfo(name = "type") val type: SensorType,
    @ColumnInfo(name = "time_millis") val timeMillis: Long,
    /** STEP_DETECTOR: 1.0; STEP_COUNTER: cumulative count; PRESSURE: hPa. */
    @ColumnInfo(name = "value") val value: Double,
)
