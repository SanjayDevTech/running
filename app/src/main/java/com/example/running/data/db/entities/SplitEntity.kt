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
    tableName = "splits",
    primaryKeys = ["run_id", "split_index"],
    foreignKeys = [ForeignKey(RunEntity::class, ["id"], ["run_id"], onDelete = ForeignKey.CASCADE)],
)
data class SplitEntity(
    @ColumnInfo(name = "run_id") val runId: String,
    /** 0 = first km. */
    @ColumnInfo(name = "split_index") val splitIndex: Int,
    /** 1000.0 except a partial last split. */
    @ColumnInfo(name = "distance_m") val distanceM: Double,
    @ColumnInfo(name = "start_millis") val startMillis: Long,
    @ColumnInfo(name = "end_millis") val endMillis: Long,
    @ColumnInfo(name = "moving_millis") val movingMillis: Long,
    @ColumnInfo(name = "elapsed_millis") val elapsedMillis: Long,
    /** Moving pace, normalised to /km. */
    @ColumnInfo(name = "pace_sec_per_km") val paceSecPerKm: Double?,
    @ColumnInfo(name = "elevation_gain_m") val elevationGainM: Double?,
    @ColumnInfo(name = "elevation_loss_m") val elevationLossM: Double?,
    @ColumnInfo(name = "avg_cadence_spm") val avgCadenceSpm: Double?,
    @ColumnInfo(name = "walk_fraction") val walkFraction: Double?,
)
