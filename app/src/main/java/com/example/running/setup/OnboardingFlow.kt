package com.example.running.setup

import com.example.running.core.contracts.AccuracyState

/** Onboarding pages in order (architecture.md §3.8). */
internal enum class OnboardingStep { PROFILE, LOCATION, BACKGROUND_LOCATION, NOTIFICATIONS, ACTIVITY_RECOGNITION, BATTERY, ACCURACY }

/** Pure step-order rules: which pages still need showing given what is already granted. */
internal object OnboardingFlow {
    /** True when [step] has nothing left to ask. [accuracy] null means not checked yet. */
    fun isSatisfied(step: OnboardingStep, s: SetupSnapshot, accuracy: AccuracyState?, batteryDismissed: Boolean = false): Boolean =
        when (step) {
            OnboardingStep.PROFILE -> false
            OnboardingStep.LOCATION -> s.fineLocation
            // Background location is only ever offered once fine location is granted.
            OnboardingStep.BACKGROUND_LOCATION -> !s.canRequestBackground
            OnboardingStep.NOTIFICATIONS -> s.notifications
            OnboardingStep.ACTIVITY_RECOGNITION -> s.activityRecognition
            OnboardingStep.BATTERY -> s.batteryExempt || batteryDismissed
            OnboardingStep.ACCURACY -> accuracy != null && accuracy != AccuracyState.OFF
        }

    /** The first step after [from] that still needs the user, or null when onboarding is done. */
    fun next(from: OnboardingStep, s: SetupSnapshot, accuracy: AccuracyState?, batteryDismissed: Boolean = false): OnboardingStep? =
        OnboardingStep.entries.drop(from.ordinal + 1)
            .firstOrNull { !isSatisfied(it, s, accuracy, batteryDismissed) }
}

/** Counts taps on the version row; unlocks developer mode on the [REQUIRED]th tap within a streak. */
internal class DevModeTapCounter(private val windowMillis: Long = 2_000L) {
    private var count = 0
    private var lastTap = Long.MIN_VALUE

    /** Registers a tap; returns taps still needed (0 = unlock now). */
    fun tap(nowMillis: Long): Int {
        count = if (lastTap != Long.MIN_VALUE && nowMillis - lastTap > windowMillis) 1 else count + 1
        lastTap = nowMillis
        val left = (REQUIRED - count).coerceAtLeast(0)
        if (left == 0) count = 0
        return left
    }

    companion object {
        const val REQUIRED = 7
    }
}

/** Backup schedule choices shown in the UI; hours 0 = off. */
internal object BackupIntervals {
    val OPTIONS = listOf(0, 24, 72, 168)

    fun label(hours: Int): String = when (hours) {
        0 -> "Off"
        24 -> "Daily"
        72 -> "Every 3 days"
        168 -> "Weekly"
        else -> "Every ${hours}h"
    }
}
