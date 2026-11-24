package com.example.running.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.running.core.model.ActivityType
import com.example.running.core.model.ElevationSource
import com.example.running.core.model.LocationEngine
import com.example.running.core.model.LocationEngineMode
import com.example.running.core.model.RunStatus

@Entity(
    tableName = "runs",
    indices = [Index("started_at_millis"), Index("status"), Index(value = ["type", "started_at_millis"])],
)
data class RunEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "name_is_custom") val nameIsCustom: Boolean,
    @ColumnInfo(name = "type") val type: ActivityType,
    @ColumnInfo(name = "type_is_manual") val typeIsManual: Boolean,
    @ColumnInfo(name = "status") val status: RunStatus,
    @ColumnInfo(name = "started_at_millis") val startedAtMillis: Long,
    @ColumnInfo(name = "ended_at_millis") val endedAtMillis: Long?,
    @ColumnInfo(name = "zone_id") val zoneId: String,
    @ColumnInfo(name = "engine_mode") val engineMode: LocationEngineMode,
    @ColumnInfo(name = "start_engine") val startEngine: LocationEngine,
    @ColumnInfo(name = "engine_fallback") val engineFallback: Boolean,
    @ColumnInfo(name = "dev_dual_engine") val devDualEngine: Boolean,
    // ---- RunStats (written by saveAnalysis; zeros/nulls while ACTIVE) ----
    @ColumnInfo(name = "distance_m") val distanceM: Double,
    @ColumnInfo(name = "elapsed_millis") val elapsedMillis: Long,
    @ColumnInfo(name = "moving_millis") val movingMillis: Long,
    @ColumnInfo(name = "paused_millis") val pausedMillis: Long,
    @ColumnInfo(name = "avg_pace_sec_per_km") val avgPaceSecPerKm: Double?,
    @ColumnInfo(name = "max_speed_mps") val maxSpeedMps: Double?,
    @ColumnInfo(name = "elevation_gain_m") val elevationGainM: Double?,
    @ColumnInfo(name = "elevation_loss_m") val elevationLossM: Double?,
    @ColumnInfo(name = "elevation_source") val elevationSource: ElevationSource,
    @ColumnInfo(name = "calories_kcal") val caloriesKcal: Double?,
    @ColumnInfo(name = "steps") val steps: Int?,
    @ColumnInfo(name = "avg_cadence_spm") val avgCadenceSpm: Double?,
    @ColumnInfo(name = "walk_fraction") val walkFraction: Double?,
    @ColumnInfo(name = "step_filled_m") val stepFilledM: Double,
    @ColumnInfo(name = "min_lat") val minLat: Double?,
    @ColumnInfo(name = "min_lon") val minLon: Double?,
    @ColumnInfo(name = "max_lat") val maxLat: Double?,
    @ColumnInfo(name = "max_lon") val maxLon: Double?,
    @ColumnInfo(name = "weight_kg_used") val weightKgUsed: Double?,
    @ColumnInfo(name = "stride_m_used") val strideMUsed: Double?,
    @ColumnInfo(name = "analysis_version") val analysisVersion: Int,
    @ColumnInfo(name = "created_at_millis") val createdAtMillis: Long,
    @ColumnInfo(name = "updated_at_millis") val updatedAtMillis: Long,
)
