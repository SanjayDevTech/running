package com.example.running.setup

import com.example.running.core.model.RunnerProfile
import com.example.running.core.model.Sex

/** Text-field state for the profile (onboarding and Settings share it). Parsing and ranges live here. */
internal data class ProfileForm(
    val weight: String = "",
    val age: String = "",
    val sex: Sex = Sex.UNSPECIFIED,
    val height: String = "",
) {
    enum class Error { WEIGHT, AGE, HEIGHT }

    val weightKg: Double? get() = weight.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it in WEIGHT_RANGE }
    val ageYears: Int? get() = age.trim().toIntOrNull()?.takeIf { it in AGE_RANGE }

    /** Height is optional: blank is valid (null); anything else must parse and be in range. */
    private val heightValid: Boolean get() = height.isBlank() || height.trim().toIntOrNull()?.let { it in HEIGHT_RANGE } == true

    val errors: Set<Error>
        get() = buildSet {
            if (weightKg == null) add(Error.WEIGHT)
            if (ageYears == null) add(Error.AGE)
            if (!heightValid) add(Error.HEIGHT)
        }

    val isValid: Boolean get() = errors.isEmpty()

    /** Merges into [base] so learned strides survive; null when invalid. */
    fun toProfile(base: RunnerProfile = RunnerProfile()): RunnerProfile? {
        if (!isValid) return null
        return base.copy(
            weightKg = weightKg!!, ageYears = ageYears!!, sex = sex,
            heightCm = height.trim().toIntOrNull(),
        )
    }

    companion object {
        val WEIGHT_RANGE = 30.0..250.0
        val AGE_RANGE = 10..100
        val HEIGHT_RANGE = 100..250

        fun from(profile: RunnerProfile) = ProfileForm(
            weight = formatWeight(profile.weightKg),
            age = profile.ageYears.toString(),
            sex = profile.sex,
            height = profile.heightCm?.toString().orEmpty(),
        )

        private fun formatWeight(kg: Double): String =
            if (kg == Math.floor(kg)) kg.toInt().toString() else "%.1f".format(java.util.Locale.US, kg)
    }
}
