package com.example.running.analysis

import com.example.running.core.contracts.LiveMetricsFactory
import com.example.running.core.contracts.RunAnalyzer
import com.example.running.core.contracts.RunNamer
import com.example.running.core.contracts.RunSeriesBuilder
import com.example.running.core.util.Clock

/**
 * CONTRACT(B). Public signature frozen (architecture.md §3.3); bodies are B's.
 * Currently wired to FOUNDATION's minimal stubs in StubAnalysis.kt.
 */
class AnalysisModule(clock: Clock) {
    val runNamer: RunNamer = StubRunNamer()
    val runAnalyzer: RunAnalyzer = StubRunAnalyzer(runNamer)
    val liveMetricsFactory: LiveMetricsFactory = StubLiveMetricsFactory()
    val runSeriesBuilder: RunSeriesBuilder = StubRunSeriesBuilder()
}
