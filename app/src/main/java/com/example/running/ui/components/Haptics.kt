package com.example.running.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Press feedback beyond the ripple (gym-tracker's two levels). System haptics: no permission, and the
 * phone's own touch-feedback setting turns them off.
 */
class AppHaptics(private val feedback: HapticFeedback) {
    /** Small choices: a chip, a toggle, pause/resume. */
    fun light() = feedback.performHapticFeedback(HapticFeedbackType.ContextClick)

    /** Something that starts or ends: start a run, finish it. */
    fun confirm() = feedback.performHapticFeedback(HapticFeedbackType.Confirm)
}

@Composable
fun rememberAppHaptics(): AppHaptics {
    val feedback = LocalHapticFeedback.current
    return remember(feedback) { AppHaptics(feedback) }
}
