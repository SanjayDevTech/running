package com.example.running.analysis

import com.example.running.core.model.RunnerProfile
import com.example.running.core.model.SegmentKind
import com.example.running.core.model.Sex

internal enum class Gait { RUN, WALK }

internal fun Gait.toSegmentKind(): SegmentKind = if (this == Gait.RUN) SegmentKind.RUN else SegmentKind.WALK

/** Stride length (metres per step) seeded from height and learned from good-GPS stretches. */
internal object StrideModel {
    fun seed(profile: RunnerProfile, gait: Gait): Double {
        val h = (profile.heightCm ?: AnalysisTuning.DEFAULT_HEIGHT_CM) / 100.0
        return h * if (gait == Gait.RUN) AnalysisTuning.SEED_RUN_STRIDE_PER_HEIGHT else AnalysisTuning.SEED_WALK_STRIDE_PER_HEIGHT
    }

    /** The stride to use before this run measured anything: learned value, else the height seed. */
    fun prior(profile: RunnerProfile, gait: Gait): Double =
        (if (gait == Gait.RUN) profile.learnedRunStrideM else profile.learnedWalkStrideM) ?: seed(profile, gait)

    fun plausible(gait: Gait, strideM: Double): Boolean =
        strideM in (if (gait == Gait.RUN) AnalysisTuning.RUN_STRIDE_RANGE else AnalysisTuning.WALK_STRIDE_RANGE)

    /** Measured stride from [distanceM] over [steps], or null when there is too little data or it is implausible. */
    fun measure(gait: Gait, distanceM: Double, steps: Double, minSteps: Double = AnalysisTuning.STRIDE_MIN_STEPS): Double? {
        if (steps < minSteps || distanceM <= 0.0) return null
        val s = distanceM / steps
        return s.takeIf { plausible(gait, it) }
    }

    /**
     * New learned stride: blend of the stored one with this run's measurement. Keeps the stored
     * value when nothing was measured, so callers can always write the pair back.
     */
    fun learn(stored: Double?, measured: Double?): Double? = when {
        measured == null -> stored
        stored == null -> measured
        else -> stored * (1 - AnalysisTuning.STRIDE_LEARN_RATE) + measured * AnalysisTuning.STRIDE_LEARN_RATE
    }
}

/** Walk vs run from cadence (preferred) and speed. */
internal object GaitClassifier {
    fun classify(cadenceSpm: Double?, speedMps: Double): Gait {
        if (cadenceSpm != null && cadenceSpm >= AnalysisTuning.STILL_CADENCE_SPM) {
            if (cadenceSpm >= AnalysisTuning.RUN_CADENCE_SPM) return Gait.RUN
            if (cadenceSpm < AnalysisTuning.WALK_CADENCE_SPM) return Gait.WALK
        }
        return if (speedMps >= AnalysisTuning.RUN_SPEED_MPS) Gait.RUN else Gait.WALK
    }
}

/**
 * Energy: ACSM metabolic equations for the activity cost (running 0.2, walking 0.1 ml O2/kg/m, plus
 * the uphill grade term; downhill earns nothing) and Mifflin–St Jeor BMR for the resting part during
 * moving time, so weight, age, sex (and height when known) all count. 5 kcal per litre of O2.
 */
internal object Calories {
    private const val KCAL_PER_LITRE_O2 = 5.0

    fun bmrKcalPerDay(profile: RunnerProfile): Double {
        val h = (profile.heightCm ?: AnalysisTuning.DEFAULT_HEIGHT_CM).toDouble()
        val sexTerm = when (profile.sex) {
            Sex.MALE -> 5.0
            Sex.FEMALE -> -161.0
            Sex.UNSPECIFIED -> -78.0
        }
        return (10 * profile.weightKg + 6.25 * h - 5 * profile.ageYears + sexTerm).coerceAtLeast(800.0)
    }

    /** Net activity kcal for covering [distanceM] with [climbM] of ascent in [gait]. */
    fun activityKcal(profile: RunnerProfile, gait: Gait, distanceM: Double, climbM: Double): Double {
        if (distanceM <= 0) return 0.0
        val horizontal = if (gait == Gait.RUN) 0.2 else 0.1
        return horizontal * distanceM * profile.weightKg / 1000.0 * KCAL_PER_LITRE_O2 +
            climbKcal(profile, gait, climbM.coerceIn(0.0, distanceM * 0.3))
    }

    /** Vertical (uphill) term alone. */
    fun climbKcal(profile: RunnerProfile, gait: Gait, climbM: Double): Double {
        if (climbM <= 0) return 0.0
        val vertical = if (gait == Gait.RUN) 0.9 else 1.8
        return vertical * climbM * profile.weightKg / 1000.0 * KCAL_PER_LITRE_O2
    }

    fun restingKcal(profile: RunnerProfile, movingMillis: Long): Double =
        bmrKcalPerDay(profile) / 1_440.0 * (movingMillis / 60_000.0)
}
