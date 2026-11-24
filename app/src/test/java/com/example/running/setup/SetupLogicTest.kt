package com.example.running.setup

import com.example.running.core.contracts.AccuracyState
import com.example.running.core.model.RunnerProfile
import com.example.running.core.model.Sex
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ProfileFormTest {
    @Test
    fun validFormBuildsProfileAndKeepsLearnedStrides() {
        val base = RunnerProfile(learnedRunStrideM = 1.1, learnedWalkStrideM = 0.7)
        val p = ProfileForm("72,5", "34", Sex.MALE, "180").toProfile(base)!!
        assertThat(p.weightKg).isEqualTo(72.5)
        assertThat(p.ageYears).isEqualTo(34)
        assertThat(p.heightCm).isEqualTo(180)
        assertThat(p.learnedRunStrideM).isEqualTo(1.1)
    }

    @Test
    fun heightIsOptional() {
        assertThat(ProfileForm("70", "30", Sex.UNSPECIFIED, "").toProfile()!!.heightCm).isNull()
    }

    @Test
    fun outOfRangeAndGarbageAreRejectedPerField() {
        assertThat(ProfileForm("20", "30").errors).containsExactly(ProfileForm.Error.WEIGHT)
        assertThat(ProfileForm("70", "5").errors).containsExactly(ProfileForm.Error.AGE)
        assertThat(ProfileForm("70", "30", height = "50").errors).containsExactly(ProfileForm.Error.HEIGHT)
        assertThat(ProfileForm("", "").errors).containsExactly(ProfileForm.Error.WEIGHT, ProfileForm.Error.AGE)
        assertThat(ProfileForm("abc", "30").toProfile()).isNull()
    }

    @Test
    fun roundTripsThroughFormText() {
        val form = ProfileForm.from(RunnerProfile(weightKg = 68.5, ageYears = 41, sex = Sex.FEMALE, heightCm = 166))
        assertThat(form).isEqualTo(ProfileForm("68.5", "41", Sex.FEMALE, "166"))
        assertThat(ProfileForm.from(RunnerProfile(weightKg = 70.0)).weight).isEqualTo("70")
    }
}

class OnboardingFlowTest {
    private val nothing = SetupSnapshot()
    private val fine = SetupSnapshot(fineLocation = true)

    @Test
    fun orderIsFineThenBackgroundThenNotificationsThenActivityThenBattery() {
        var step = OnboardingStep.PROFILE
        val seen = mutableListOf(step)
        // Fine location is granted as soon as the LOCATION page has been shown.
        while (true) {
            val snap = if (step == OnboardingStep.PROFILE) nothing else fine
            step = OnboardingFlow.next(step, snap, AccuracyState.OFF) ?: break
            seen += step
        }
        assertThat(seen).containsExactly(
            OnboardingStep.PROFILE, OnboardingStep.LOCATION, OnboardingStep.BACKGROUND_LOCATION,
            OnboardingStep.NOTIFICATIONS, OnboardingStep.ACTIVITY_RECOGNITION, OnboardingStep.BATTERY, OnboardingStep.ACCURACY,
        ).inOrder()
    }

    @Test
    fun backgroundLocationIsNeverOfferedWithoutFineLocation() {
        assertThat(OnboardingFlow.isSatisfied(OnboardingStep.BACKGROUND_LOCATION, nothing, null)).isTrue()
        assertThat(OnboardingFlow.isSatisfied(OnboardingStep.BACKGROUND_LOCATION, fine, null)).isFalse()
        val all = fine.copy(backgroundLocation = true)
        assertThat(OnboardingFlow.isSatisfied(OnboardingStep.BACKGROUND_LOCATION, all, null)).isTrue()
    }

    @Test
    fun grantedStepsAreSkipped() {
        val s = SetupSnapshot(fineLocation = true, backgroundLocation = true, notifications = true, activityRecognition = true)
        assertThat(OnboardingFlow.next(OnboardingStep.PROFILE, s, null)).isEqualTo(OnboardingStep.BATTERY)
    }

    @Test
    fun batteryIsSatisfiedWhenExemptOrDismissed() {
        assertThat(OnboardingFlow.isSatisfied(OnboardingStep.BATTERY, nothing, null)).isFalse()
        assertThat(OnboardingFlow.isSatisfied(OnboardingStep.BATTERY, nothing.copy(batteryExempt = true), null)).isTrue()
        assertThat(OnboardingFlow.isSatisfied(OnboardingStep.BATTERY, nothing, null, batteryDismissed = true)).isTrue()
    }

    @Test
    fun accuracyStepOnlyWhenOff() {
        val s = SetupSnapshot(true, true, true, true, true)
        assertThat(OnboardingFlow.next(OnboardingStep.BATTERY, s, AccuracyState.OFF)).isEqualTo(OnboardingStep.ACCURACY)
        assertThat(OnboardingFlow.next(OnboardingStep.BATTERY, s, AccuracyState.ON)).isNull()
        assertThat(OnboardingFlow.next(OnboardingStep.BATTERY, s, AccuracyState.UNKNOWN)).isNull()
        assertThat(OnboardingFlow.next(OnboardingStep.BATTERY, s, null)).isEqualTo(OnboardingStep.ACCURACY) // not yet checked
    }
}

class DevModeTapCounterTest {
    @Test
    fun seventhTapInAStreakUnlocks() {
        val c = DevModeTapCounter()
        val left = (1..7).map { c.tap(1_000L + it * 100L) }
        assertThat(left).containsExactly(6, 5, 4, 3, 2, 1, 0).inOrder()
    }

    @Test
    fun aPauseResetsTheStreak() {
        val c = DevModeTapCounter(windowMillis = 2_000)
        repeat(5) { c.tap(it * 100L) }
        assertThat(c.tap(10_000)).isEqualTo(6)
    }
}

class BackupIntervalsTest {
    @Test
    fun labelsAndOffOption() {
        assertThat(BackupIntervals.OPTIONS.first()).isEqualTo(0)
        assertThat(BackupIntervals.label(0)).isEqualTo("Off")
        assertThat(BackupIntervals.label(168)).isEqualTo("Weekly")
        assertThat(BackupIntervals.label(5)).isEqualTo("Every 5h")
    }
}
