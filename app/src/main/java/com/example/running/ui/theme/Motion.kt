package com.example.running.ui.theme

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset

/**
 * The app's motion vocabulary (docs/ui-house-rules.md §9), taken from gym-tracker's motion table:
 * quiet, short tweens on `FastOutSlowIn`, no spring overshoot, no shape-morph, no shared elements.
 * Every animation in the app uses these durations and specs; none hard-codes its own.
 *
 * Reduced motion: Compose scales every one of these by the system animator duration scale (0 =
 * "Remove animations" makes them jump to the end), so nothing here checks it by hand.
 */
object Motion {
    /** Generic state change: show/hide, crossfade, colour, size. */
    const val STATE_MS = 200

    /** Numbers and meters: the value itself interpolates (tabular figures keep digits still). */
    const val VALUE_MS = 450

    /** Selection / row tint fading in or out. */
    const val TINT_MS = 150

    /** Push / pop slide between a screen and the one it opens. */
    const val NAV_MS = 300

    /** Fade-through between peers (tabs, record phases): the old one is gone before the new one fades in. */
    const val FADE_OUT_MS = 90
    const val FADE_IN_MS = 210

    /** Run detail route draw-in; starts once the push slide has landed. */
    const val DRAW_IN_MS = 1_200
    const val DRAW_IN_DELAY_MS = NAV_MS

    /** The one easing (Material standard). */
    val Easing = FastOutSlowInEasing

    fun <T> state(): FiniteAnimationSpec<T> = tween(STATE_MS, easing = Easing)
    fun <T> value(): FiniteAnimationSpec<T> = tween(VALUE_MS, easing = Easing)
    fun <T> tint(): FiniteAnimationSpec<T> = tween(TINT_MS, easing = Easing)
    fun <T> nav(): FiniteAnimationSpec<T> = tween(NAV_MS, easing = Easing)

    /** Something joining a layout (a row, a warning, a banner): fade in while its space opens. */
    val appear: EnterTransition = fadeIn(state()) + expandVertically(state(), expandFrom = Alignment.Top)

    /** The reverse of [appear]. */
    val disappear: ExitTransition = fadeOut(state()) + shrinkVertically(state(), shrinkTowards = Alignment.Top)

    /** A badge or pill: fade + scale 0.9 → 1, no overshoot (gym's PR badge). */
    val badgeIn: EnterTransition = fadeIn(state()) + scaleIn(state(), initialScale = 0.9f)
    val badgeOut: ExitTransition = fadeOut(state())

    /** Fade-through for peers: out in [FADE_OUT_MS], then in over [FADE_IN_MS]. */
    val fadeThroughIn: EnterTransition = fadeIn(tween(FADE_IN_MS, delayMillis = FADE_OUT_MS, easing = Easing))
    val fadeThroughOut: ExitTransition = fadeOut(tween(FADE_OUT_MS, easing = Easing))

    /** AnimatedContent: one value replacing another in place (a label, a number, a button set). */
    fun crossfade(): ContentTransform = fadeIn(state()) togetherWith fadeOut(state())

    /** AnimatedContent: one screen-sized phase replacing another (loading → content, ready → recording). */
    fun fadeThrough(): ContentTransform = fadeThroughIn togetherWith fadeThroughOut
}

/** Lazy list rows: fade in/out on insert and delete, slide into place when neighbours change. */
fun LazyItemScope.listItemMotion(modifier: Modifier = Modifier): Modifier = modifier.animateItem(
    fadeInSpec = Motion.state<Float>(),
    placementSpec = Motion.state<IntOffset>(),
    fadeOutSpec = Motion.state<Float>(),
)
