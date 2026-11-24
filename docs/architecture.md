# Running — architecture contract

Single source of truth for the multi-agent build. Run 1 writes **FOUNDATION** (everything marked
FOUNDATION below, plus compiling stubs for every contract). Run 2 runs six tracks (A–F) in parallel
git worktrees. Run 3 integrates. If code and this document disagree, this document wins until the
integrator changes it.

Package root: `com.example.running` (source root `app/src/main/java/com/example/running/`, written
`…/` below). Test root `app/src/test/java/com/example/running/`, written `test/…/`.

Decisions that are already settled live in the project memory (`project-running-app.md`); this file
turns them into files, types and rules. Anything not covered here: ask in the track report, do not
invent a shared API.

---

## 0. Ground rules in one screen

1. **Ownership is by path.** Every file has exactly one owner (FOUNDATION or A–F). You may only
   create or edit files you own. New files inside a directory you own are yours automatically.
2. **Interfaces and models are FOUNDATION; implementations are tracks.** Every cross-track type
   lives in `core/` and is read-only for tracks. A track implements the interface in its own package.
3. **Contract files** (marked `CONTRACT` in §1) are created by FOUNDATION as compiling stubs and then
   *owned by the implementing track*. The track fills in bodies and may add private/internal code,
   but must not change the public signature (names, parameter lists, defaults, return types).
4. **No new dependencies, manifest entries, DB columns, DataStore keys, routes or util helpers from
   tracks.** File a SHARED-CHANGE REQUEST (§5.3) instead.
5. Every worktree must stay green on `./gradlew assembleDebug testDebugUnitTest`.

---

## 1. Package layout and FILE OWNERSHIP MAP

Legend: **F0** = FOUNDATION (read-only for all tracks). **A–F** = track. `CONTRACT(X)` = created by
FOUNDATION as a stub, then owned by track X with a frozen public signature.

### 1.1 Repository root / build (all F0)

| Path | Owner | Notes |
|---|---|---|
| `settings.gradle.kts` | F0 | `rootProject.name = "Running"` |
| `build.gradle.kts` | F0 | plugin aliases `apply false` |
| `gradle.properties` | F0 | keep template (config cache on) |
| `gradle/libs.versions.toml` | F0 | §4 |
| `gradle/wrapper/**`, `gradle/gradle-daemon-jvm.properties` | F0 | Gradle 9.6.0, JDK 25 toolchain |
| `app/build.gradle.kts` | F0 | §4.3 |
| `app/proguard-rules.pro` | F0 | §7 |
| `app/schemas/**` | F0 | Room-generated; changes only via schema SCR |
| `app/src/main/AndroidManifest.xml` | F0 | §5.6 |
| `app/src/main/res/values/strings.xml` | F0 | app name, tab labels, common words |
| `app/src/main/res/values/themes.xml`, `colors.xml` | F0 | window background black |
| `app/src/main/res/xml/**` | F0 | backup rules (see §5.6) |
| `app/src/main/res/mipmap-*/**`, `drawable/ic_launcher_*` | F0 | launcher icon |
| `.gitignore`, `LICENSE`, `README.md` | F0 | copied pattern from food-tracker |
| `github-public-release.jks` | F0 | generated in Run 1, §7 |
| `.github/actions/build-apk/action.yml` | F0 | §7 |
| `.github/workflows/build-release-apk.yml`, `trigger-release.yml` | F0 | §7 |
| `docs/architecture.md` (this), `docs/ui-house-rules.md` | F0 | |
| `app/src/main/keepRules/` (template) | F0 | deleted in Run 1 (rules go in `proguard-rules.pro`) |

### 1.2 Main source

```
…/
├── RunningApp.kt                         F0   Application; builds AppContainer; MapModule.initialize(); channels
├── MainActivity.kt                       F0   edge-to-edge, theme, nav host, deep links, recovery check
├── di/
│   ├── AppContainer.kt                   F0   manual DI root (§2.6)
│   ├── CoreDeps.kt                       F0
│   └── AppViewModel.kt                   F0   LocalAppContainer + appViewModel {} helper
├── core/
│   ├── model/
│   │   ├── Enums.kt                      F0
│   │   ├── Geo.kt                        F0   GeoPoint, GeoBounds
│   │   ├── Run.kt                        F0   Run, RunStats
│   │   ├── TrackPoint.kt                 F0
│   │   ├── RunParts.kt                   F0   PauseInterval, Split, Segment, BestEffort, BestEffortWithRank, SensorSample
│   │   ├── RunGraph.kt                   F0
│   │   ├── PersonalBests.kt              F0
│   │   ├── Profile.kt                    F0   RunnerProfile
│   │   ├── Settings.kt                   F0   AppSettings, BackupSettings
│   │   ├── LocationFix.kt                F0
│   │   ├── Recording.kt                  F0   RecordingState, GpsStatus, LiveStats, RecordingEvent
│   │   ├── Analysis.kt                   F0   AnalysisInput, RunAnalysis, RunSeries, SeriesPoint
│   │   └── MapModels.kt                  F0   MapRoute, RoutePoint, RouteColoring, RouteMapMode, OfflineAreaStatus
│   ├── contracts/
│   │   ├── RecordingController.kt        F0   interface (impl A)
│   │   ├── LocationSource.kt             F0   LocationSource, StepSource, PressureSource (impl A)
│   │   ├── Analysis.kt                   F0   RunAnalyzer, LiveMetricsFactory, LiveMetrics, RunNamer, RunSeriesBuilder (impl B)
│   │   ├── MapOfflineManager.kt          F0   interface (impl C)
│   │   └── LocationAccuracyChecker.kt    F0   interface (impl F)
│   ├── util/
│   │   ├── Clock.kt                      F0   Clock, RealClock
│   │   ├── AppDispatchers.kt             F0
│   │   ├── GeoMath.kt                    F0   haversine, bearing, interpolate, metres per degree (bounds: core/model/Geo.kt)
│   │   ├── Units.kt                      F0   pace/speed conversions, constants
│   │   ├── Format.kt                     F0   all user-visible number/time formatting
│   │   ├── DateTimeUtil.kt               F0   week/month boundaries, epoch-day helpers
│   │   └── Ids.kt                        F0   newRunId()
│   ├── stats/
│   │   └── Aggregation.kt                F0   period totals, week/month/day buckets
│   ├── system/
│   │   ├── Permissions.kt                F0   permission checks + intents
│   │   └── BatteryOptimization.kt        F0
│   ├── notifications/
│   │   └── NotificationChannels.kt       F0   channel ids + creation, notification ids
│   └── demo/
│       └── SyntheticRuns.kt              F0   deterministic run generator (debug seeding + tests)
├── data/
│   ├── db/
│   │   ├── RunningDatabase.kt            F0
│   │   ├── entities/*.kt                 F0   7 entities, one file each (§2.1) + QueryRows.kt
│   │   ├── dao/*.kt                      F0   RunDao, TrackPointDao, RunPartsDao, SensorSampleDao
│   │   └── Mappers.kt                    F0   entity <-> domain
│   ├── repo/
│   │   ├── RunRepository.kt              F0   interface
│   │   └── RoomRunRepository.kt          F0   full implementation
│   └── settings/
│       ├── SettingsRepository.kt         F0   interface
│       ├── SettingsKeys.kt               F0
│       └── DataStoreSettingsRepository.kt F0  full implementation
├── ui/
│   ├── theme/ Color.kt Type.kt Tokens.kt Theme.kt              F0
│   ├── components/                                             F0
│   │   ├── StatTile.kt  SectionHeader.kt  Buttons.kt  Chips.kt
│   │   ├── Dialogs.kt  AppTopBar.kt  EmptyState.kt  ActivityTypeIcon.kt
│   │   └── charts/ ChartModels.kt AppLineChart.kt AppBarChart.kt
│   ├── navigation/ Routes.kt AppNavHost.kt BottomBar.kt DeepLinks.kt  F0
│   ├── record/                           A    RecordScreen.kt = CONTRACT(A); rest free
│   ├── dev/                              A    DevEngineCompareScreen.kt = CONTRACT(A)
│   ├── activities/                       D    ActivitiesScreen.kt = CONTRACT(D)
│   ├── rundetail/                        D    RunDetailScreen.kt = CONTRACT(D)
│   ├── progress/                         E    ProgressScreen.kt, PersonalBestsScreen.kt = CONTRACT(E)
│   ├── onboarding/                       F    OnboardingScreen.kt = CONTRACT(F)
│   └── settings/                         F    SettingsScreen.kt, ProfileEditScreen.kt, BackupScreen.kt = CONTRACT(F)
├── recording/                            A    RecordingModule.kt = CONTRACT(A); controller, service, notification, finalizer, recovery
├── location/                             A    engines (Play / platform), engine manager, fallback, dual recorder
├── sensors/                              A    Android step + pressure sources
├── analysis/                             B    AnalysisModule.kt = CONTRACT(B); filters, analyzer, naming, calories…
├── map/                                  C    MapModule.kt, RouteMap.kt, RouteThumbnail.kt = CONTRACT(C); style, offline, cache
├── backup/                               F    payload, manager, worker, scheduler
└── setup/                                F    SetupModule.kt = CONTRACT(F); PlayLocationAccuracyChecker.kt
```

### 1.3 Resources and assets

| Path | Owner |
|---|---|
| `res/values/strings.xml` | F0 |
| `res/values/strings_record.xml` (prefix `record_`, `dev_`) | A |
| `res/values/strings_analysis.xml` | B (normally unused: `analysis/` is pure Kotlin, see §5.4) |
| `res/values/strings_map.xml` (prefix `map_`) | C |
| `res/values/strings_history.xml` (prefix `history_`, `detail_`) | D |
| `res/values/strings_progress.xml` (prefix `progress_`, `pb_`) | E |
| `res/values/strings_setup.xml` (prefix `onboarding_`, `settings_`, `backup_`) | F |
| `res/drawable/ic_<track>_*.xml` (`ic_record_`, `ic_map_`, `ic_history_`, `ic_progress_`, `ic_setup_`) | that track |
| `app/src/main/assets/map/**` (style JSON, sprites if any) | C |

Icons: prefer `Icons.Rounded.*` / `Icons.Outlined.*` from material-icons-extended (already a dependency) over new drawables.

### 1.4 Tests

| Path | Owner |
|---|---|
| `test/…/testing/**` (fakes, fixture loader, rules) | F0 |
| `app/src/test/resources/fixtures/f0_*.json` | F0 |
| `test/…/data/**`, `test/…/core/**` | F0 |
| `test/…/recording/**`, `test/…/location/**`, `test/…/sensors/**`, `test/…/ui/record/**` | A |
| `test/…/analysis/**`, `app/src/test/resources/fixtures/b_*.json` | B |
| `test/…/map/**` | C |
| `test/…/ui/activities/**`, `test/…/ui/rundetail/**` | D |
| `test/…/ui/progress/**` | E |
| `test/…/backup/**`, `test/…/setup/**`, `test/…/ui/onboarding/**`, `test/…/ui/settings/**`, `fixtures/f_*.json` | F |
| `app/src/test/resources/fixtures/a_*.json` | A |
| `app/src/androidTest/java/com/example/running/data/db/**` | F0 (MigrationTest scaffold, schema v1 smoke test) |
| `app/src/androidTest/java/com/example/running/<track pkg>/**` | owning track |

---

## 2. SHARED CODE (FOUNDATION writes now, full implementations)

All of §2 is implemented for real in Run 1, not stubbed.

### 2.1 Room

Conventions: table and column names `snake_case` via `@ColumnInfo(name = …)`; Kotlin names
camelCase. Times are epoch millis (`Long`), durations millis (`Long`), distances metres (`Double`),
pace seconds per km (`Double`), speed m/s, altitude metres. Enums are stored as `TEXT` by Room's
built-in enum support (by `name`), so **no `@TypeConverters` class exists**; renaming an enum
constant is a schema change. All child tables have `ForeignKey(onDelete = CASCADE)` to `runs.id`.

```kotlin
// data/db/RunningDatabase.kt
@Database(
    entities = [
        RunEntity::class, TrackPointEntity::class, PauseEntity::class, SplitEntity::class,
        SegmentEntity::class, BestEffortEntity::class, SensorSampleEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class RunningDatabase : RoomDatabase() {
    abstract fun runDao(): RunDao
    abstract fun trackPointDao(): TrackPointDao
    abstract fun runPartsDao(): RunPartsDao
    abstract fun sensorSampleDao(): SensorSampleDao

    companion object {
        const val NAME = "running.db"
        fun build(context: Context): RunningDatabase  // Room.databaseBuilder(...).build(); NO destructive fallback
    }
}
```

```kotlin
// data/db/entities/RunEntity.kt
@Entity(
    tableName = "runs",
    indices = [Index("started_at_millis"), Index("status"), Index(value = ["type", "started_at_millis"])],
)
data class RunEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,                 // UUID string (newRunId())
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "name_is_custom") val nameIsCustom: Boolean,       // true once the user renamed
    @ColumnInfo(name = "type") val type: ActivityType,
    @ColumnInfo(name = "type_is_manual") val typeIsManual: Boolean,       // true once the user changed it
    @ColumnInfo(name = "status") val status: RunStatus,
    @ColumnInfo(name = "started_at_millis") val startedAtMillis: Long,
    @ColumnInfo(name = "ended_at_millis") val endedAtMillis: Long?,
    @ColumnInfo(name = "zone_id") val zoneId: String,                     // ZoneId at start, e.g. "Asia/Kolkata"
    @ColumnInfo(name = "engine_mode") val engineMode: LocationEngineMode, // setting when the run started
    @ColumnInfo(name = "start_engine") val startEngine: LocationEngine,   // engine that drove the first fix
    @ColumnInfo(name = "engine_fallback") val engineFallback: Boolean,    // a mid-run switch happened
    @ColumnInfo(name = "dev_dual_engine") val devDualEngine: Boolean,     // both engines recorded
    // ---- RunStats (written by saveAnalysis; zeros/nulls while ACTIVE) ----
    @ColumnInfo(name = "distance_m") val distanceM: Double,
    @ColumnInfo(name = "elapsed_millis") val elapsedMillis: Long,
    @ColumnInfo(name = "moving_millis") val movingMillis: Long,
    @ColumnInfo(name = "paused_millis") val pausedMillis: Long,
    @ColumnInfo(name = "avg_pace_sec_per_km") val avgPaceSecPerKm: Double?,   // over moving time
    @ColumnInfo(name = "max_speed_mps") val maxSpeedMps: Double?,
    @ColumnInfo(name = "elevation_gain_m") val elevationGainM: Double?,
    @ColumnInfo(name = "elevation_loss_m") val elevationLossM: Double?,
    @ColumnInfo(name = "elevation_source") val elevationSource: ElevationSource,
    @ColumnInfo(name = "calories_kcal") val caloriesKcal: Double?,
    @ColumnInfo(name = "steps") val steps: Int?,
    @ColumnInfo(name = "avg_cadence_spm") val avgCadenceSpm: Double?,
    @ColumnInfo(name = "walk_fraction") val walkFraction: Double?,            // 0..1 of moving distance
    @ColumnInfo(name = "step_filled_m") val stepFilledM: Double,
    @ColumnInfo(name = "min_lat") val minLat: Double?,
    @ColumnInfo(name = "min_lon") val minLon: Double?,
    @ColumnInfo(name = "max_lat") val maxLat: Double?,
    @ColumnInfo(name = "max_lon") val maxLon: Double?,
    @ColumnInfo(name = "weight_kg_used") val weightKgUsed: Double?,
    @ColumnInfo(name = "stride_m_used") val strideMUsed: Double?,
    @ColumnInfo(name = "analysis_version") val analysisVersion: Int,          // 0 = never analysed
    @ColumnInfo(name = "created_at_millis") val createdAtMillis: Long,
    @ColumnInfo(name = "updated_at_millis") val updatedAtMillis: Long,
)
```

