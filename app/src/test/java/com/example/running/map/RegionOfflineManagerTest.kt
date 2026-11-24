package com.example.running.map

import com.example.running.core.model.GeoBounds
import com.example.running.core.model.OfflineAreaStatus
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class RegionOfflineManagerTest {
    private class FakeBackend : OfflineBackend {
        val regions = mutableMapOf<Long, String>()       // handle -> runId
        val complete = mutableSetOf<Long>()
        var nextHandle = 1L
        var createFails = false
        var downloadResult = true
        val deleted = mutableListOf<Long>()

        override suspend fun find(runId: String) = regions.filterValues { it == runId }.keys.toList()
        override suspend fun create(runId: String, bounds: GeoBounds): Long? {
            if (createFails) return null
            return nextHandle++.also { regions[it] = runId }
        }
        override suspend fun download(handle: Long): Boolean {
            if (downloadResult) complete += handle
            return downloadResult
        }
        override suspend fun progress(handle: Long) = if (handle in regions) RegionProgress(handle in complete) else null
        override suspend fun delete(handle: Long) { regions.remove(handle); complete.remove(handle); deleted += handle }
    }

    private val bounds = GeoBounds(12.0, 77.0, 12.01, 77.02)

    @Test
    fun noRegionMeansNone() = runTest {
        assertThat(RegionOfflineManager(FakeBackend()).status("r1")).isEqualTo(OfflineAreaStatus.NONE)
    }

    @Test
    fun successfulDownloadIsReady() = runTest {
        val m = RegionOfflineManager(FakeBackend())
        assertThat(m.downloadRunArea("r1", bounds)).isTrue()
        assertThat(m.status("r1")).isEqualTo(OfflineAreaStatus.READY)
        assertThat(m.status("other")).isEqualTo(OfflineAreaStatus.NONE)
    }

    @Test
    fun failedDownloadIsFailedAndLeavesNoRegion() = runTest {
        val backend = FakeBackend().apply { downloadResult = false }
        val m = RegionOfflineManager(backend)
        assertThat(m.downloadRunArea("r1", bounds)).isFalse()
        assertThat(m.status("r1")).isEqualTo(OfflineAreaStatus.FAILED)
        assertThat(backend.regions).isEmpty()
    }

    @Test
    fun createFailureIsFailed() = runTest {
        val m = RegionOfflineManager(FakeBackend().apply { createFails = true })
        assertThat(m.downloadRunArea("r1", bounds)).isFalse()
        assertThat(m.status("r1")).isEqualTo(OfflineAreaStatus.FAILED)
    }

    @Test
    fun retryAfterFailureClearsFailedState() = runTest {
        val backend = FakeBackend().apply { downloadResult = false }
        val m = RegionOfflineManager(backend)
        m.downloadRunArea("r1", bounds)
        backend.downloadResult = true
        assertThat(m.downloadRunArea("r1", bounds)).isTrue()
        assertThat(m.status("r1")).isEqualTo(OfflineAreaStatus.READY)
    }

    @Test
    fun redownloadReplacesExistingRegion() = runTest {
        val backend = FakeBackend()
        val m = RegionOfflineManager(backend)
        m.downloadRunArea("r1", bounds)
        m.downloadRunArea("r1", bounds)
        assertThat(backend.regions.values.count { it == "r1" }).isEqualTo(1)
        assertThat(backend.deleted).hasSize(1)
    }

    @Test
    fun incompleteStoredRegionReportsFailed() = runTest {
        val backend = FakeBackend().apply { regions[7L] = "r1" }
        assertThat(RegionOfflineManager(backend).status("r1")).isEqualTo(OfflineAreaStatus.FAILED)
    }

    @Test
    fun deleteRemovesRegionAndResetsStatus() = runTest {
        val backend = FakeBackend()
        val m = RegionOfflineManager(backend)
        m.downloadRunArea("r1", bounds)
        m.downloadRunArea("r2", bounds)
        m.deleteRunArea("r1")
        assertThat(m.status("r1")).isEqualTo(OfflineAreaStatus.NONE)
        assertThat(m.status("r2")).isEqualTo(OfflineAreaStatus.READY)
    }

    @Test
    fun metadataRoundTripsAwkwardIds() {
        for (id in listOf("abc-123", "we\"ird\\id", "")) {
            assertThat(OfflinePlanner.decodeRunId(OfflinePlanner.encodeMetadata(id))).isEqualTo(id)
        }
        assertThat(OfflinePlanner.decodeRunId(null)).isNull()
        assertThat(OfflinePlanner.decodeRunId("{}".toByteArray())).isNull()
    }

    @Test
    fun areaIsPaddedBeyondRunBounds() {
        val a = OfflinePlanner.area(bounds)
        assertThat(a.minLat).isLessThan(bounds.minLat)
        assertThat(a.maxLon).isGreaterThan(bounds.maxLon)
    }
}
