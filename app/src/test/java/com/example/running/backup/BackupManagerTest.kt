package com.example.running.backup

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.running.core.demo.SyntheticRuns
import com.example.running.core.model.AppSettings
import com.example.running.core.model.LocationEngine
import com.example.running.core.model.LocationEngineMode
import com.example.running.core.model.RunnerProfile
import com.example.running.core.model.Sex
import com.example.running.testing.FakeClock
import com.example.running.testing.FakeRunRepository
import com.example.running.testing.FakeSettingsRepository
import com.example.running.testing.TestAppDispatchers
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupManagerTest {
    private val clock = FakeClock()
    private val storage = MemStorage()

    private class Rig(val repo: FakeRunRepository, val settings: FakeSettingsRepository, val manager: BackupManager)

    private fun TestScope.rig(initial: AppSettings = AppSettings(), storage: MemStorage = this@BackupManagerTest.storage): Rig {
        val repo = FakeRunRepository(clock)
        val settings = FakeSettingsRepository(initial)
        val manager = BackupManager(
            storage, repo, settings, clock, TestAppDispatchers(UnconfinedTestDispatcher(testScheduler)), "1.0-test",
        )
        return Rig(repo, settings, manager)
    }

    private fun graphs() = listOf(
        SyntheticRuns.runGraph("r1", 1_764_040_800_000L, "Asia/Kolkata", SyntheticRuns.Spec(distanceM = 400.0, seed = 1), "Morning Run"),
        SyntheticRuns.runGraph("r2", 1_764_127_200_000L, "Asia/Kolkata", SyntheticRuns.Spec(distanceM = 300.0, walkOnly = true, paceSecPerKm = 700.0, seed = 2), "Walk"),
    )

    @Test
    fun exportThenImportIntoEmptyAppReproducesEveryRunGraph() = runTest {
        val a = rig()
        graphs().forEach { a.repo.importRunGraph(it) }
        val target = Uri.parse("mem://export/backup.json")
        assertThat(a.manager.exportTo(target).getOrThrow()).isEqualTo(2)

        val b = rig()
        val result = b.manager.importFrom(target, restoreProfile = false).getOrThrow()
        assertThat(result).isEqualTo(ImportResult(imported = 2, skippedExisting = 0, failed = 0))
        graphs().forEach { original ->
            assertThat(b.repo.getRunGraph(original.run.id)).isEqualTo(original)
        }
    }

    @Test
    fun importIsIdempotent() = runTest {
        val a = rig()
        graphs().forEach { a.repo.importRunGraph(it) }
        val target = Uri.parse("mem://export/backup.json")
        a.manager.exportTo(target).getOrThrow()

        val b = rig()
        b.manager.importFrom(target, false).getOrThrow()
        val second = b.manager.importFrom(target, false).getOrThrow()
        assertThat(second).isEqualTo(ImportResult(imported = 0, skippedExisting = 2, failed = 0))
        assertThat(b.repo.getAllRunIds()).hasSize(2)
    }

    @Test
    fun onlyFinishedRunsAreExported() = runTest {
        val a = rig()
        a.repo.importRunGraph(graphs().first())
        a.repo.createActiveRun("active", 1L, "UTC", LocationEngineMode.AUTO, LocationEngine.PLAY, false)
        val payload = a.manager.buildPayload()
        assertThat(payload.runs.map { it.id }).containsExactly("r1")
    }

    @Test
    fun goldenV1FixtureRestores() = runTest {
        val json = javaClass.getResourceAsStream("/fixtures/f_backup_v1.json")!!.readBytes()
        val source = Uri.parse("mem://golden.json")
        storage.blobs[source] = json
        val b = rig()
        val result = b.manager.importFrom(source, restoreProfile = true).getOrThrow()
        assertThat(result).isEqualTo(ImportResult(2, 0, 0))
        assertThat(b.repo.getRunGraph("golden-1")!!.points).isNotEmpty()
        assertThat(b.repo.getRunGraph("golden-2")!!.run.name).isEqualTo("Evening Walk")
        assertThat(b.settings.current().profile.weightKg).isEqualTo(70.0)
    }

    @Test
    fun unknownKeysAndMissingDefaultedFieldsAreTolerated() = runTest {
        val text = """
            {"createdAtMillis":1,"appVersion":"9.9","futureField":{"x":1},
             "profile":{"weightKg":61.5,"ageYears":40,"sex":"FEMALE","somethingNew":true},
             "settings":{"engineMode":"BUILT_IN","other":1},
             "runs":[{"id":"min","name":"Minimal","type":"RUN","startedAtMillis":5,"zoneId":"UTC","extra":[1,2]}]}
        """.trimIndent()
        val source = Uri.parse("mem://min.json")
        storage.blobs[source] = text.toByteArray()
        val b = rig()
        val result = b.manager.importFrom(source, restoreProfile = true).getOrThrow()
        assertThat(result.imported).isEqualTo(1)
        val run = b.repo.getRunGraph("min")!!.run
        assertThat(run.stats.distanceM).isEqualTo(0.0)
        assertThat(run.engineMode).isEqualTo(LocationEngineMode.AUTO)
        val s = b.settings.current()
        assertThat(s.profile.sex).isEqualTo(Sex.FEMALE)
        assertThat(s.engineMode).isEqualTo(LocationEngineMode.BUILT_IN)
    }

    @Test
    fun aBrokenRunCountsAsFailedAndDoesNotStopTheRest() = runTest {
        val text = """
            {"createdAtMillis":1,"appVersion":"1","profile":{"weightKg":70,"ageYears":30,"sex":"MALE"},
             "runs":[{"id":"bad","name":"Bad","type":"SWIM","startedAtMillis":5,"zoneId":"UTC"},
                     {"id":"ok","name":"Ok","type":"WALK","startedAtMillis":6,"zoneId":"UTC"}]}
        """.trimIndent()
        val source = Uri.parse("mem://mixed.json")
        storage.blobs[source] = text.toByteArray()
        val b = rig()
        assertThat(b.manager.importFrom(source, false).getOrThrow()).isEqualTo(ImportResult(1, 0, 1))
    }

    @Test
    fun garbageAndFutureSchemaFilesFailCleanly() = runTest {
        val b = rig()
        val garbage = Uri.parse("mem://garbage.json").also { storage.blobs[it] = "not json".toByteArray() }
        assertThat(b.manager.importFrom(garbage, false).isFailure).isTrue()
        val future = Uri.parse("mem://future.json").also {
            storage.blobs[it] = """{"schemaVersion":99,"createdAtMillis":1,"appVersion":"x","profile":{"weightKg":70,"ageYears":30,"sex":"MALE"}}""".toByteArray()
        }
        assertThat(b.manager.importFrom(future, false).isFailure).isTrue()
    }

    @Test
    fun profileIsRestoredByDefaultOnlyBeforeOnboardingCompletes() = runTest {
        val source = Uri.parse("mem://p.json")
        val a = rig(AppSettings(profile = RunnerProfile(weightKg = 55.0, ageYears = 25, sex = Sex.FEMALE, heightCm = 168)))
        a.manager.exportTo(source).getOrThrow()

        val fresh = rig(AppSettings(onboardingComplete = false))
        fresh.manager.importFrom(source).getOrThrow()
        assertThat(fresh.settings.current().profile.weightKg).isEqualTo(55.0)
        assertThat(fresh.settings.current().profile.heightCm).isEqualTo(168)

        val onboarded = rig(AppSettings(onboardingComplete = true))
        onboarded.manager.importFrom(source).getOrThrow()
        assertThat(onboarded.settings.current().profile.weightKg).isEqualTo(70.0)
    }

    @Test
    fun scheduledBackupWritesNamedFileRecordsTimeAndKeepsNewestFive() = runTest {
        val a = rig()
        a.repo.importRunGraph(graphs().first())
        repeat(7) {
            clock.advanceBy(60_000)
            a.manager.saveScheduledBackup().getOrThrow()
        }
        val files = a.manager.listBackups()
        assertThat(files).hasSize(5)
        assertThat(files.all { it.name.matches(Regex("running-backup-\\d{8}-\\d{6}\\.json")) }).isTrue()
        assertThat(a.settings.current().backup.lastBackupAtMillis).isEqualTo(clock.nowMillis())
        // newest first
        assertThat(files.map { it.name }).isInOrder(Comparator.reverseOrder<String>())
    }

    @Test
    fun scheduledBackupUsesTheConfiguredFolder() = runTest {
        val a = rig()
        a.settings.setBackupFolderUri("content://tree/abc")
        a.manager.saveScheduledBackup().getOrThrow()
        assertThat(storage.list("content://tree/abc")).hasSize(1)
        assertThat(storage.list(null)).isEmpty()
    }

    @Test
    fun failedWriteIsReportedNotThrown() = runTest {
        val a = rig()
        storage.failWrites = true
        assertThat(a.manager.saveScheduledBackup().isFailure).isTrue()
        assertThat(a.settings.current().backup.lastBackupAtMillis).isNull()
    }

    @Test
    fun fileNameUsesTheZoneAndPrunePicksOldest() {
        assertThat(backupFileName(1_764_040_800_000L, java.time.ZoneId.of("Asia/Kolkata")))
            .isEqualTo("running-backup-20251125-085000.json")
        val files = (1..7).map { BackupFile("running-backup-$it.json", Uri.parse("mem://$it"), 1, it.toLong()) }
        assertThat(filesToPrune(files, 5).map { it.modifiedMillis }).containsExactly(2L, 1L)
    }
}
