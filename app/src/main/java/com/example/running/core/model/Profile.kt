package com.example.running.core.model

data class RunnerProfile(
    val weightKg: Double = 70.0,
    val ageYears: Int = 30,
    val sex: Sex = Sex.UNSPECIFIED,
    val heightCm: Int? = null,
    val learnedRunStrideM: Double? = null,
    val learnedWalkStrideM: Double? = null,
)
