package com.example.running.core.model

data class EffortRecord(val runId: String, val runName: String, val runStartedAtMillis: Long, val effort: BestEffort)

data class RunRecord(
    val runId: String,
    val runName: String,
    val runStartedAtMillis: Long,
    val distanceM: Double,
    val movingMillis: Long,
)

data class PersonalBests(
    /** Every [BestEffortDistance] key is present. */
    val efforts: Map<BestEffortDistance, EffortRecord?>,
    val longestDistance: RunRecord?,
    val longestDuration: RunRecord?,
)
