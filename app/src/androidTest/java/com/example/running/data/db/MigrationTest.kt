package com.example.running.data.db

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Schema v1 smoke test, and the scaffold for future migrations: add MIGRATION_1_2 to
 * RunningDatabase, then a test here that creates v1, inserts rows and runs
 * `helper.runMigrationsAndValidate(dbName, 2, true, RunningDatabase.MIGRATION_1_2)`.
 * Schemas come from app/schemas via androidTest assets.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val dbName = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        RunningDatabase::class.java,
    )

    @Test
    fun createV1_acceptsRowsAndOpensWithRoom() {
        helper.createDatabase(dbName, 1).use { db ->
            db.execSQL(
                "INSERT INTO runs (id, name, name_is_custom, type, type_is_manual, status, started_at_millis, " +
                    "ended_at_millis, zone_id, engine_mode, start_engine, engine_fallback, dev_dual_engine, distance_m, " +
                    "elapsed_millis, moving_millis, paused_millis, elevation_source, step_filled_m, analysis_version, " +
                    "created_at_millis, updated_at_millis) VALUES ('r1', 'Morning Run', 0, 'RUN', 0, 'FINISHED', 1000, " +
                    "2000, 'Asia/Kolkata', 'AUTO', 'PLAY', 0, 0, 1234.5, 1000, 900, 100, 'NONE', 0, 1, 1000, 2000)"
            )
            db.execSQL(
                "INSERT INTO track_points (run_id, kind, engine, is_primary, time_millis, lat, lon, paused, step_filled) " +
                    "VALUES ('r1', 'RAW', 'PLAY', 1, 1000, 12.97, 77.59, 0, 0)"
            )
        }
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val room = Room.databaseBuilder(ctx, RunningDatabase::class.java, dbName).build()
        try {
            room.openHelper.writableDatabase.query("SELECT name, distance_m FROM runs WHERE id = 'r1'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("Morning Run", c.getString(0))
                assertEquals(1234.5, c.getDouble(1), 1e-9)
            }
        } finally {
            room.close()
        }
    }
}
