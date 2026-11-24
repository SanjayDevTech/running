package com.example.running.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.running.core.model.AppSettings
import com.example.running.core.model.BackupSettings
import com.example.running.testing.FakeSettingsRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class BackupSchedulerTest {
    private lateinit var context: Context
    private lateinit var work: WorkManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().build())
        work = WorkManager.getInstance(context)
    }

    private fun infos() = work.getWorkInfosForUniqueWork(BackupScheduler.WORK_NAME).get()

    @Test
    fun positiveIntervalEnqueuesOneUniquePeriodicJobWithConstraints() {
        val scheduler = BackupScheduler(context, FakeSettingsRepository())
        scheduler.apply(24)
        val info = infos().single()
        assertThat(info.state).isEqualTo(WorkInfo.State.ENQUEUED)
        assertThat(info.constraints.requiresBatteryNotLow()).isTrue()
        assertThat(info.constraints.requiresStorageNotLow()).isTrue()
        assertThat(info.periodicityInfo!!.repeatIntervalMillis).isEqualTo(TimeUnit.HOURS.toMillis(24))
    }

    @Test
    fun changingTheIntervalReplacesTheJobRatherThanAddingOne() {
        val scheduler = BackupScheduler(context, FakeSettingsRepository())
        scheduler.apply(24)
        scheduler.apply(168)
        val info = infos().single { !it.state.isFinished }
        assertThat(info.periodicityInfo!!.repeatIntervalMillis).isEqualTo(TimeUnit.HOURS.toMillis(168))
    }

    @Test
    fun zeroCancelsTheJob() {
        val scheduler = BackupScheduler(context, FakeSettingsRepository())
        scheduler.apply(24)
        scheduler.apply(0)
        assertThat(infos().all { it.state == WorkInfo.State.CANCELLED }).isTrue()
    }

    @Test
    fun applyFromSettingsReadsTheStoredInterval() = runBlocking {
        val settings = FakeSettingsRepository(AppSettings(backup = BackupSettings(intervalHours = 72)))
        BackupScheduler(context, settings).applyFromSettings()
        assertThat(infos().single().periodicityInfo!!.repeatIntervalMillis).isEqualTo(TimeUnit.HOURS.toMillis(72))
    }
}
