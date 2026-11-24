package com.example.running.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.running.data.db.dao.RunDao
import com.example.running.data.db.dao.RunPartsDao
import com.example.running.data.db.dao.SensorSampleDao
import com.example.running.data.db.dao.TrackPointDao
import com.example.running.data.db.entities.BestEffortEntity
import com.example.running.data.db.entities.PauseEntity
import com.example.running.data.db.entities.RunEntity
import com.example.running.data.db.entities.SegmentEntity
import com.example.running.data.db.entities.SensorSampleEntity
import com.example.running.data.db.entities.SplitEntity
import com.example.running.data.db.entities.TrackPointEntity

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

        /** No destructive fallback, ever: every schema change ships a Migration and a MigrationTest. */
        fun build(context: Context): RunningDatabase =
            Room.databaseBuilder(context.applicationContext, RunningDatabase::class.java, NAME).build()
    }
}