```kotlin
// data/db/entities/TrackPointEntity.kt
@Entity(
    tableName = "track_points",
    foreignKeys = [ForeignKey(RunEntity::class, ["id"], ["run_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["run_id", "kind", "time_millis"])],
)
data class TrackPointEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "run_id") val runId: String,
    @ColumnInfo(name = "kind") val kind: PointKind,                 // RAW (engine fix) | FILTERED (analysis output)
    @ColumnInfo(name = "engine") val engine: LocationEngine?,       // RAW: always set; FILTERED: source engine, null if step-filled
    @ColumnInfo(name = "is_primary") val isPrimary: Boolean,        // RAW: came from the engine driving the run at that moment
    @ColumnInfo(name = "time_millis") val timeMillis: Long,
    @ColumnInfo(name = "elapsed_realtime_nanos") val elapsedRealtimeNanos: Long?,
    @ColumnInfo(name = "lat") val lat: Double,
    @ColumnInfo(name = "lon") val lon: Double,
    @ColumnInfo(name = "altitude_m") val altitudeM: Double?,              // WGS84 ellipsoid
    @ColumnInfo(name = "msl_altitude_m") val mslAltitudeM: Double?,       // Location.mslAltitudeMeters (API 34+)
    @ColumnInfo(name = "horizontal_accuracy_m") val horizontalAccuracyM: Float?,
    @ColumnInfo(name = "vertical_accuracy_m") val verticalAccuracyM: Float?,
    @ColumnInfo(name = "speed_mps") val speedMps: Float?,                 // Doppler speed
    @ColumnInfo(name = "speed_accuracy_mps") val speedAccuracyMps: Float?,
    @ColumnInfo(name = "bearing_deg") val bearingDeg: Float?,
    @ColumnInfo(name = "paused") val paused: Boolean,
    // ---- FILTERED-only (null/false on RAW) ----
    @ColumnInfo(name = "step_filled") val stepFilled: Boolean,
    @ColumnInfo(name = "distance_m") val distanceM: Double?,              // cumulative, excludes paused movement
    @ColumnInfo(name = "elevation_m") val elevationM: Double?,            // fused (baro or smoothed GPS)
    @ColumnInfo(name = "pace_sec_per_km") val paceSecPerKm: Double?,      // smoothed, for charts/map colour
    @ColumnInfo(name = "segment_kind") val segmentKind: SegmentKind?,
)
```

```kotlin
// data/db/entities/PauseEntity.kt          table "pauses", index(run_id)
data class PauseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "run_id") val runId: String,
    @ColumnInfo(name = "start_millis") val startMillis: Long,
    @ColumnInfo(name = "end_millis") val endMillis: Long?,        // null = currently paused
)

// data/db/entities/SplitEntity.kt          table "splits", primaryKeys = ["run_id", "split_index"]
data class SplitEntity(
    @ColumnInfo(name = "run_id") val runId: String,
    @ColumnInfo(name = "split_index") val splitIndex: Int,        // 0 = first km
    @ColumnInfo(name = "distance_m") val distanceM: Double,       // 1000.0 except a partial last split
    @ColumnInfo(name = "start_millis") val startMillis: Long,
    @ColumnInfo(name = "end_millis") val endMillis: Long,
    @ColumnInfo(name = "moving_millis") val movingMillis: Long,
    @ColumnInfo(name = "elapsed_millis") val elapsedMillis: Long,
    @ColumnInfo(name = "pace_sec_per_km") val paceSecPerKm: Double?,  // moving pace, normalised to /km
    @ColumnInfo(name = "elevation_gain_m") val elevationGainM: Double?,
    @ColumnInfo(name = "elevation_loss_m") val elevationLossM: Double?,
    @ColumnInfo(name = "avg_cadence_spm") val avgCadenceSpm: Double?,
    @ColumnInfo(name = "walk_fraction") val walkFraction: Double?,
)

// data/db/entities/SegmentEntity.kt        table "segments", index(run_id, start_millis)
data class SegmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "run_id") val runId: String,
    @ColumnInfo(name = "kind") val kind: SegmentKind,             // RUN | WALK | STOP
    @ColumnInfo(name = "start_millis") val startMillis: Long,
    @ColumnInfo(name = "end_millis") val endMillis: Long,
    @ColumnInfo(name = "start_distance_m") val startDistanceM: Double,
    @ColumnInfo(name = "end_distance_m") val endDistanceM: Double,
    @ColumnInfo(name = "avg_speed_mps") val avgSpeedMps: Double?,
    @ColumnInfo(name = "avg_cadence_spm") val avgCadenceSpm: Double?,
)

// data/db/entities/BestEffortEntity.kt     table "best_efforts", primaryKeys = ["run_id", "distance"],
//                                          index(distance, duration_millis)
data class BestEffortEntity(
    @ColumnInfo(name = "run_id") val runId: String,
    @ColumnInfo(name = "distance") val distance: BestEffortDistance,
    @ColumnInfo(name = "duration_millis") val durationMillis: Long,   // moving time over the window
    @ColumnInfo(name = "start_millis") val startMillis: Long,
    @ColumnInfo(name = "end_millis") val endMillis: Long,
    @ColumnInfo(name = "start_distance_m") val startDistanceM: Double,
)

// data/db/entities/SensorSampleEntity.kt   table "sensor_samples", index(run_id, type, time_millis)
data class SensorSampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "run_id") val runId: String,
    @ColumnInfo(name = "type") val type: SensorType,
    @ColumnInfo(name = "time_millis") val timeMillis: Long,
    @ColumnInfo(name = "value") val value: Double,   // STEP_DETECTOR: 1.0; STEP_COUNTER: cumulative count; PRESSURE: hPa
)
```

DAOs (FOUNDATION; tracks never call DAOs directly — they go through `RunRepository`):

```kotlin
@Dao interface RunDao {
    @Insert suspend fun insert(run: RunEntity)
    @Update suspend fun update(run: RunEntity)
    @Query("SELECT * FROM runs WHERE id = :id") suspend fun get(id: String): RunEntity?
    @Query("SELECT * FROM runs WHERE id = :id") fun observe(id: String): Flow<RunEntity?>
    @Query("SELECT * FROM runs WHERE status = 'ACTIVE' ORDER BY started_at_millis DESC LIMIT 1") suspend fun findActive(): RunEntity?
    @Query("SELECT * FROM runs WHERE status = 'FINISHED' AND (:type IS NULL OR type = :type) ORDER BY started_at_millis DESC")
    fun observeFinished(type: ActivityType?): Flow<List<RunEntity>>
    @Query("SELECT * FROM runs WHERE status = 'FINISHED' AND started_at_millis >= :from AND started_at_millis < :to AND (:type IS NULL OR type = :type) ORDER BY started_at_millis")
    fun observeFinishedBetween(from: Long, to: Long, type: ActivityType?): Flow<List<RunEntity>>
    @Query("SELECT id FROM runs") suspend fun allIds(): List<String>
    @Query("SELECT id FROM runs WHERE status = 'FINISHED' ORDER BY started_at_millis") suspend fun finishedIds(): List<String>
    @Query("UPDATE runs SET name = :name, name_is_custom = 1, updated_at_millis = :now WHERE id = :id") suspend fun rename(id: String, name: String, now: Long)
    @Query("UPDATE runs SET type = :type, type_is_manual = 1, updated_at_millis = :now WHERE id = :id") suspend fun setType(id: String, type: ActivityType, now: Long)
    @Query("DELETE FROM runs WHERE id = :id") suspend fun delete(id: String)
    // longest distance / duration for PBs
    @Query("SELECT * FROM runs WHERE status='FINISHED' AND (:type IS NULL OR type=:type) ORDER BY distance_m DESC LIMIT 1") suspend fun longestDistance(type: ActivityType?): RunEntity?
    @Query("SELECT * FROM runs WHERE status='FINISHED' AND (:type IS NULL OR type=:type) ORDER BY moving_millis DESC LIMIT 1") suspend fun longestDuration(type: ActivityType?): RunEntity?
    // Flow twins of the two above (added in Run 1 for observePersonalBests):
    fun observeLongestDistance(type: ActivityType?): Flow<RunEntity?>
    fun observeLongestDuration(type: ActivityType?): Flow<RunEntity?>
    @Query("SELECT COUNT(*) FROM runs WHERE status='FINISHED'") fun observeFinishedCount(): Flow<Int>
}

@Dao interface TrackPointDao {
    @Insert suspend fun insertAll(points: List<TrackPointEntity>)
    @Query("SELECT * FROM track_points WHERE run_id = :runId AND kind = :kind ORDER BY time_millis, id") suspend fun get(runId: String, kind: PointKind): List<TrackPointEntity>
    @Query("SELECT * FROM track_points WHERE run_id = :runId AND kind = 'RAW' AND is_primary = 1 ORDER BY time_millis, id") suspend fun primaryRaw(runId: String): List<TrackPointEntity>
    @Query("SELECT * FROM track_points WHERE run_id = :runId ORDER BY kind, time_millis, id") suspend fun all(runId: String): List<TrackPointEntity>
    @Query("DELETE FROM track_points WHERE run_id = :runId AND kind = :kind") suspend fun deleteKind(runId: String, kind: PointKind)
    @Query("SELECT COUNT(*) FROM track_points WHERE run_id = :runId AND kind = 'RAW'") suspend fun rawCount(runId: String): Int
}

@Dao interface RunPartsDao {
    // pauses
    @Insert suspend fun insertPause(p: PauseEntity): Long
    @Query("UPDATE pauses SET end_millis = :end WHERE run_id = :runId AND end_millis IS NULL") suspend fun closeOpenPause(runId: String, end: Long)
    @Query("SELECT * FROM pauses WHERE run_id = :runId ORDER BY start_millis") suspend fun pauses(runId: String): List<PauseEntity>
    @Insert suspend fun insertPauses(p: List<PauseEntity>)
    // derived parts: replaced wholesale by saveAnalysis
    @Query("DELETE FROM splits WHERE run_id = :runId") suspend fun deleteSplits(runId: String)
    @Query("DELETE FROM segments WHERE run_id = :runId") suspend fun deleteSegments(runId: String)
    @Query("DELETE FROM best_efforts WHERE run_id = :runId") suspend fun deleteBestEfforts(runId: String)
    @Insert suspend fun insertSplits(s: List<SplitEntity>)
    @Insert suspend fun insertSegments(s: List<SegmentEntity>)
    @Insert suspend fun insertBestEfforts(b: List<BestEffortEntity>)
    @Query("SELECT * FROM splits WHERE run_id = :runId ORDER BY split_index") fun observeSplits(runId: String): Flow<List<SplitEntity>>
    @Query("SELECT * FROM segments WHERE run_id = :runId ORDER BY start_millis") fun observeSegments(runId: String): Flow<List<SegmentEntity>>
    @Query("SELECT * FROM splits WHERE run_id = :runId ORDER BY split_index") suspend fun splits(runId: String): List<SplitEntity>
    @Query("SELECT * FROM segments WHERE run_id = :runId ORDER BY start_millis") suspend fun segments(runId: String): List<SegmentEntity>
    @Query("SELECT * FROM best_efforts WHERE run_id = :runId") suspend fun bestEfforts(runId: String): List<BestEffortEntity>
    /** Each effort of one run with its all-time rank among FINISHED runs of the same type (1 = PB). */
    @Query("""
        SELECT be.*, (SELECT COUNT(*) FROM best_efforts o JOIN runs r2 ON r2.id = o.run_id
                      WHERE o.distance = be.distance AND r2.type = r.type AND r2.status = 'FINISHED'
                        AND o.duration_millis < be.duration_millis) + 1 AS effort_rank
        FROM best_efforts be JOIN runs r ON r.id = be.run_id WHERE be.run_id = :runId""")
    fun observeRankedEfforts(runId: String): Flow<List<RankedEffortRow>>
    /** Fastest effort for one distance (type null = all types). */
    @Query("""
        SELECT be.*, r.name AS run_name, r.started_at_millis AS run_started_at_millis
        FROM best_efforts be JOIN runs r ON r.id = be.run_id
        WHERE r.status = 'FINISHED' AND be.distance = :distance AND (:type IS NULL OR r.type = :type)
        ORDER BY be.duration_millis ASC, r.started_at_millis ASC LIMIT 1""")
    fun observeBest(distance: BestEffortDistance, type: ActivityType?): Flow<BestEffortRunRow?>
}
// RankedEffortRow(@Embedded effort: BestEffortEntity, @ColumnInfo(name = "effort_rank") rank: Int)
//   ("rank" is an SQL keyword Room's parser rejects, hence the alias; both rows live in entities/QueryRows.kt)
// BestEffortRunRow(@Embedded effort: BestEffortEntity, runName: String, runStartedAtMillis: Long)

@Dao interface SensorSampleDao {
    @Insert suspend fun insertAll(samples: List<SensorSampleEntity>)
    @Query("SELECT * FROM sensor_samples WHERE run_id = :runId ORDER BY time_millis, id") suspend fun forRun(runId: String): List<SensorSampleEntity>
}
```

