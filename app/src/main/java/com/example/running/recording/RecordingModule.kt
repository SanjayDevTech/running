package com.example.running.recording

import com.example.running.analysis.AnalysisModule
import com.example.running.core.contracts.RecordingController
import com.example.running.di.CoreDeps
import com.example.running.map.MapModule

/** CONTRACT(A). Public signature frozen (architecture.md §3.1). */
class RecordingModule(core: CoreDeps, analysis: AnalysisModule, map: MapModule) {
    val controller: RecordingController = StubRecordingController()
}
