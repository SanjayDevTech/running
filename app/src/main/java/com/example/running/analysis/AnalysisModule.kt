package com.example.running.analysis

import com.example.running.core.contracts.LiveMetricsFactory
import com.example.running.core.contracts.RunAnalyzer
import com.example.running.core.contracts.RunNamer
import com.example.running.core.contracts.RunSeriesBuilder
import com.example.running.core.util.Clock

/**
 * CONTRACT(B). Public signature frozen (architecture.md §3.3). Everything behind these four
 * interfaces is pure Kotlin; only each LiveMetrics instance holds state. The clock is part of the
 * contract but unused: analysis works purely on the timestamps it is given.
 */
class AnalysisModule(@Suppress("UNUSED_PARAMETER") clock: Clock) {
    val runNamer: RunNamer = DefaultRunNamer()
    val runAnalyzer: RunAnalyzer = DefaultRunAnalyzer(runNamer)
    val liveMetricsFactory: LiveMetricsFactory = DefaultLiveMetricsFactory()
    val runSeriesBuilder: RunSeriesBuilder = DefaultRunSeriesBuilder()
}
