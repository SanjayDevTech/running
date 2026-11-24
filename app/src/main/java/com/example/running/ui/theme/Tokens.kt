package com.example.running.ui.theme

import androidx.compose.ui.unit.dp

/** The only spacing values. Raw .dp is for sizes, never gaps. */
object Spacing {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp
    val gutter = 16.dp
}

object Radius {
    val sm = 10.dp
    val md = 16.dp
    val lg = 24.dp
    val sheet = 28.dp
    val pill = 999.dp
}

/** Minimums: use heightIn(min = …), never height(…). */
object Sizes {
    val touchTarget = 48.dp
    val button = 52.dp
    val recordButton = 88.dp
    val controlButton = 64.dp
    val chartHeight = 180.dp
    /** Route map on Run detail (and the dev engine comparison). */
    val mapHeight = 260.dp
    /** Route thumbnail on an Activities card. */
    val thumbnailHeight = 120.dp
}

object Elevation {
    val none = 0.dp
}