`data/db/Mappers.kt`: `RunEntity.toDomain(): Run`, `Run.toEntity(): RunEntity`, and the same pair for
every entity. Domain types never import Room.

Room schema export (gym-tracker pattern):
`ksp { arg("room.schemaLocation", "$projectDir/schemas") }` and
`sourceSets { getByName("androidTest").assets.directories.add("$projectDir/schemas") }`.
FOUNDATION commits `app/schemas/com.example.running.data.db.RunningDatabase/1.json` and an
androidTest `data/db/MigrationTest.kt` that creates v1 via `MigrationTestHelper` and validates it
(ready for future `MIGRATION_1_2`). `fallbackToDestructiveMigration` is forbidden.

### 2.2 Enums (`core/model/Enums.kt`)

```kotlin
enum class ActivityType { RUN, WALK }
enum class ActivityFilter { ALL, RUN, WALK; val type: ActivityType? get() = when (this) { ALL -> null; RUN -> ActivityType.RUN; WALK -> ActivityType.WALK } }
enum class RunStatus { ACTIVE, FINISHED }
enum class LocationEngineMode { AUTO, GOOGLE, BUILT_IN }      // user setting; labels "Auto", "Google", "Built-in"
enum class LocationEngine { PLAY, PLATFORM }                  // what actually produced a fix
enum class PointKind { RAW, FILTERED }
enum class SegmentKind { RUN, WALK, STOP }
enum class SensorType { STEP_DETECTOR, STEP_COUNTER, PRESSURE }
enum class ElevationSource { BAROMETER, GPS, NONE }
enum class Sex { MALE, FEMALE, UNSPECIFIED }
enum class GpsQuality { NONE, POOR, FAIR, GOOD }              // NONE: no fix; POOR >20 m; FAIR 10–20 m; GOOD <=10 m
enum class BestEffortDistance(val meters: Double, val label: String) {
    K1(1_000.0, "1K"), K5(5_000.0, "5K"), K10(10_000.0, "10K"), HALF(21_097.5, "Half marathon")
}
```

### 2.3 Domain models (pure Kotlin, no android.* imports)

```kotlin
// core/model/Geo.kt
data class GeoPoint(val lat: Double, val lon: Double)
data class GeoBounds(val minLat: Double, val minLon: Double, val maxLat: Double, val maxLon: Double) {
    val center: GeoPoint
    fun padded(meters: Double): GeoBounds
    companion object { fun of(points: Iterable<GeoPoint>): GeoBounds? }
}

// core/model/Run.kt
data class RunStats(
    val distanceM: Double = 0.0, val elapsedMillis: Long = 0, val movingMillis: Long = 0, val pausedMillis: Long = 0,
    val avgPaceSecPerKm: Double? = null, val maxSpeedMps: Double? = null,
    val elevationGainM: Double? = null, val elevationLossM: Double? = null, val elevationSource: ElevationSource = ElevationSource.NONE,
    val caloriesKcal: Double? = null, val steps: Int? = null, val avgCadenceSpm: Double? = null,
    val walkFraction: Double? = null, val stepFilledM: Double = 0.0, val bounds: GeoBounds? = null,
    val weightKgUsed: Double? = null, val strideMUsed: Double? = null,
)
data class Run(
    val id: String, val name: String, val nameIsCustom: Boolean,
    val type: ActivityType, val typeIsManual: Boolean, val status: RunStatus,
    val startedAtMillis: Long, val endedAtMillis: Long?, val zoneId: String,
    val engineMode: LocationEngineMode, val startEngine: LocationEngine,
    val engineFallback: Boolean, val devDualEngine: Boolean,
    val stats: RunStats, val analysisVersion: Int,
    val createdAtMillis: Long, val updatedAtMillis: Long,
)

// core/model/TrackPoint.kt  (mirrors TrackPointEntity without id)
data class TrackPoint(
    val runId: String, val kind: PointKind, val engine: LocationEngine?, val isPrimary: Boolean,
    val timeMillis: Long, val elapsedRealtimeNanos: Long?, val lat: Double, val lon: Double,
    val altitudeM: Double?, val mslAltitudeM: Double?,
    val horizontalAccuracyM: Float?, val verticalAccuracyM: Float?,
    val speedMps: Float?, val speedAccuracyMps: Float?, val bearingDeg: Float?,
    val paused: Boolean,
    val stepFilled: Boolean = false, val distanceM: Double? = null, val elevationM: Double? = null,
    val paceSecPerKm: Double? = null, val segmentKind: SegmentKind? = null,
)

// core/model/RunParts.kt
data class PauseInterval(val startMillis: Long, val endMillis: Long?)
data class Split(val index: Int, val distanceM: Double, val startMillis: Long, val endMillis: Long,
    val movingMillis: Long, val elapsedMillis: Long, val paceSecPerKm: Double?,
    val elevationGainM: Double?, val elevationLossM: Double?, val avgCadenceSpm: Double?, val walkFraction: Double?)
data class Segment(val kind: SegmentKind, val startMillis: Long, val endMillis: Long,
    val startDistanceM: Double, val endDistanceM: Double, val avgSpeedMps: Double?, val avgCadenceSpm: Double?)
data class BestEffort(val distance: BestEffortDistance, val durationMillis: Long,
    val startMillis: Long, val endMillis: Long, val startDistanceM: Double)
data class BestEffortWithRank(val effort: BestEffort, val rank: Int) { val isPersonalBest get() = rank == 1 }
data class SensorSample(val type: SensorType, val timeMillis: Long, val value: Double)

// core/model/RunGraph.kt — one run with every child row; unit of backup/import and demo seeding
data class RunGraph(
    val run: Run, val points: List<TrackPoint>, val pauses: List<PauseInterval>,
    val splits: List<Split>, val segments: List<Segment>, val bestEfforts: List<BestEffort>,
    val sensorSamples: List<SensorSample>,
)

// core/model/PersonalBests.kt
data class EffortRecord(val runId: String, val runName: String, val runStartedAtMillis: Long, val effort: BestEffort)
data class RunRecord(val runId: String, val runName: String, val runStartedAtMillis: Long, val distanceM: Double, val movingMillis: Long)
data class PersonalBests(
    val efforts: Map<BestEffortDistance, EffortRecord?>,  // every enum key present
    val longestDistance: RunRecord?, val longestDuration: RunRecord?,
)

// core/model/Profile.kt
data class RunnerProfile(
    val weightKg: Double = 70.0, val ageYears: Int = 30, val sex: Sex = Sex.UNSPECIFIED,
    val heightCm: Int? = null,
    val learnedRunStrideM: Double? = null, val learnedWalkStrideM: Double? = null,
)

// core/model/Settings.kt
data class BackupSettings(val folderUri: String? = null, val intervalHours: Int = 0, val lastBackupAtMillis: Long? = null)
data class AppSettings(
    val onboardingComplete: Boolean = false,
    val profile: RunnerProfile = RunnerProfile(),
    val engineMode: LocationEngineMode = LocationEngineMode.AUTO,
    val devModeUnlocked: Boolean = false,
    val devDualEngine: Boolean = false,
    val backup: BackupSettings = BackupSettings(),
    val batteryPromptDismissed: Boolean = false,
)

// core/model/LocationFix.kt
data class LocationFix(
    val timeMillis: Long, val elapsedRealtimeNanos: Long,
    val lat: Double, val lon: Double,
    val altitudeM: Double?, val mslAltitudeM: Double?,
    val horizontalAccuracyM: Float?, val verticalAccuracyM: Float?,
    val speedMps: Float?, val speedAccuracyMps: Float?, val bearingDeg: Float?,
    val engine: LocationEngine,
) { fun toRawPoint(runId: String, isPrimary: Boolean, paused: Boolean): TrackPoint }
data class StepEvent(val timeMillis: Long, val count: Int = 1)        // STEP_DETECTOR (count=1) or delta from STEP_COUNTER
data class PressureSample(val timeMillis: Long, val hPa: Double)
```

`core/model/Recording.kt`, `Analysis.kt`, `MapModels.kt`: see §3 (they are FOUNDATION models
consumed by contracts).

### 2.4 Repository (`data/repo/RunRepository.kt`, impl `RoomRunRepository`, FOUNDATION)

All writes that touch more than one table run in `database.withTransaction {}`.

```kotlin
interface RunRepository {
    // ---- Recording (A) ----
    suspend fun createActiveRun(
        id: String, startedAtMillis: Long, zoneId: String,
        engineMode: LocationEngineMode, startEngine: LocationEngine, devDualEngine: Boolean,
    ): Run                                                  // name placeholder "Run", type RUN, status ACTIVE
    suspend fun appendRawPoints(points: List<TrackPoint>)  // kind must be RAW; A batches (~every 5 s)
    suspend fun appendSensorSamples(runId: String, samples: List<SensorSample>)
    suspend fun startPause(runId: String, atMillis: Long)
    suspend fun endPause(runId: String, atMillis: Long)     // closes the open pause
    suspend fun markEngineFallback(runId: String)
    suspend fun findActiveRun(): Run?
    suspend fun loadAnalysisInput(runId: String, endedAtMillis: Long, profile: RunnerProfile): AnalysisInput
    /**
     * Finishes a run: replaces FILTERED points, splits, segments, best efforts; writes stats;
     * status = FINISHED; ended_at; analysis_version. Keeps name if nameIsCustom, keeps type if
     * typeIsManual, otherwise applies analysis.suggestedName / suggestedType.
     */
    suspend fun saveAnalysis(runId: String, endedAtMillis: Long, analysis: RunAnalysis)

    // ---- History / detail (D) ----
    fun observeRuns(filter: ActivityFilter): Flow<List<Run>>          // FINISHED, newest first
    fun observeRun(runId: String): Flow<Run?>
    suspend fun getRun(runId: String): Run?
    suspend fun getFilteredPoints(runId: String): List<TrackPoint>
    suspend fun getRawPoints(runId: String): List<TrackPoint>          // all engines (dev compare)
    fun observeSplits(runId: String): Flow<List<Split>>
    fun observeSegments(runId: String): Flow<List<Segment>>
    fun observeBestEfforts(runId: String): Flow<List<BestEffortWithRank>>
    suspend fun rename(runId: String, name: String)
    suspend fun setType(runId: String, type: ActivityType)             // also re-applies auto-name if !nameIsCustom (uses RunNamer passed in ctor)
    suspend fun deleteRun(runId: String)

    // ---- Progress (E) ----
    fun observeRunsBetween(fromMillis: Long, toMillis: Long, filter: ActivityFilter): Flow<List<Run>>
    fun observePersonalBests(filter: ActivityFilter): Flow<PersonalBests>

    // ---- Backup (F) ----
    suspend fun getAllRunIds(): List<String>
    suspend fun getRunGraph(runId: String): RunGraph?
    /** Inserts the whole graph in one transaction. Returns false (and writes nothing) if the id exists. */
    suspend fun importRunGraph(graph: RunGraph): Boolean
}
```

`RoomRunRepository(db: RunningDatabase, clock: Clock, dispatchers: AppDispatchers, runNamer: () -> RunNamer)`
— the namer is a provider so the repository can be built before `AnalysisModule`.

### 2.5 Settings / DataStore (`data/settings/`, FOUNDATION)

DataStore Preferences file name `settings`. Keys (`SettingsKeys.kt`):

| Key | Type | Default |
|---|---|---|
| `onboarding_complete` | Boolean | false |
| `profile_weight_kg` | Double | 70.0 |
| `profile_age_years` | Int | 30 |
| `profile_sex` | String (Sex.name) | UNSPECIFIED |
| `profile_height_cm` | Int (absent = null) | — |
| `learned_run_stride_m` | Double | — |
| `learned_walk_stride_m` | Double | — |
| `engine_mode` | String (LocationEngineMode.name) | AUTO |
| `dev_mode_unlocked` | Boolean | false |
| `dev_dual_engine` | Boolean | false |
| `backup_folder_uri` | String | — |
| `backup_interval_hours` | Int | 0 (off) |
| `last_backup_at_millis` | Long | — |
| `battery_prompt_dismissed` | Boolean | false |

```kotlin
interface SettingsRepository {
    val settings: Flow<AppSettings>
    val profile: Flow<RunnerProfile>                // = settings.map { it.profile }
    suspend fun current(): AppSettings
    suspend fun setOnboardingComplete(done: Boolean)
    suspend fun updateProfile(profile: RunnerProfile)         // writes weight/age/sex/height only
    suspend fun setLearnedStrides(runStrideM: Double?, walkStrideM: Double?)
    suspend fun setEngineMode(mode: LocationEngineMode)
    suspend fun setDevModeUnlocked(unlocked: Boolean)
    suspend fun setDevDualEngine(enabled: Boolean)
    suspend fun setBackupFolderUri(uri: String?)
    suspend fun setBackupIntervalHours(hours: Int)
    suspend fun setLastBackupAt(millis: Long)
    suspend fun setBatteryPromptDismissed(dismissed: Boolean)
}
```

