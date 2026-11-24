package com.example.running.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.running.core.model.LocationEngine
import com.example.running.core.model.PointKind
import com.example.running.core.model.SegmentKind

@Entity(
    tableName = "track_points",
    foreignKeys = [ForeignKey(RunEntity::class, ["id"], ["run_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["run_id", "kind", "time_millis"])],
)
data class TrackPointEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "run_id") val runId: String,
    @ColumnInfo(name = "kind") val kind: PointKind,
    @ColumnInfo(name = "engine") val engine: LocationEngine?,
    @ColumnInfo(name = "is_primary") val isPrimary: Boolean,
    @ColumnInfo(name = "time_millis") val timeMillis: Long,
    @ColumnInfo(name = "elapsed_realtime_nanos") val elapsedRealtimeNanos: Long?,
    @ColumnInfo(name = "lat") val lat: Double,
    @ColumnInfo(name = "lon") val lon: Double,
    @ColumnInfo(name = "altitude_m") val altitudeM: Double?,
    @ColumnInfo(name = "msl_altitude_m") val mslAltitudeM: Double?,
    @ColumnInfo(name = "horizontal_accuracy_m") val horizontalAccuracyM: Float?,
    @ColumnInfo(name = "vertical_accuracy_m") val verticalAccuracyM: Float?,
    @ColumnInfo(name = "speed_mps") val speedMps: Float?,
    @ColumnInfo(name = "speed_accuracy_mps") val speedAccuracyMps: Float?,
    @ColumnInfo(name = "bearing_deg") val bearingDeg: Float?,
    @ColumnInfo(name = "paused") val paused: Boolean,
    // ---- FILTERED-only (null/false on RAW) ----
    @ColumnInfo(name = "step_filled") val stepFilled: Boolean,
    @ColumnInfo(name = "distance_m") val distanceM: Double?,
    @ColumnInfo(name = "elevation_m") val elevationM: Double?,
    @ColumnInfo(name = "pace_sec_per_km") val paceSecPerKm: Double?,
    @ColumnInfo(name = "segment_kind") val segmentKind: SegmentKind?,
)
