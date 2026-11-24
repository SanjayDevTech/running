package com.example.running.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.running.analysis.AnalysisModule
import com.example.running.core.demo.SyntheticRuns
import com.example.running.core.model.ActivityFilter
import com.example.running.core.model.ActivityType
import com.example.running.core.model.BestEffortDistance
import com.example.running.core.model.LocationEngine
import com.example.running.core.model.LocationEngineMode
import com.example.running.core.model.PauseInterval
import com.example.running.core.model.PointKind
import com.example.running.core.model.RunStatus
import com.example.running.core.model.RunnerProfile
import com.example.running.data.db.RunningDatabase
import com.example.running.data.repo.RoomRunRepository
import com.example.running.testing.FakeClock
import com.example.running.testing.GpsFixture
import com.example.running.testing.TestAppDispatchers
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomRunRepositoryTest {
    private lateinit var db: RunningDatabase
    private lateinit var repo: RoomRunRepository
    private val clock = FakeClock()
    private val analysis = AnalysisModule(clock)

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, RunningDatabase::class.java).allowMainThreadQueries().build()
        repo = RoomRunRepository(db, clock, TestAppDispatchers()) { analysis.runNamer }
    }

    @After fun tearDown() = db.close()

    @Test fun recordPauseAnalyseFinish() = runTest {
        val f = GpsFixture.load("f0_straight_1k")
        val run = repo.createActiveRun("r1", f.startMillis, f.zoneId, LocationEngineMode.AUTO, LocationEngine.PLAY, false)
        assertThat(run.status).isEqualTo(RunStatus.ACTIVE)
        assertThat(repo.findActiveRun()?.id).isEqualTo("r1")

        val raw = f.toRawPoints("r1")
        repo.appendRawPoints(raw.take(100))
        repo.appendRawPoints(raw.drop(100))
        repo.startPause("r1", f.startMillis + 100_000)
        repo.startPause("r1", f.startMillis + 101_000) // repeated pause is a no-op
        repo.endPause("r1", f.startMillis + 110_000)
        repo.startPause("r1", f.startMillis + 300_000) // left open: closed at endedAt in the input
        repo.markEngineFallback("r1")

        val input = repo.loadAnalysisInput("r1", f.endMillis, RunnerProfile())
        assertThat(input.rawPoints).hasSize(raw.size)
        assertThat(input.pauses).containsExactly(
            PauseInterval(f.startMillis + 100_000, f.startMillis + 110_000),
            PauseInterval(f.startMillis + 300_000, f.endMillis),
        ).inOrder()
        assertThat(input.manualType).isNull()

        val result = analysis.runAnalyzer.analyze(input)
        repo.saveAnalysis("r1", f.endMillis, result)
        val saved = repo.getRun("r1")!!
        assertThat(saved.status).isEqualTo(RunStatus.FINISHED)
        assertThat(saved.engineFallback).isTrue()
        assertThat(saved.name).isEqualTo("Morning Run")
        assertThat(saved.stats.distanceM).isWithin(1e-6).of(result.stats.distanceM)
        assertThat(repo.findActiveRun()).isNull()
        assertThat(repo.getFilteredPoints("r1")).hasSize(raw.size)
        assertThat(repo.getRawPoints("r1")).hasSize(raw.size)
        assertThat(repo.observeSplits("r1").first()).hasSize(result.splits.size)
        assertThat(repo.observeRuns(ActivityFilter.ALL).first().map { it.id }).containsExactly("r1")
        assertThat(repo.observeRuns(ActivityFilter.WALK).first()).isEmpty()

        // re-saving replaces derived rows instead of duplicating them
        repo.saveAnalysis("r1", f.endMillis, result)
        assertThat(repo.getFilteredPoints("r1")).hasSize(raw.size)
    }

    @Test fun renameAndSetTypeKeepCustomNames() = runTest {
        val g = SyntheticRuns.runGraph("a", 1_764_040_800_000L, "Asia/Kolkata", SyntheticRuns.Spec(distanceM = 1_200.0))
        assertThat(repo.importRunGraph(g)).isTrue()
        repo.setType("a", ActivityType.WALK)
        assertThat(repo.getRun("a")!!.name).isEqualTo("Morning Walk")
        assertThat(repo.getRun("a")!!.typeIsManual).isTrue()
        repo.rename("a", "  Park loop ")
        repo.setType("a", ActivityType.RUN)
        val r = repo.getRun("a")!!
        assertThat(r.name).isEqualTo("Park loop")
        assertThat(r.nameIsCustom).isTrue()
        assertThat(r.type).isEqualTo(ActivityType.RUN)
    }

    @Test fun importIsIdempotentAndGraphRoundTrips() = runTest {
        val g = SyntheticRuns.runGraph("g", 1_764_040_800_000L, "Asia/Kolkata", SyntheticRuns.Spec(distanceM = 2_100.0, stopSeconds = 20))
        assertThat(repo.importRunGraph(g)).isTrue()
        assertThat(repo.importRunGraph(g)).isFalse()
        val back = repo.getRunGraph("g")!!
        assertThat(back.run).isEqualTo(g.run)
        assertThat(back.points.sortedWith(compareBy({ it.kind }, { it.timeMillis })))
            .isEqualTo(g.points.sortedWith(compareBy({ it.kind }, { it.timeMillis })))
        assertThat(back.splits).isEqualTo(g.splits)
        assertThat(back.segments).isEqualTo(g.segments)
        assertThat(back.bestEfforts).containsExactlyElementsIn(g.bestEfforts)
        assertThat(back.sensorSamples).isEqualTo(g.sensorSamples)
        assertThat(repo.getAllRunIds()).containsExactly("g")

        repo.deleteRun("g")
        assertThat(repo.getRunGraph("g")).isNull()
        assertThat(db.trackPointDao().get("g", PointKind.RAW)).isEmpty() // cascade
    }

    @Test fun personalBestsAndRanking() = runTest {
        val start = 1_764_040_800_000L
        val fast = SyntheticRuns.runGraph("fast", start, "Asia/Kolkata", SyntheticRuns.Spec(distanceM = 5_100.0, paceSecPerKm = 280.0, seed = 1))
        val slow = SyntheticRuns.runGraph("slow", start + 86_400_000, "Asia/Kolkata", SyntheticRuns.Spec(distanceM = 11_000.0, paceSecPerKm = 360.0, seed = 2))
        val walk = SyntheticRuns.runGraph("walk", start + 2 * 86_400_000, "Asia/Kolkata", SyntheticRuns.Spec(distanceM = 1_500.0, paceSecPerKm = 700.0, walkOnly = true, seed = 3))
        listOf(fast, slow, walk).forEach { repo.importRunGraph(it) }

        val pbs = repo.observePersonalBests(ActivityFilter.RUN).first()
        assertThat(pbs.efforts.keys).containsExactlyElementsIn(BestEffortDistance.entries)
        assertThat(pbs.efforts[BestEffortDistance.K5]!!.runId).isEqualTo("fast")
        assertThat(pbs.efforts[BestEffortDistance.K10]!!.runId).isEqualTo("slow")
        assertThat(pbs.efforts[BestEffortDistance.HALF]).isNull()
        assertThat(pbs.longestDistance!!.runId).isEqualTo("slow")
        assertThat(pbs.longestDuration!!.runId).isEqualTo("slow")

        val walkPbs = repo.observePersonalBests(ActivityFilter.WALK).first()
        assertThat(walkPbs.efforts[BestEffortDistance.K1]!!.runId).isEqualTo("walk")

        val slowRanks = repo.observeBestEfforts("slow").first().associate { it.effort.distance to it.rank }
        assertThat(slowRanks[BestEffortDistance.K1]).isEqualTo(2)
        assertThat(slowRanks[BestEffortDistance.K10]).isEqualTo(1)
        // the walk is ranked only against walks
        assertThat(repo.observeBestEfforts("walk").first().single().isPersonalBest).isTrue()

        val between = repo.observeRunsBetween(start, start + 2 * 86_400_000, ActivityFilter.ALL).first()
        assertThat(between.map { it.id }).containsExactly("fast", "slow").inOrder()
    }
}