Tracks needing private, non-backed-up state (e.g. A's recovery attempt counter) may use their own
`SharedPreferences`/DataStore file named `<track>_<purpose>` (e.g. `recording_state`). Nothing in a
private store may be needed by another track.

In use (Run 2): A keeps a private SharedPreferences file `recording_state` (keys `auto_resume_run_id`,
`auto_resume_attempts`) that caps automatic crash resumes at 3 per run.

### 2.6 DI (`di/`, FOUNDATION) — manual, like food-tracker

```kotlin
class CoreDeps(
    val app: Application,
    val clock: Clock,
    val dispatchers: AppDispatchers,
    val appScope: CoroutineScope,          // SupervisorJob + dispatchers.default
    val database: RunningDatabase,
    val runRepository: RunRepository,
    val settings: SettingsRepository,
)

class AppContainer(app: RunningApp) {
    val core: CoreDeps
    val analysis: AnalysisModule = AnalysisModule(core.clock)                   // B
    val map: MapModule = MapModule(core)                                        // C
    val recording: RecordingModule = RecordingModule(core, analysis, map)       // A
    val setup: SetupModule = SetupModule(core)                                  // F
    // convenience pass-throughs used by screens:
    val runRepository get() = core.runRepository
    val settings get() = core.settings
}
```

`RunningApp : Application` holds `lateinit var container: AppContainer` (built in `onCreate`), calls
`NotificationChannels.ensure(this)`, `container.map.initialize()`. `MainActivity` calls
`container.setup.backupScheduler.applyFromSettings()` and
`container.recording.controller.checkForInterruptedRun()` once per UI launch (food-tracker's
`initializeForUi` pattern).

```kotlin
// di/AppViewModel.kt
val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }
@Composable inline fun <reified VM : ViewModel> appViewModel(
    key: String? = null, crossinline create: (AppContainer) -> VM,
): VM   // viewModel(key = key, factory = viewModelFactory { initializer { create(container) } })
```

Every screen obtains its ViewModel with `appViewModel { c -> XViewModel(c.runRepository, …) }`.
ViewModels expose one `StateFlow<XUiState>`; composables use `collectAsStateWithLifecycle()`.

### 2.7 Navigation + bottom-tab shell (`ui/navigation/`, FOUNDATION)

Type-safe Navigation Compose with `@Serializable` routes:

```kotlin
@Serializable object OnboardingRoute                              // F
@Serializable object RecordRoute                                  // A   (tab 1, start destination)
@Serializable object ActivitiesRoute                              // D   (tab 2)
@Serializable object ProgressRoute                                // E   (tab 3)
@Serializable object SettingsRoute                                // F   (tab 4)
@Serializable data class RunDetailRoute(val runId: String)        // D
@Serializable object PersonalBestsRoute                           // E
@Serializable data class DevEngineCompareRoute(val runId: String) // A
@Serializable object ProfileEditRoute                             // F
@Serializable object BackupRoute                                  // F
```

`AppNavHost` (F0) wires each route to the CONTRACT entry composable (§3.8). Start destination:
`OnboardingRoute` when `!onboardingComplete`, else `RecordRoute`. Bottom bar (Record · Activities ·
Progress · Settings, icons `DirectionsRun`, `FormatListBulleted`, `BarChart`, `Settings`) is shown on
the four tab routes only, and hidden on `RecordRoute` while `RecordingState` is `Recording` or
`Saving` (full-screen recording). The Record tab icon shows an accent dot while a run is active.

`DeepLinks.kt`:
```kotlin
object DeepLinks {
    const val ACTION_OPEN_RECORDING = "com.example.running.action.OPEN_RECORDING"   // notification tap → Record tab
    const val EXTRA_SEED_DEMO = "seed_demo"                                          // debug only, §6.5
    fun openRecordingIntent(context: Context): Intent
}
```
`MainActivity` is `launchMode="singleTop"`; handles the action in `onCreate`/`onNewIntent`.

### 2.8 Theme (`ui/theme/`, FOUNDATION) — always dark, OLED

No light scheme, no dynamic colour. Bar icons pinned light (`SystemBarStyle.dark(TRANSPARENT)`).
`MaterialExpressiveTheme` (material3 1.5.0) with `MotionScheme.expressive()`.

Pure black where content sits directly on the canvas (screen backgrounds, Record screen, top bars,
map letterboxing). Slightly raised near-black where a pure-black surface would make a shape vanish
(cards, sheets, dialogs, bottom bar, chips, text fields). Charts sit on black; their plot area is not
boxed (docs/ui-house-rules.md §1).

```kotlin
// Color.kt — tokens
val Black          = Color(0xFF000000)   // background, surface, surfaceContainerLowest
val Raised1        = Color(0xFF0F0F11)   // surfaceContainerLow   — list cards
val Raised2        = Color(0xFF16161A)   // surfaceContainer      — bottom bar, chips
val Raised3        = Color(0xFF1E1E23)   // surfaceContainerHigh  — sheets, dialogs, menus
val Raised4        = Color(0xFF27272D)   // surfaceContainerHighest — pressed/selected fills
val Hairline       = Color(0xFF26262B)   // outlineVariant (dividers)
val Outline        = Color(0xFF3C3C44)   // outline (chip borders)
val TextPrimary    = Color(0xFFF3F3F5)   // onSurface / onBackground
val TextSecondary  = Color(0xFFA3A3AD)   // onSurfaceVariant
val TextTertiary   = Color(0xFF6E6E78)
val Accent         = Color(0xFFFF6A2B)   // primary — record button, selected tab, PB, links
val OnAccent       = Color(0xFF1A0800)   // onPrimary (dark text on orange: 7:1)
val AccentMuted    = Color(0xFF3A1A0C)   // primaryContainer (filled area under charts)
val Walk           = Color(0xFF4CB8FF)   // walk segments / walk filter
val Stop           = Color(0xFF6E6E78)   // stop segments
val Success        = Color(0xFF3DDC97)
val Warning        = Color(0xFFFFC24B)   // weak GPS
val Danger         = Color(0xFFFF5A5F)   // error, stop/finish, delete
val StepFilled     = Color(0xFF9A9AA6)   // dashed step-filled route parts
val PaceScale = listOf(Color(0xFF3DDC97), Color(0xFFB8E04A), Color(0xFFFFC24B), Color(0xFFFF6A2B), Color(0xFFFF3B5C)) // fast → slow
```

```kotlin
// Tokens.kt
object Spacing { val xs = 4.dp; val s = 8.dp; val m = 12.dp; val l = 16.dp; val xl = 20.dp; val xxl = 24.dp; val xxxl = 32.dp; val gutter = 16.dp }
object Radius  { val sm = 10.dp; val md = 16.dp; val lg = 24.dp; val sheet = 28.dp; val pill = 999.dp }
object Sizes   { val touchTarget = 48.dp; val button = 52.dp; val recordButton = 88.dp; val controlButton = 64.dp; val chartHeight = 180.dp }
object Elevation { val none = 0.dp }
```

```kotlin
// Type.kt — system sans (no bundled fonts); all numeric styles use fontFeatureSettings = "tnum"
object AppText {
    val hero      // 72.sp Bold, -1.5.sp tracking, tnum  — Record distance
    val numberXL  // 44.sp SemiBold tnum                  — detail headline stats
    val numberL   // 28.sp SemiBold tnum                  — stat tiles
    val numberM   // 20.sp Medium tnum                    — dense grids, splits
    val titleL    // 24.sp SemiBold
    val titleM    // 18.sp SemiBold
    val body      // 16.sp
    val bodyS     // 14.sp
    val label     // 13.sp Medium
    val eyebrow   // 11.sp Medium UPPERCASE, 1.2.sp tracking — stat labels
}
```

Design rules (also written to `docs/ui-house-rules.md` by FOUNDATION): the number is the loudest
thing on screen; label above value in `eyebrow`, unit in `bodyS`/`TextSecondary`; accent means
"action or achievement", never decoration; zero elevation; spacing only from `Spacing`; control
sizes are minimums (`heightIn(min = …)`); no emoji; 16 dp gutter; everything must survive font
scale 2.0. Do not copy food-tracker's look (mint accent, hairline-boxed sections).

### 2.9 Shared UI components (`ui/components/`, FOUNDATION)

```kotlin
@Composable fun StatTile(label: String, value: String, modifier: Modifier = Modifier, unit: String? = null,
    size: StatSize = StatSize.L, valueColor: Color = Color.Unspecified, alignment: Alignment.Horizontal = Alignment.Start)
enum class StatSize { Hero, XL, L, M }
@Composable fun StatRow(stats: List<StatItem>, modifier: Modifier = Modifier, size: StatSize = StatSize.L) // equal-width columns, wraps at large font scale
data class StatItem(val label: String, val value: String, val unit: String? = null)

@Composable fun SectionHeader(title: String, modifier: Modifier = Modifier, trailing: String? = null, onTrailingClick: (() -> Unit)? = null)

@Composable fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null)
@Composable fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null)
@Composable fun DestructiveButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier)
@Composable fun CircleIconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit,
    modifier: Modifier = Modifier, size: Dp = Sizes.controlButton, containerColor: Color = Raised3, contentColor: Color = TextPrimary, enabled: Boolean = true)

@Composable fun <T> ChoiceChips(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier)
@Composable fun ActivityFilterChips(selected: ActivityFilter, onSelect: (ActivityFilter) -> Unit, modifier: Modifier = Modifier) // "All" "Run" "Walk"
@Composable fun EngineChip(engine: LocationEngine?, fallback: Boolean, modifier: Modifier = Modifier, quality: GpsQuality? = null) // "Google" / "Built-in" (+ "fallback"), dot coloured by quality

@Composable fun ConfirmDialog(title: String, text: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit,
    destructive: Boolean = false, dismissLabel: String = stringResource(R.string.common_cancel))
@Composable fun TextInputDialog(title: String, initial: String, confirmLabel: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit)
@Composable fun AppTopBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {})
@Composable fun EmptyState(title: String, body: String, modifier: Modifier = Modifier, actionLabel: String? = null, onAction: (() -> Unit)? = null)
@Composable fun ActivityTypeIcon(type: ActivityType, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) // DirectionsRun / DirectionsWalk
fun ActivityType.displayName(): String   // "Run" / "Walk"
fun LocationEngineMode.displayName(): String  // "Auto" / "Google" / "Built-in"
fun LocationEngine.displayName(): String      // "Google" / "Built-in"
// Added in the Run 3 fix pass (one implementation each; screens must not rebuild them):
@Composable fun AppCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(Spacing.l), verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit)                 // Raised1 list card
@Composable fun AppDivider(modifier: Modifier = Modifier)         // Hairline
@Composable fun ScreenTitle(title: String, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) // tab roots
@Composable fun PbBadge(text: String, modifier: Modifier = Modifier) // outlined accent pill
@Composable fun StatusDot(color: Color, modifier: Modifier = Modifier) // 8 dp
// ui/theme/SemanticColors.kt
fun GpsQuality?.color(): Color     // GOOD Success, FAIR/POOR Warning, NONE TextTertiary
fun SegmentKind?.color(): Color    // RUN/null Accent, WALK Walk, STOP Stop
```

Charts (`ui/components/charts/`, Vico, shared by D and E — D/E must not build their own Vico setup):
```kotlin
data class ChartPoint(val x: Double, val y: Double)
data class BarDatum(val label: String, val value: Double)
@Composable fun AppLineChart(
    points: List<ChartPoint>, modifier: Modifier = Modifier,
    yFormatter: (Double) -> String, xFormatter: (Double) -> String,
    filled: Boolean = true, invertY: Boolean = false,           // invertY for pace (faster = higher)
    color: Color = Accent, height: Dp = Sizes.chartHeight,
    selectedX: Double? = null, onSelectX: ((Double?) -> Unit)? = null,
    comparison: List<ChartPoint>? = null,                        // secondary series (TextTertiary)
)
@Composable fun AppBarChart(
    bars: List<BarDatum>, modifier: Modifier = Modifier, yFormatter: (Double) -> String,
    color: Color = Accent, height: Dp = Sizes.chartHeight,
    selectedIndex: Int? = null, onSelect: ((Int) -> Unit)? = null,
)
```

### 2.10 util/ (FOUNDATION)

```kotlin
// Clock.kt
interface Clock {
    fun nowMillis(): Long                 // wall clock, epoch ms
    fun elapsedRealtimeNanos(): Long      // monotonic (android.os.SystemClock in RealClock)
    fun zoneId(): ZoneId
}
object RealClock : Clock

// AppDispatchers.kt
interface AppDispatchers { val main: CoroutineDispatcher; val io: CoroutineDispatcher; val default: CoroutineDispatcher }
object DefaultAppDispatchers : AppDispatchers

// GeoMath.kt
const val EARTH_RADIUS_M = 6_371_008.8
const val METERS_PER_DEGREE_LAT = EARTH_RADIUS_M * PI / 180     // same sphere as haversine
fun cosLatFactor(lat: Double): Double                            // cos(lat), floored at 0.01
fun metersPerDegreeLon(lat: Double): Double
fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double
fun haversineMeters(a: GeoPoint, b: GeoPoint): Double
fun bearingDegrees(a: GeoPoint, b: GeoPoint): Double
fun interpolate(a: GeoPoint, b: GeoPoint, fraction: Double): GeoPoint
fun pathLengthMeters(points: List<GeoPoint>): Double

// Units.kt
const val MAX_DISPLAY_PACE_SEC_PER_KM = 3_599.0    // slower shows "--:--"; live pace clamps here
const val MAX_CHART_PACE_SEC_PER_KM = 1_800.0      // slower is left out of pace charts
const val STANDING_SPEED_MPS = 0.3
fun paceSecPerKm(speedMps: Double): Double?        // null if speed <= 0.3 m/s (standing)
fun speedMps(paceSecPerKm: Double): Double
fun paceSecPerKm(distanceM: Double, durationMillis: Long): Double?   // null if distance < 10 m
const val METERS_PER_KM = 1_000.0

// Format.kt — the ONLY place user-visible numbers are formatted (en, km only)
fun formatDistanceKm(meters: Double, decimals: Int = 2): String      // "5.02"
fun formatDistance(meters: Double, decimals: Int = 2): String        // "5.02 km"
fun formatPace(secPerKm: Double?): String                            // "5:32", "--:--" for null/≥ 60:00
fun formatPaceWithUnit(secPerKm: Double?): String                    // "5:32 /km"
fun formatDuration(millis: Long): String                             // "32:12", "1:02:03"
fun formatDurationShort(millis: Long): String                        // "2h 19m", "45m", "38s"
fun formatElevation(meters: Double?): String                         // "106 m", "–"
fun formatCalories(kcal: Double?): String                            // "412", "–"
fun formatCadence(spm: Double?): String                              // "172"
fun formatAccuracy(meters: Float?): String                           // "±5 m", "–"
fun formatMeters(meters: Double?): String                            // "4.2 m", "312 m", "–"
fun formatSecondsPrecise(millis: Double?): String                    // "1.02 s", "–"
fun formatPercent(fraction: Double): String                          // "42 %"
fun formatWeightKg(kg: Double): String                               // "72", "72.5"
fun formatFileSize(bytes: Long): String                              // "12 KB", "1.3 MB"
fun formatRunDateTime(millis: Long, zoneId: String): String          // "Tue, 24 Nov 2026 · 7:02 AM"
fun formatRunDate(millis: Long, zoneId: String): String              // "24 Nov 2026"
fun formatRelativeDay(millis: Long, zoneId: String, clock: Clock): String  // "Today", "Yesterday", "Mon", "12 Nov"
fun formatWeekLabel(startMillis: Long, zone: ZoneId): String         // "24 Nov"
fun formatMonthLabel(startMillis: Long, zone: ZoneId, withYear: Boolean = false): String  // "Nov", "Nov 2026"

// Stats.kt
fun percentile(sorted: List<Double>, p: Double): Double?            // linear interpolation, null if empty

// DateTimeUtil.kt  (weeks start Monday, ISO)
fun startOfDay(millis: Long, zone: ZoneId): Long
fun startOfWeek(millis: Long, zone: ZoneId): Long
fun startOfMonth(millis: Long, zone: ZoneId): Long
fun epochDay(millis: Long, zone: ZoneId): Long
fun weekRange(millis: Long, zone: ZoneId): LongRange    // [start, nextStart)
fun monthRange(millis: Long, zone: ZoneId): LongRange
fun isoWeekIndex(epochDay: Long): Long                  // consecutive integer per ISO week
fun dayOfWeekIndex(millis: Long, zone: ZoneId): Int     // 0 = Monday … 6 = Sunday
// internal: LocalDate.startMillis(zone), localDate(millis, zone), LocalDate.isoWeekStart() (used by Aggregation)

// Ids.kt
fun newRunId(): String   // UUID.randomUUID().toString()

// core/stats/Aggregation.kt
data class PeriodTotals(val startMillis: Long, val endMillis: Long, val count: Int, val distanceM: Double,
    val movingMillis: Long, val elapsedMillis: Long, val elevationGainM: Double, val caloriesKcal: Double)
fun List<Run>.totals(startMillis: Long, endMillis: Long): PeriodTotals
fun bucketByDay(runs: List<Run>, weekStartMillis: Long, zone: ZoneId): List<PeriodTotals>   // 7 entries Mon..Sun
fun bucketByWeek(runs: List<Run>, weeks: Int, now: Long, zone: ZoneId): List<PeriodTotals> // oldest → current
fun bucketByMonth(runs: List<Run>, months: Int, now: Long, zone: ZoneId): List<PeriodTotals>
```

`core/system/Permissions.kt` (F0, real implementation):
```kotlin
object Permissions {
    val LOCATION = arrayOf(ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION)
    const val NOTIFICATIONS = POST_NOTIFICATIONS
    const val ACTIVITY_RECOGNITION = android.Manifest.permission.ACTIVITY_RECOGNITION
    const val BACKGROUND_LOCATION = ACCESS_BACKGROUND_LOCATION   // request alone, after fine location (§8.1)
    fun hasFineLocation(ctx: Context): Boolean
    fun hasBackgroundLocation(ctx: Context): Boolean             // "Allow all the time"
    fun canRequestBackgroundLocation(ctx: Context): Boolean      // fine granted && background not yet granted
    fun hasNotifications(ctx: Context): Boolean                  // always true below API 33
    fun hasActivityRecognition(ctx: Context): Boolean
    fun appSettingsIntent(ctx: Context): Intent
    fun locationSettingsIntent(): Intent
    fun isLocationEnabled(ctx: Context): Boolean
}
// BatteryOptimization.kt
object BatteryOptimization {
    fun isIgnoring(ctx: Context): Boolean
    fun requestExemptionIntent(ctx: Context): Intent   // ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
}
```

`core/notifications/NotificationChannels.kt`:
```kotlin
object NotificationChannels {
    const val RECORDING = "recording"          // IMPORTANCE_LOW, ongoing live stats (A)
    const val RECORDING_ALERTS = "recording_alerts" // IMPORTANCE_DEFAULT: GPS lost, engine fallback, recovery (A)
    const val BACKUP = "backup"                // IMPORTANCE_LOW (F)
    const val ID_RECORDING = 1001; const val ID_RECORDING_ALERT = 1002; const val ID_BACKUP = 2001
    fun ensure(context: Context)
}
```

---

## 3. CROSS-TRACK INTERFACES

FOUNDATION writes every interface/model below for real, and writes a **stub implementation** in the
owning track's package that compiles and, where noted, behaves minimally so consumers can develop in
parallel. "Stub" behaviour is replaced by the implementing track. Since Run 2 every stub is gone
(§10.3); the stub paragraphs below are kept, marked historical, only to explain Run 1.

### 3.1 Recording — implemented by A

```kotlin
// core/model/Recording.kt (F0)
data class GpsStatus(
    val engine: LocationEngine?, val accuracyM: Float?, val quality: GpsQuality,
    val startAllowed: Boolean,        // good fix, or ≥ ~10 s of waiting with any fix
    val weakSignalWarning: Boolean,   // start allowed only by the timeout
    val fallbackActive: Boolean,      // AUTO switched from PLAY to PLATFORM (or back)
    val waitingMillis: Long,
)
data class LiveStats(
    val elapsedMillis: Long, val movingMillis: Long, val distanceM: Double,
    val currentPaceSecPerKm: Double?, val avgPaceSecPerKm: Double?,
    val caloriesKcal: Double?, val steps: Int?, val cadenceSpm: Double?, val elevationGainM: Double?,
    val currentSplitIndex: Int, val lastSplitPaceSecPerKm: Double?,
    val pausedMillis: Long = 0,       // manual pauses so far, incl. an open one (Run 3)
) {
    val activeMillis: Long            // elapsed − pausedMillis: the live clock (keeps running at stops)
    companion object { val ZERO: LiveStats }
}
sealed interface RecordingState {
    data object Idle : RecordingState
    data class WarmingUp(val gps: GpsStatus) : RecordingState
    data class Recording(val runId: String, val startedAtMillis: Long, val paused: Boolean,
                         val stats: LiveStats, val gps: GpsStatus) : RecordingState
    data class Saving(val runId: String) : RecordingState
    data class NeedsRecoveryDecision(val runId: String, val startedAtMillis: Long, val distanceM: Double) : RecordingState
}
sealed interface RecordingEvent {
    data class RunSaved(val runId: String) : RecordingEvent
    data class EngineSwitched(val from: LocationEngine, val to: LocationEngine) : RecordingEvent
    data class Failure(val message: String) : RecordingEvent
}

// core/contracts/RecordingController.kt (F0)
interface RecordingController {
    val state: StateFlow<RecordingState>
    val events: SharedFlow<RecordingEvent>
    val liveRoute: StateFlow<MapRoute?>          // accepted points so far (for RouteMap Live)
    fun beginWarmUp()                            // Record screen visible & idle → start location for GPS gating
    fun endWarmUp()
    fun start()                                  // no-op unless state is WarmingUp with startAllowed
    fun pause(); fun resume(); fun stop()        // stop → Saving → RunSaved(runId) → Idle
    suspend fun checkForInterruptedRun()         // on UI launch: auto-resume or NeedsRecoveryDecision (§8.1)
    fun resumeInterrupted(runId: String)
    fun finishInterrupted(runId: String)
}
```
*Historical (Run 1 only, stub deleted in Run 2, §10.3):* stub `recording/StubRecordingController.kt`
had state `Idle`, all calls no-op, `liveRoute` null.

```kotlin
// recording/RecordingModule.kt  CONTRACT(A)
class RecordingModule(core: CoreDeps, analysis: AnalysisModule, map: MapModule) {
    val controller: RecordingController
}
```
Consumers: A (Record screen, service, notification), F0 (`AppNavHost` bottom-bar hiding, Record
tab dot, `MainActivity` recovery + deep link), F (Settings disables engine-mode change while
`state !is Idle && state !is WarmingUp`).

Stop pipeline (A's `RunFinalizer`, run on `appScope`): close open pause → `loadAnalysisInput` →
`analysis.runAnalyzer.analyze` → `saveAnalysis` → `settings.setLearnedStrides` (if non-null) →
`map.offline.downloadRunArea(runId, bounds)` (fire-and-forget) → emit `RunSaved`. The Record screen
navigates to `RunDetailRoute(runId)` on `RunSaved` (auto-save, no Save/Discard).

Crash recovery (A, decided §8.1): when the process dies with an `ACTIVE` run, A restarts the location
FGS and resumes recording automatically **if `Permissions.hasBackgroundLocation()`** — both from the
service restart (`START_STICKY`) and from `checkForInterruptedRun()`. Without background location (or
if the FGS start is refused, e.g. `ForegroundServiceStartNotAllowedException`), A does not retry from
the background and instead surfaces `NeedsRecoveryDecision` (resume / finish) on the next app open.
Automatic resumes are capped at 3 per run (§2.5) and only happen when the last persisted data is at
most 10 min old (`ControllerConfig.autoResumeMaxGapMillis`); an older interruption (phone died or
rebooted mid-run, app opened later) always gets the prompt. A resume the user confirms after a gap of 60 s or
more stores the gap as a pause; finishing an interrupted run uses the last recorded data time as
`endedAtMillis`, not the current time.

### 3.2 Location + sensor sources — implemented by A

```kotlin
// core/contracts/LocationSource.kt (F0)
interface LocationSource {
    val engine: LocationEngine
    fun isAvailable(): Boolean                           // PLAY: GMS present; PLATFORM: GPS provider exists
    /** Cold flow; collection starts high-accuracy updates at [intervalMillis], cancellation stops them. */
    fun fixes(intervalMillis: Long = 1_000): Flow<LocationFix>
}
interface StepSource {
    val hasStepDetector: Boolean
    val hasStepCounter: Boolean
    fun steps(): Flow<StepEvent>
}
interface PressureSource {
    val isAvailable: Boolean
    fun pressure(): Flow<PressureSample>                  // ~1 Hz is enough
}
```
A implements `location/PlayLocationSource` (FusedLocationProviderClient, `Priority.PRIORITY_HIGH_ACCURACY`,
1 s, `setMinUpdateIntervalMillis(1000)`, `setWaitForAccurateLocation(false)`), `location/PlatformLocationSource`
(LocationManager GPS_PROVIDER, 1 s, 0 m), `sensors/AndroidStepSource`, `sensors/AndroidPressureSource`,
and `location/LocationEngineManager` (Geofence `GeofenceEngine` facade pattern: mode AUTO/GOOGLE/BUILT_IN;
AUTO prefers PLAY and falls back to PLATFORM mid-run when PLAY is unavailable/fails/goes silent for N
seconds while PLATFORM has fixes; GOOGLE/BUILT_IN are fixed; dev dual mode collects both and marks
`isPrimary` on whichever drives the run). Tests use `FakeLocationSource` (§6). These interfaces are
consumed only by A, but live in `core/` so the test fakes (F0) can implement them.

### 3.3 Analysis — implemented by B (pure Kotlin)

```kotlin
// core/model/Analysis.kt (F0)
data class AnalysisInput(
    val runId: String, val startedAtMillis: Long, val endedAtMillis: Long, val zoneId: String,
    val rawPoints: List<TrackPoint>,          // kind RAW, isPrimary = true, time-ordered
    val pauses: List<PauseInterval>,          // closed intervals
    val sensorSamples: List<SensorSample>,    // time-ordered
    val profile: RunnerProfile,
    val manualType: ActivityType?,            // non-null if the user already chose a type
)
data class RunAnalysis(
    val stats: RunStats,
    val suggestedType: ActivityType,
    val suggestedName: String,
    val filteredPoints: List<TrackPoint>,     // kind FILTERED with distanceM/elevationM/paceSecPerKm/segmentKind/stepFilled set
    val splits: List<Split>,
    val segments: List<Segment>,              // RUN/WALK/STOP covering the moving timeline
    val bestEfforts: List<BestEffort>,        // only distances actually covered
    val learnedRunStrideM: Double?, val learnedWalkStrideM: Double?,
    val analysisVersion: Int,
)
data class SeriesPoint(val distanceKm: Double, val value: Double)
data class RunSeries(
    val pace: List<SeriesPoint>,             // sec/km, smoothed, stops removed
    val elevation: List<SeriesPoint>,        // m
    val cadence: List<SeriesPoint>,          // spm (empty if no step data)
)

// core/contracts/Analysis.kt (F0)
interface RunAnalyzer { fun analyze(input: AnalysisInput): RunAnalysis }
interface LiveMetrics {
    fun onFix(fix: LocationFix, paused: Boolean)
    fun onSteps(event: StepEvent, paused: Boolean)
    fun onPressure(sample: PressureSample)
    fun onPauseChanged(paused: Boolean, atMillis: Long)
    fun snapshot(nowMillis: Long): LiveStats
    fun acceptedRoute(): MapRoute            // live polyline (pace colouring optional)
}
interface LiveMetricsFactory { fun create(profile: RunnerProfile, startedAtMillis: Long): LiveMetrics }
interface RunNamer { fun name(startedAtMillis: Long, zoneId: String, type: ActivityType): String }  // "Morning Run", "Evening Walk"…
interface RunSeriesBuilder { fun build(filteredPoints: List<TrackPoint>, sensorSamples: List<SensorSample> = emptyList()): RunSeries }  // samples only feed cadence (Run 3)

// analysis/AnalysisModule.kt  CONTRACT(B)
class AnalysisModule(clock: Clock) {
    val runAnalyzer: RunAnalyzer
    val liveMetricsFactory: LiveMetricsFactory
    val runNamer: RunNamer
    val runSeriesBuilder: RunSeriesBuilder
}
```
*Historical (Run 1 only, `analysis/StubAnalysis.kt` deleted in Run 2, §10.3):* stub analyzer copies raw points to FILTERED with cumulative haversine
distance, stats from distance/elapsed/pauses (moving = elapsed − paused), 1 km splits by linear
interpolation, one RUN segment, no best efforts, `suggestedName` from the namer; namer uses hour
buckets (5–11 Morning, 11–14 Lunch, 14–17 Afternoon, 17–21 Evening, else Night) + "Run"/"Walk";
live metrics = haversine sum; series builder maps `paceSecPerKm`/`elevationM` directly.

Consumers: A (live metrics, analyze on stop, namer not needed), D (`runSeriesBuilder` for charts;
`runNamer` indirectly via `RunRepository.setType`), F0 repository (`runNamer`), dev compare (A).
B owns everything else inside `analysis/` (Kalman, Doppler fusion, stationary gating, step fill when
accuracy > ~20 m, stride learning seeded from height, walk/run labelling, stop detection, splits,
best efforts on moving time, calories, elevation, naming) as internal API.

### 3.4 Map — implemented by C

```kotlin
// core/model/MapModels.kt (F0)
data class RoutePoint(val lat: Double, val lon: Double, val distanceM: Double,
    val paceSecPerKm: Double?, val stepFilled: Boolean, val segmentKind: SegmentKind?)
enum class RouteColoring { PACE, SOLID, SEGMENT_KIND }
data class MapRoute(val points: List<RoutePoint>, val coloring: RouteColoring = RouteColoring.PACE,
    val tintArgb: Int? = null, val label: String? = null) {
    val bounds: GeoBounds?
    companion object { fun fromTrackPoints(points: List<TrackPoint>, coloring: RouteColoring = RouteColoring.PACE): MapRoute }
}
enum class RouteMapMode { STATIC, INTERACTIVE, LIVE }   // STATIC: fit bounds, no gestures; LIVE: follow user, north-up
enum class OfflineAreaStatus { NONE, DOWNLOADING, READY, FAILED }

// core/contracts/MapOfflineManager.kt (F0)
interface MapOfflineManager {
    suspend fun downloadRunArea(runId: String, bounds: GeoBounds): Boolean
    suspend fun deleteRunArea(runId: String)
    suspend fun status(runId: String): OfflineAreaStatus
}

// map/MapModule.kt  CONTRACT(C)
class MapModule(core: CoreDeps) {
    val offline: MapOfflineManager
    fun initialize()                     // main thread, from RunningApp.onCreate: MapLibre.getInstance + ambient cache size
}

// map/RouteMap.kt  CONTRACT(C)
@Composable fun RouteMap(
    route: MapRoute?,
    modifier: Modifier = Modifier,
    mode: RouteMapMode = RouteMapMode.STATIC,
    animateDrawIn: Boolean = false,
    comparison: List<MapRoute> = emptyList(),               // dev compare overlays (SOLID + tint)
    highlightDistanceM: ClosedFloatingPointRange<Double>? = null,  // e.g. tapped split
    userLocation: GeoPoint? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onClick: (() -> Unit)? = null,
)
// map/RouteThumbnail.kt  CONTRACT(C) — cheap, list-safe (no live MapView per row)
@Composable fun RouteThumbnail(route: MapRoute, modifier: Modifier = Modifier, cacheKey: String? = null)
```
*Historical (Run 1 only, `map/StubRouteCanvas.kt` deleted in Run 2, §10.3):* `RouteMap` was a `Raised1` box with the route drawn as a Canvas polyline (projected
equirectangular) and start/end dots; `RouteThumbnail` same; `MapModule.initialize()` no-op; offline
manager returns `false`/`NONE`.

Rendering rules for C: pace colouring from `PaceScale` (normalised to the run's own 5th–95th
percentile pace), step-filled parts dashed in `StepFilled`, start marker `Success`, end marker
`Danger`/chequered, draw-in animation over ~1.2 s. Tile source: bundled `assets/map/style_dark.json`
("slightly lighter dark", OpenFreeMap vector tiles `https://tiles.openfreemap.org/planet`); when
`BuildConfig.MAPTILER_KEY` is non-empty, C switches the source URL to MapTiler with the key (same
style layers). MapLibre ambient cache + per-run offline region whose metadata stores the runId.

Consumers: A (Record screen `LIVE`, dev compare with `comparison`, finalizer `downloadRunArea`),
D (detail `INTERACTIVE`/`STATIC` with `animateDrawIn = true`; list cards `RouteThumbnail`; delete →
`deleteRunArea`), E (optional thumbnails on PB rows).

### 3.5 Setup — implemented by F

```kotlin
// core/contracts/LocationAccuracyChecker.kt (F0)
enum class AccuracyState { ON, OFF, UNKNOWN }
interface LocationAccuracyChecker {
    suspend fun check(): AccuracyState                       // Play SettingsClient, HIGH_ACCURACY request, 5 s timeout
    suspend fun resolution(): PendingIntent?                 // one-tap "turn on Google Location Accuracy"
}

// setup/SetupModule.kt  CONTRACT(F)
class SetupModule(core: CoreDeps) {
    val accuracyChecker: LocationAccuracyChecker
    val backupManager: BackupManager
    val backupScheduler: BackupScheduler
}
// backup/BackupManager.kt  CONTRACT(F) — public surface:
class BackupManager(/* F chooses */) {
    suspend fun exportTo(target: Uri): Result<Int>            // runs written
    suspend fun importFrom(source: Uri): Result<ImportResult>
    suspend fun saveScheduledBackup(): Result<String>          // file name
    suspend fun listBackups(): List<BackupFile>
}
data class ImportResult(val imported: Int, val skippedExisting: Int, val failed: Int)
data class BackupFile(val name: String, val uri: Uri, val sizeBytes: Long, val modifiedMillis: Long)
// backup/BackupScheduler.kt  CONTRACT(F)
class BackupScheduler(/* F chooses */) { fun apply(intervalHours: Int); suspend fun applyFromSettings() }
```
*Historical (Run 1 only, replaced by F in Run 2):* checker returned `UNKNOWN`/`null`; manager failures; scheduler no-op.
Consumers: F (onboarding, settings, worker), A (may read `accuracyChecker` to warn on the Record
screen and to inform AUTO fallback), F0 (`MainActivity` calls `applyFromSettings()`).

### 3.6 Backup payload schema — implemented by F

`backup/BackupPayload.kt` (F). kotlinx.serialization, `Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }`.
Every field added later must have a default (food-tracker rule) so old files stay readable.

```kotlin
@Serializable data class BackupPayload(
    val schemaVersion: Int = 1,
    val createdAtMillis: Long,
    val appVersion: String,
    val profile: ProfileSnapshot,
    val settings: SettingsSnapshot,
    val runs: List<RunSnapshot>,
)
@Serializable data class ProfileSnapshot(val weightKg: Double, val ageYears: Int, val sex: String,
    val heightCm: Int? = null, val learnedRunStrideM: Double? = null, val learnedWalkStrideM: Double? = null)
@Serializable data class SettingsSnapshot(val engineMode: String = "AUTO")   // backup folder/schedule are device-local: not restored
@Serializable data class RunSnapshot(
    val id: String, val name: String, val nameIsCustom: Boolean, val type: String, val typeIsManual: Boolean,
    val startedAtMillis: Long, val endedAtMillis: Long?, val zoneId: String,
    val engineMode: String, val startEngine: String, val engineFallback: Boolean, val devDualEngine: Boolean,
    val stats: StatsSnapshot, val analysisVersion: Int,
    val createdAtMillis: Long, val updatedAtMillis: Long,
    val points: List<PointSnapshot>, val pauses: List<PauseSnapshot>, val splits: List<SplitSnapshot>,
    val segments: List<SegmentSnapshot>, val bestEfforts: List<BestEffortSnapshot>, val sensorSamples: List<SensorSnapshot>,
)
// StatsSnapshot/PointSnapshot/… mirror the domain types field-for-field; enums as String names;
// PointSnapshot uses short JSON names via @SerialName ("k","e","p","t","n","la","lo","alt","msl","ha","va","s","sa","b","pz","sf","d","el","pc","sk") to keep files small.
```
Rules: only `FINISHED` runs are exported. Import maps each `RunSnapshot` → `RunGraph` →
`RunRepository.importRunGraph`; existing ids are skipped (idempotent). Profile is restored only if
`onboardingComplete` is false or the user confirms. File name `running-backup-yyyyMMdd-HHmmss.json`;
default location `Download/Running/` via MediaStore, or the SAF folder from settings; keep the newest
5 (food-tracker `BackupManager` pattern). Worker: `backup/BackupWorker` (CoroutineWorker), unique
periodic work `recurring-backup`, constraints battery-not-low + storage-not-low.

### 3.7 Summary matrix

| Contract | Defined (F0) | Implemented | Consumed by |
|---|---|---|---|
| `RecordingController`, `RecordingState`, `LiveStats` | core/contracts, core/model | A | A, F0 nav/MainActivity, F |
| `LocationSource`, `StepSource`, `PressureSource` | core/contracts | A | A, F0 test fakes |
| `RunAnalyzer`, `LiveMetrics*`, `RunNamer`, `RunSeriesBuilder` | core/contracts | B | A, D, F0 repo |
| `RouteMap`, `RouteThumbnail`, `MapModule` | CONTRACT files in map/ | C | A, D, E, F0 app |
| `MapOfflineManager` | core/contracts | C | A (download), D (delete) |
| `LocationAccuracyChecker`, `SetupModule`, `BackupManager`, `BackupScheduler` | core/contracts + CONTRACT files | F | F, A, F0 |
| `RunRepository`, `SettingsRepository` | data/ | F0 (real) | all |
| Screen entry composables | CONTRACT files | per §3.8 | F0 AppNavHost |

### 3.8 Screen entry points (CONTRACT, called only by `AppNavHost`)

```kotlin
// ui/record/RecordScreen.kt              CONTRACT(A)
@Composable fun RecordScreen(onRunSaved: (runId: String) -> Unit)
// ui/dev/DevEngineCompareScreen.kt       CONTRACT(A)
@Composable fun DevEngineCompareScreen(runId: String, onBack: () -> Unit)
// ui/activities/ActivitiesScreen.kt      CONTRACT(D)
@Composable fun ActivitiesScreen(onOpenRun: (runId: String) -> Unit)
// ui/rundetail/RunDetailScreen.kt        CONTRACT(D)
@Composable fun RunDetailScreen(runId: String, onBack: () -> Unit, onOpenDevCompare: (runId: String) -> Unit)
// ui/progress/ProgressScreen.kt          CONTRACT(E)
@Composable fun ProgressScreen(onOpenPersonalBests: () -> Unit, onOpenRun: (runId: String) -> Unit)
// ui/progress/PersonalBestsScreen.kt     CONTRACT(E)
@Composable fun PersonalBestsScreen(onBack: () -> Unit, onOpenRun: (runId: String) -> Unit)
// ui/onboarding/OnboardingScreen.kt      CONTRACT(F)
@Composable fun OnboardingScreen(onFinished: () -> Unit)
// ui/settings/SettingsScreen.kt          CONTRACT(F)
@Composable fun SettingsScreen(onOpenProfile: () -> Unit, onOpenBackup: () -> Unit)
// ui/settings/ProfileEditScreen.kt       CONTRACT(F)
@Composable fun ProfileEditScreen(onBack: () -> Unit)
// ui/settings/BackupScreen.kt            CONTRACT(F)
@Composable fun BackupScreen(onBack: () -> Unit)
```
*Historical (Run 1 only):* stub bodies were `EmptyState(title = "<Screen>", body = "Not built yet")`. Insets rule: the nav host's
`Scaffold` applies the bottom-bar padding; screens draw edge-to-edge and handle the status bar
themselves (`WindowInsets.statusBars`).

Feature notes that pin cross-track behaviour:
- **D** shows a "Compare engines" action on Run detail only when `settings.devModeUnlocked && run.devDualEngine`.
- **D** delete: `ConfirmDialog(destructive = true)` → `runRepository.deleteRun` → `map.offline.deleteRunArea` → `onBack()`.
- **D** change type: `runRepository.setType` (repository re-applies auto-name when not custom). No re-analysis.
- **D** "This week" strip on Activities uses `bucketByDay` + `totals` from `core/stats`.
- **E** streaks (consecutive weeks with ≥1 activity; current + longest; day streak) live in `ui/progress/Streaks.kt` (E).
- **E** charts: weekly (last 12 weeks) and monthly (last 12 months) via `bucketByWeek`/`bucketByMonth` + `AppBarChart`/`AppLineChart`; metric switch Distance / Time / Elevation; `ActivityFilterChips`.
- **F** onboarding permission order: fine location (`Permissions.LOCATION`) → **background location as
  its own step** (only once fine is granted; explain "lets a run resume if Android closes the app
  mid-run"; on API 30+ the request opens the system settings page where the user picks "Allow all the
  time"; skippable, never blocks onboarding) → notifications → activity recognition → battery
  exemption. Settings shows the background-location state with a button to grant it later
  (`Permissions.canRequestBackgroundLocation`).
- **F** dev mode: tapping the version row 7 times sets `devModeUnlocked`; then a "Developer" section shows `devDualEngine`.
- **F** Settings engine mode picker: Auto / Google / Built-in with one-line explanations; disabled while recording.

---

## 4. Gradle dependencies (FOUNDATION; tracks may not add any)

Versions checked against Google Maven / Maven Central on 2026-11-24 (latest stable).

### 4.1 `gradle/libs.versions.toml`

```toml
[versions]
agp = "9.5.2"
kotlin = "2.4.21"
ksp = "2.3.14"
coreKtx = "1.19.1"
splashscreen = "1.2.0"
activityCompose = "1.13.0"
lifecycle = "2.11.1"
composeBom = "2026.11.01"          # stable BOM: material3 1.5.0 (Expressive), ui 1.13.0
navigationCompose = "2.10.2"
room = "2.8.5"
datastore = "1.3.0"
work = "2.12.0"
documentfile = "1.1.0"
coroutines = "1.11.0"
serialization = "1.13.0"
playServicesLocation = "21.4.0"
maplibre = "13.7.1"
vico = "3.3.1"
junit = "4.13.2"
truth = "1.4.5"
turbine = "1.2.1"
robolectric = "4.18.1"
androidxTestCore = "1.7.0"
androidxJunit = "1.3.0"
espresso = "3.7.0"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
androidx-core-splashscreen = { group = "androidx.core", name = "core-splashscreen", version.ref = "splashscreen" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycle" }
androidx-lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-lifecycle-service = { group = "androidx.lifecycle", name = "lifecycle-service", version.ref = "lifecycle" }
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-compose-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
androidx-compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
androidx-compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx-compose-ui-test-manifest = { group = "androidx.compose.ui", name = "ui-test-manifest" }
androidx-compose-ui-test-junit4 = { group = "androidx.compose.ui", name = "ui-test-junit4" }
androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3" }
androidx-compose-material-icons-extended = { group = "androidx.compose.material", name = "material-icons-extended" }
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }
androidx-room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
androidx-room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
androidx-room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
androidx-room-testing = { group = "androidx.room", name = "room-testing", version.ref = "room" }
androidx-datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
androidx-work-runtime-ktx = { group = "androidx.work", name = "work-runtime-ktx", version.ref = "work" }
androidx-work-testing = { group = "androidx.work", name = "work-testing", version.ref = "work" }
androidx-documentfile = { group = "androidx.documentfile", name = "documentfile", version.ref = "documentfile" }
kotlinx-coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }
kotlinx-coroutines-play-services = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-play-services", version.ref = "coroutines" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines" }
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "serialization" }
play-services-location = { group = "com.google.android.gms", name = "play-services-location", version.ref = "playServicesLocation" }
maplibre-android = { group = "org.maplibre.gl", name = "android-sdk-opengl", version.ref = "maplibre" }
vico-compose-m3 = { group = "com.patrykandpatrick.vico", name = "compose-m3", version.ref = "vico" }
junit = { group = "junit", name = "junit", version.ref = "junit" }
truth = { group = "com.google.truth", name = "truth", version.ref = "truth" }
turbine = { group = "app.cash.turbine", name = "turbine", version.ref = "turbine" }
robolectric = { group = "org.robolectric", name = "robolectric", version.ref = "robolectric" }
androidx-test-core = { group = "androidx.test", name = "core-ktx", version.ref = "androidxTestCore" }
androidx-junit = { group = "androidx.test.ext", name = "junit", version.ref = "androidxJunit" }
androidx-espresso-core = { group = "androidx.test.espresso", name = "espresso-core", version.ref = "espresso" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

If any of these fails to resolve or is incompatible (e.g. Kotlin 2.4.21 vs AGP 9.5.2 built-in
Kotlin, KSP 2.3.14), FOUNDATION picks the nearest working stable version and records it here.

Chart library decision: **Vico 3 (`compose-m3`)** — one lib for D and E, wrapped by F0's
`AppLineChart`/`AppBarChart`. Map: **MapLibre Android 13.7.1 (OpenGL)**, MapLibre's built-in
`GeoJsonSource`/`LineLayer` for routes (no annotation plugin).

### 4.2 Dependency → main user

| Dependency | Used by |
|---|---|
| play-services-location, kotlinx-coroutines-play-services | A (Play engine), F (accuracy check) |
| lifecycle-service | A (`RecordingService : LifecycleService`) |
| maplibre-android | C only (A/D/E use C's composables) |
| vico-compose-m3 | F0 chart wrappers only |
| work-runtime, documentfile | F |
| room, datastore | F0 |
| splashscreen | F0 |
| robolectric, androidx-test-core, work-testing | JVM tests needing Android (F backup, F0 DAO tests) |

### 4.3 `app/build.gradle.kts` essentials

- plugins: `android.application`, `kotlin.compose`, `kotlin.serialization`, `ksp` (AGP 9 built-in Kotlin; no `kotlin-android` plugin).
- `namespace = applicationId = "com.example.running"`; `compileSdk { version = release(37) }`, `compileSdkMinor = 2` (android-37.2 is installed; drop the minor if it fails); `minSdk = 31`; `targetSdk = 37`; `versionCode = 1`; `versionName = "1.0"`.
- `buildFeatures { compose = true; buildConfig = true }`.
- `buildConfigField("String", "MAPTILER_KEY", "\"$mapTilerKey\"")` read via food-tracker's `creds()` helper from `local.properties` / env, default `""`.
- `signingConfigs.githubPublicRelease` (§7); release: `isMinifyEnabled = true`, `isShrinkResources = true`, `proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")`, `ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }` (MapLibre ships native libs; this matters here).
- `androidResources { localeFilters += listOf("en") }`.
- `packaging.resources.excludes`: `/META-INF/{AL2.0,LGPL2.1}`, `/META-INF/*.version`, `/META-INF/com.android.tools/**`, `/DebugProbesKt.bin`, `/kotlin-tooling-metadata.json`.
- `compileOptions` Java 11; `ksp { arg("room.schemaLocation", "$projectDir/schemas") }`; androidTest assets from `schemas`.
- `testOptions { unitTests { isIncludeAndroidResources = true; isReturnDefaultValues = true } }`.

---

## 5. RULES for tracks

### 5.1 Ownership
- Edit only files you own (§1). FOUNDATION files and other tracks' files are read-only, including
  `AndroidManifest.xml`, `libs.versions.toml`, `app/build.gradle.kts`, `strings.xml`, theme and
  `ui/components`.
- CONTRACT files: fill in, never change the public signature. You may add *optional* trailing
  parameters with defaults only via SCR.
- Inside your package do what you like (sub-packages, `internal` classes, ViewModels).
- Prefer `internal` for anything not part of a contract; the only `public` declarations a track adds
  are the contract implementations and what it lists in its report.

### 5.2 Reuse
- Formatting, geo maths, unit conversion, dates, aggregation: use `core/util` / `core/stats`.
  Never re-implement haversine, pace formatting, week boundaries, etc.
- UI: use `ui/theme` tokens and `ui/components`. No raw colours (use `MaterialTheme.colorScheme` or
  the tokens), no raw spacing outside `Spacing`.
- Time: inject `Clock`; never call `System.currentTimeMillis()`, `SystemClock.*`, `Instant.now()`
  or `LocalDate.now()` outside `RealClock` (A may read `Location.elapsedRealtimeNanos`).
- Threads: inject `AppDispatchers`; no hard-coded `Dispatchers.IO` in classes that have tests.
- Data access only through `RunRepository` / `SettingsRepository`.

### 5.3 SHARED-CHANGE REQUEST (SCR)
When you need something in a FOUNDATION file (helper, component, dependency, manifest entry, route,
DB column, DataStore key, signature change), do **not** edit it. Instead:
1. Write the smallest private stand-in inside your own package, marked `// SCR-<track>-<n>`.
2. Add to your final report:
```
SCR-<track>-<n>
File:       core/util/Format.kt
Change:     add fun formatSpeedKmh(mps: Double): String
Signature:  fun formatSpeedKmh(mps: Double): String  // "12.4 km/h"
Why:        dev compare screen shows engine speed
Stand-in:   ui/dev/DevFormat.kt (private), remove after integration
```
The integrator applies SCRs in Run 3 and removes the stand-ins.

### 5.4 Track-specific constraints
- **B**: `analysis/**` must compile as pure Kotlin — no `android.*`, `androidx.*` or Compose
  imports (Kotlin stdlib, `java.time`, `core/model`, `core/util` pure files only). It has no UI and
  no strings; names like "Morning Run" are English literals (app is en-only).
- **A**: the only owner of `RecordingService`, notifications on `RECORDING*` channels, and all
  location/sensor API calls (except F's accuracy check).
- **C**: the only importer of `org.maplibre.*`.
- **F**: the only owner of WorkManager and SAF code.
- **D/E**: UI + ViewModels only; any non-trivial calculation that both need goes to SCR, not copied.

### 5.5 Strings and resources
- Each track writes UI strings only in its own `res/values/strings_<track>.xml`, every name prefixed
  as in §1.3 (resource names are global; duplicates break the merge).
- Drawables `ic_<prefix>_*`. No new colours/themes in XML.
- Plurals allowed in your own file with the same prefix.

### 5.6 AndroidManifest (pre-declared by FOUNDATION; tracks request changes via SCR)

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.POST_PROMOTED_NOTIFICATIONS" />  <!-- Android 16 Live Updates on lock screen -->
<uses-permission android:name="android.permission.ACTIVITY_RECOGNITION" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />
<uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />  <!-- crash auto-resume only, §8.1 -->

<uses-feature android:name="android.hardware.location.gps" android:required="false" />
<uses-feature android:name="android.hardware.sensor.stepdetector" android:required="false" />
<uses-feature android:name="android.hardware.sensor.stepcounter" android:required="false" />
<uses-feature android:name="android.hardware.sensor.barometer" android:required="false" />

<application android:name=".RunningApp" android:allowBackup="false"
    android:icon=… android:roundIcon=… android:label="@string/app_name"
    android:supportsRtl="true" android:theme="@style/Theme.Running">
  <activity android:name=".MainActivity" android:exported="true" android:launchMode="singleTop"
      android:windowSoftInputMode="adjustResize" android:theme="@style/Theme.Running.Starting">
    <intent-filter> MAIN / LAUNCHER </intent-filter>
    <intent-filter> <action android:name="com.example.running.action.OPEN_RECORDING" /> <category DEFAULT/> </intent-filter>
  </activity>
  <service android:name=".recording.RecordingService"
      android:exported="false"
      android:foregroundServiceType="location" />
  <!-- WorkManager's own components come from its manifest; no initializer override. -->
</application>
```
`RecordingService` stub (A-owned file, created by F0): an empty `LifecycleService` subclass, so the
manifest resolves. Notification actions (pause/resume/stop) use `PendingIntent.getService` to the
same service with actions `com.example.running.recording.PAUSE|RESUME|STOP` (A defines the
constants inside its package) — no broadcast receiver is declared. `ACCESS_BACKGROUND_LOCATION` is
declared and requested by onboarding (F) as a separate step; recording still always starts from the
foreground, and the permission is used only to restart the FGS after a process kill (§8.1).

### 5.7 Definition of done per track
- `./gradlew assembleDebug testDebugUnitTest` green in the worktree (also `lintDebug` without new errors).
- Unit tests for every non-UI class with logic; replay tests use fixtures (§6).
- Manual check on the dedicated emulator AVD (`Running_Test`, pixel_9 profile, android-36.1
  google_apis_playstore arm64 — never the user's `Pixel_9`/`PA_Repro` AVDs) where UI is involved.
- Final report contains: files created/changed; public APIs created (signatures); SCRs; tests added
  and what they cover; known gaps/TODOs; anything the integrator must wire.
- No commits to other branches; commit on your track branch `track/<letter>-<name>` with message
  ending in the attribution line.

---

## 6. Testing seams

### 6.1 Injected seams
| Seam | Real | Fake (F0, `test/…/testing/`) |
|---|---|---|
| `Clock` | `RealClock` | `FakeClock(startMillis, zone = ZoneId.of("Asia/Kolkata"))` with `advanceBy(ms)` |
| `AppDispatchers` | `DefaultAppDispatchers` | `TestAppDispatchers(testDispatcher)`; `MainDispatcherRule` |
| `LocationSource` | A's Play/Platform sources | `FakeLocationSource(engine, fixes: List<LocationFix>, clock: FakeClock?)` — emits on `TestScope` virtual time; `failAfter(n)` / `silenceAfter(ms)` to simulate engine failure |
| `StepSource`, `PressureSource` | A's Android sources | `FakeStepSource(events)`, `FakePressureSource(samples)`, both with `available` flags |
| `RunRepository` | `RoomRunRepository` | `FakeRunRepository` (in-memory, same semantics incl. idempotent import and PB ranking) |
| `SettingsRepository` | DataStore impl | `FakeSettingsRepository(initial = AppSettings())` |
| `MapOfflineManager`, `LocationAccuracyChecker` | C / F | `FakeMapOfflineManager`, `FakeAccuracyChecker` |
| `RunAnalyzer` etc. | B | tests of A may use B's real `AnalysisModule` (pure) or `FakeRunAnalyzer` |

### 6.2 GPS fixture format

Location: `app/src/test/resources/fixtures/<owner>_<name>.json` (`f0_`, `a_`, `b_`, `f_`). Loaded by
`testing/GpsFixture.kt` (F0): `GpsFixture.load(name: String): GpsFixture` via classpath.

```json
{
  "name": "b_city_loop_5k",
  "description": "5 km loop, urban canyon section km 2–3, one 40 s traffic-light stop",
  "zoneId": "Asia/Kolkata",
  "startMillis": 1764040800000,
  "profile": { "weightKg": 70, "ageYears": 30, "sex": "MALE", "heightCm": 175 },
  "fixes": [
    { "t": 0, "lat": 12.97160, "lon": 77.59460, "alt": 920.1, "msl": 913.4, "hAcc": 4.5, "vAcc": 6.0,
      "spd": 2.9, "sAcc": 0.4, "brg": 81.0, "engine": "PLAY" }
  ],
  "steps": [ { "t": 312 } ],
  "stepCounter": [ { "t": 1000, "count": 3 } ],
  "pressure": [ { "t": 0, "hPa": 912.31 } ],
  "pauses": [ { "start": 600000, "end": 660000 } ],
  "expect": {
    "distanceM": { "min": 4950, "max": 5050 },
    "movingMillis": { "min": 1500000, "max": 1560000 },
    "type": "RUN",
    "stops": 1,
    "splits": 5
  }
}
```
`t` is milliseconds from `startMillis`; omitted optional fields mean null/absent. `engine` defaults
to `PLAY`; dual-engine fixtures include both engines interleaved. `expect` is free-form per owner
(only keys a test reads matter). Helpers on `GpsFixture`: `toLocationFixes()`, `toRawPoints(runId)`,
`toSensorSamples()`, `toPauses()`, `toAnalysisInput(runId)`.

### 6.3 Who writes fixtures
- **F0**: `f0_straight_1k.json` (clean, 1 Hz, constant 3 m/s) to prove the loader; plus
  `core/demo/SyntheticRuns.kt` — deterministic generator (seeded `Random`) producing `RunGraph`s and
  fixture-like fix streams: steady run, run/walk intervals, a stop, noise level parameter.
- **B**: realistic analysis fixtures — stationary jitter (phone on a table), urban canyon with
  50–80 m accuracy spikes, tunnel gap requiring step fill, run/walk intervals, hill with barometer,
  multi-km with known PB windows. Can be generated by a test-only generator inside `test/…/analysis/`.
- **A**: engine scenarios — PLAY goes silent mid-run (fallback), PLATFORM-only, dual-engine
  interleaved, crash/restart replay (resume from DB state), weak-GPS start gate timing.
- **F**: `f_backup_v1.json` golden backup (two runs) for round-trip and idempotency tests.

### 6.4 What each track tests (minimum)
A: engine manager mode/fallback logic, GPS gate timing, pause/resume persistence, crash recovery
decisions, notification state mapping — all on `FakeClock` + fakes. B: every analysis stage + full
`analyze` on all `b_` fixtures with tolerance assertions. C: style source selection (key/no key),
pace-colour normalisation, route → GeoJSON conversion. D/E: ViewModel state with
`FakeRunRepository` + Turbine; E streak maths. F: backup round-trip, idempotent re-import, schema
tolerance (unknown keys, missing defaulted fields), scheduler interval mapping (Robolectric +
work-testing). F0: DAO/repository tests (Robolectric in-memory Room), formatting, aggregation, geo.

### 6.5 Demo data for UI tracks
Debug builds only: launching `MainActivity` with `--ez seed_demo true`
(`adb shell am start -n com.example.running/.MainActivity --ez seed_demo true`) imports ~40
synthetic finished runs over the last 16 weeks via `importRunGraph` (idempotent fixed ids), so D, E
and C can build UI before A records anything. Guarded by `BuildConfig.DEBUG` so R8 drops it.

---

## 7. Build / release conventions (copied from food-tracker)

- **Signing**: `github-public-release.jks` committed at repo root (deliberately public; passwords and
  alias all `github-public-release`, PKCS12, RSA 4096, 100-year validity). This is Running's **own key**:
  same file name, alias, passwords and parameters as food-tracker's, but a separately generated key pair
  (different certificate, so the two apps never share a signing identity). Kept as is (user decision,
  2026-11-24); do not regenerate it, or installed builds can no longer be updated. Generated in Run 1 with
  `keytool -genkeypair -keystore github-public-release.jks -storetype PKCS12 -alias github-public-release -keyalg RSA -keysize 4096 -validity 36500 -storepass github-public-release -keypass github-public-release -dname "CN=Running GitHub Public Release"`.
  `signingConfigs.create("githubPublicRelease")` with the same comment as food-tracker (never use for Play).
- **R8**: minify + shrink resources on release; `proguard-rules.pro` keeps
  `-keepattributes SourceFile,LineNumberTable` + `-renamesourcefileattribute SourceFile`; MapLibre,
  Play services, Room, WorkManager, kotlinx.serialization ship consumer rules — add rules only for
  a proven release crash. The template's `optimization {}` block and `src/main/keepRules/` are
  replaced by this.
- **ABI** `arm64-v8a`, `armeabi-v7a` on release only; **locales** `en`; packaging excludes as §4.3.
- **CI** (`.github/`):
  - `actions/build-apk/action.yml` "Build release APK": `actions/setup-java@v6` temurin **25**
    (matches `gradle-daemon-jvm.properties`), `gradle/actions/setup-gradle@v6`,
    `./gradlew :app:assembleRelease --stacktrace`. No secrets; no `MAPTILER_KEY` → OpenFreeMap.
  - `workflows/build-release-apk.yml` "Build Release APK" (manual) → artifact `Running-<sha7>-release.apk`.
  - `workflows/trigger-release.yml` "Trigger a Release" (manual, default branch only): bump
    `versionCode` +1 / `versionName` next minor (or inputs), commit "Release v<name>", build, atomic
    push of commit + tag `v<name>`, `gh release create` with **`Running-v<versionName>.apk`**.
- **Repo**: `git init` in Run 1 with local config `user.name "Anonymous"`, `user.email anonymous@example.com`;
  `.gitignore` = food-tracker's (keeps `!github-public-release.jks`, ignores `.claude/`, `local.properties`);
  LICENSE MIT "Copyright (c) 2026 Running contributors". Design reference screenshots are never copied
  into the repo.
- **Worktrees (Run 2)**: branch per track from the foundation commit — `track/a-recording`,
  `track/b-analysis`, `track/c-map`, `track/d-history`, `track/e-progress`, `track/f-setup`.
  Suggested merge order for Run 3: B, C, F, A, D, E (runtime dependencies first; file ownership
  means merges should be conflict-free).

---

## 8. Decisions (decided 2026-11-24 after Run 1 review)

All former open questions are settled; tracks treat these as rules.

1. **Background location: requested.** `ACCESS_BACKGROUND_LOCATION` is declared (§5.6). Onboarding (F)
   asks for it as a separate step after fine location (API 30+: settings-page flow, "Allow all the
   time"), explaining it is only for resuming a run after the system kills the app; skipping it is
   allowed. Recording always starts from the foreground. A's crash recovery auto-restarts the
   location FGS and resumes when `Permissions.hasBackgroundLocation()` is true, and otherwise (or if
   the FGS start is refused) falls back to the resume/finish prompt on the next app open (§3.1).
2. **Auto-backup**: `android:allowBackup="false"` (JSON backup is the mechanism; restoring a
   half-recorded ACTIVE run via Google backup would confuse recovery). Food-tracker uses `true`.
3. **Best-effort timing basis**: moving time (stops and pauses excluded), consistent with "stops
   excluded from moving time, pace, splits". Strava uses elapsed time for some efforts.
4. **PBs per type**: PB queries take an `ActivityFilter` (Progress filter applies); the run-detail
   PB badge compares within the run's own type.
5. **Dev dual-engine data** is backed up like everything else (RAW points from both engines).
6. **Kotlin 2.4.21** instead of the template's 2.2.10 (food-tracker is on 2.4.20); BOM is the stable
   `compose-bom` 2026.11.01 (material3 1.5.0 includes Expressive), not food-tracker's alpha BOM.
7. **Accent colour** is an orange in the Strava family but not Strava's exact `#FC4C02`.
8. **CI unit tests**: food-tracker has no test workflow; none added. Easy to add later.
9. **Live Updates** (`POST_PROMOTED_NOTIFICATIONS`, `Notification.ProgressStyle`) are used only on
   API 36+; older devices get a normal ongoing notification with public lock-screen visibility.
10. **Signing key**: keep Running's own public key generated in Run 1 (distinct from food-tracker's, §7).
11. **Launcher icon**: adaptive vector icon of a running figure (§9.13).

---

## 9. Run 1 (FOUNDATION) build notes and deviations

Built and verified on 2026-11-24: `./gradlew assembleDebug assembleRelease testDebugUnitTest` green
(21 JVM tests), `compileDebugAndroidTestKotlin` green, `app/schemas/com.example.running.data.db.RunningDatabase/1.json`
generated. Smoke-tested on the new dedicated AVD `Running_Test` (pixel_9, android-36.1
google_apis_playstore arm64): launches, onboarding Skip → tabs, `seed_demo` imported 40 runs, no crash.

**Versions:** every §4.1 version resolved as written (AGP 9.5.2, Kotlin 2.4.21, KSP 2.3.14, Compose BOM
2026.11.01 → material3 1.5.0 / ui 1.13.0, Room 2.8.5, …). `material-icons-extended` resolves to 1.7.8
through the BOM. compileSdk 37 minor 2 works.

**Deviations / additions (all additive unless noted):**
1. `RankedEffortRow.rank` maps to SQL alias `effort_rank` (Room rejects `AS rank`). Kotlin name unchanged.
2. `RunDao.observeLongestDistance/observeLongestDuration` (Flow) added for `observePersonalBests`.
3. Entities are one file each; the two query-row classes are in `entities/QueryRows.kt`.
4. Extra helpers: `GpsQuality.fromAccuracy(Float?)`, `GpsStatus.NONE`, `STANDING_SPEED_MPS` in Units.kt,
   `FakeClock.setNow`. `RunRepository.startPause` is a no-op while a pause is open;
   `loadAnalysisInput` closes an open pause at `endedAtMillis`; `saveAnalysis` forces kind FILTERED.
5. `AppNavHost(onboardingComplete, openRecordingRequest, onOpenRecordingHandled, navController)` — the
   start destination is read once per activity (splash held until settings load) so it never changes
   under the graph. Theme entry point is `RunningTheme {}`; tabs are `ui/navigation/Tab`.
6. The `OnboardingScreen` stub has a "Skip" action that sets `onboarding_complete` so other tracks can
   reach the tabs. F replaces it.
7. `allowBackup="false"` plus `data_extraction_rules.xml` / `backup_rules.xml` that exclude every domain
   (also blocks device-to-device transfer, which `allowBackup=false` alone does not on API 31+).
8. `AppBarChart`: when `selectedIndex` is set, other bars are drawn `Raised4` and the selected one in `color`.
   `AppLineChart` implements `invertY` by plotting −y (formatters receive the real value).
9. (Historical) Run 1 stub implementations lived in their owners' packages: `analysis/StubAnalysis.kt`,
   `map/StubRouteCanvas.kt`, `recording/StubRecordingController.kt`, `recording/RecordingService.kt`. All
   were replaced in Run 2 (§10.3).
10. `SyntheticRuns` API: `Spec(...)`, `fixes(spec, startMillis)`, `runGraph(id, startMillis, zoneId, spec, name?)`,
    `demoGraphs(nowMillis, zone, count = 40, weeks = 16)` (ids `demo-00`…).
11. Robolectric runs at `sdk=35` (`app/src/test/resources/robolectric.properties`); it does not ship 37 yet.
12. Signing key generated exactly per §7 (RSA 4096, 36500 days, PKCS12, `CN=Running GitHub Public Release`)
    — the same parameters and file/alias/password convention as food-tracker, rather than the Run-1 task
    text's RSA 2048 / 10000 days / `CN=Running`. It is a new key pair, distinct from food-tracker's
    (SHA-256 `EE:A6:59:61:…:A5:29:F2:18` vs food-tracker `C0:07:52:08:…:DB:60:D0:05`).
13. Launcher icon (foundation patch): adaptive icon in `mipmap-anydpi/ic_launcher.xml` and
    `ic_launcher_round.xml` (minSdk 31, so no density PNG/WebP fallbacks). Layers are vectors:
    `drawable/ic_launcher_background.xml` (`Raised1` near-black), `ic_launcher_foreground.xml` (bold
    running figure in `Accent`, inside the 66 dp safe zone) and `ic_launcher_monochrome.xml` (same
    figure, single colour, for themed icons). The template WebP icons were deleted. The splash screen
    reuses `@mipmap/ic_launcher`.

**Foundation patch (after Run 1 review):** `ACCESS_BACKGROUND_LOCATION` declared;
`Permissions.BACKGROUND_LOCATION`, `hasBackgroundLocation()`, `canRequestBackgroundLocation()` added
(`test/…/core/PermissionsTest.kt`); §3.1 recovery rule, §3.8 onboarding order, §5.6, §7 and §8 updated.

---

## 10. Run 2/3 integration (2026-11-24)

Tracks merged into `main` with `--no-ff` in contract order B, C, F, A, D, E. Every track stayed inside
its owned paths, so all six merges were clean. The track worktrees were removed; the `track/*`
branches are kept.

**Shared-change requests applied:**
- **SCR-A-1, SCR-E-1** → `core/util/Format.kt`: `formatAccuracy`, `formatMeters`, `formatSecondsPrecise`,
  `formatPercent`, `formatWeekLabel`, `formatMonthLabel` (tests in `test/…/core/FormatTest.kt`). The
  stand-ins `ui/record/RecordFormat.kt` and the stand-in half of `ui/progress/ProgressFormat.kt` are
  gone; Record, dev compare and Progress import from `core.util`.
- **SCR-A-2** (docs) → §2.5 and §3.1 above.
- **SCR-B-1** → `RunSeriesBuilder.build(filteredPoints, sensorSamples = emptyList())`. B's overload is now
  the override. D still passes only points because it does not chart cadence.
- **SCR-B-2** → `MapRoute.fromTrackPoints` drops `paused` points. B keeps fixes recorded during a pause
  in FILTERED (`paused = true`, distance held, no pace or segment), so without this the map would draw
  the walk made during a pause.

**Integrator changes beyond the SCRs:**
1. **Live clock** (A's known gap): B's live `movingMillis` leaves out detected stops, so a clock built on
   it would stop at traffic lights, which amounts to auto-pause (rejected). `LiveStats` gained
   `pausedMillis` (default 0, filled by B) and `activeMillis = elapsed − paused`. The Record screen
   "Time" stat and the notification chronometer use `activeMillis`; moving time stays in the analysis
   and on Run detail.
2. **Offline area retry** (C's known gap): `RunDetailViewModel` calls `downloadRunArea` when the run's
   area status is NONE or FAILED. The download is tied to the screen, so leaving cancels it and the
   next open retries.
3. No stubs remain: `StubAnalysis`, `StubRouteCanvas` and `StubRecordingController` were deleted by their
   tracks, and `AppContainer` already builds the real modules through the CONTRACT files.

**Build:** `./gradlew assembleDebug assembleRelease testDebugUnitTest` green on JDK 25 (226 JVM tests);
`lintDebug` 0 errors (warnings only: UnusedResources, UseKtx, PluralsCandidate, InlinedApi,
StaticFieldLeak in `BackupViewModel`).

**Open after integration** (for the review/fix pass): no screen has been run on the emulator since the
merge; MapLibre rendering and the offline download are unverified on real tiles; controller tests use a
fake LiveMetrics, so a replay test of A against B's real LiveMetrics is still missing; `RunSaved` has no
replay, so finishing from the notification while Record is off-screen does not open Run detail (fixed in
§10.5 for a live activity); a run whose analysis always throws loops back to the recovery prompt.

### 10.4 Run 3 fix pass: emulator smoke test (2026-11-24)

On `Running_Test` (release APK): onboarding, a ~1.2 km replayed run with a pause (movement while
paused not counted), a crash (`am crash`, sticky service restart auto-resumed) and a force-stop
(auto-resumed on app open), finish → Run detail, Activities, Progress, PBs, Settings, Backup.
Two crashes found and fixed in `ui/components/charts/`: Vico 3 rejects x values with more than four
decimals (Run detail charts now snap x to a regular grid and pass it as the x step) and blank axis
labels (Progress weekly labels are now thinned by the item placer instead of `" "`). The line chart
y range now fits the data on round label steps instead of starting at 0.
Emulator note: `adb emu geo fix` reports a trusted Doppler speed of 0, which the analysis treats as
standing still (distance stays 0). Replay with a shell test provider instead
(`cmd location providers add-test-provider gps` + `set-test-provider-location`), which sends no speed.

### 10.5 Run 3 fix pass, round 2: emulator smoke test (2026-11-24)

On `Running_Test` (release APK, fresh install): onboarding, a ~0.95 km replayed run with a 25 s pause
(movement while paused not counted), a force-stop mid-run (auto-resumed on app open), finish → Run
detail (map, pace/elevation charts, splits), Activities, Progress; a second run finished from the
notification with the app in the background opened Run detail. No crashes in logcat. Fixed:
- Live pace/avg pace after a gap with no events (process restart, GPS outage without steps): moving
  time accrued only `LIVE_MAX_ACCRUAL_MILLIS` while the gap's straight-line distance was counted, so
  pace read ~1:11 /km after auto-resume. A gap covered at moving speed now counts as moving time
  (as in the analyzer).
- Chart labels: x labels sit on a 1-2-5 interval of whole grid steps no finer than the formatter
  (`minXLabelStep`; was "0.0, 0.1, 0.1, 0.2"), and a flat elevation line gets whole-metre y labels
  (`minYStep`; was "6 m, 5 m, 5 m").
- Mock fixes (`Location.isMock`: test providers, mock-location apps) drop speed and speed accuracy, so
  a mock's constant 0 m/s is never trusted as Doppler.
- `AppNavHost` opens Run detail on `RunSaved` when another destination is showing (Record handles its
  own case).
Emulator replay: `adb emu geo fix <lon> <lat> <alt> <sats> <knots>` with the speed argument gives a
realistic Doppler speed (without it the speed is 0 and the run records 0 km).
