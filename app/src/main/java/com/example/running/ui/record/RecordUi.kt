package com.example.running.ui.record

import com.example.running.core.model.GpsQuality
import com.example.running.core.model.GpsStatus

/** What the pre-start GPS line says. */
internal enum class GpsHint {
    /** No fix yet. */
    ACQUIRING,

    /** Good fix. */
    READY,

    /** A fix, but worse than GOOD: start still allowed, distance may be less accurate. */
    WEAK,
}

internal fun gpsHint(gps: GpsStatus): GpsHint = when {
    gps.quality == GpsQuality.NONE -> GpsHint.ACQUIRING
    gps.weakSignalWarning -> GpsHint.WEAK
    else -> GpsHint.READY
}

/** What keeps the Record screen from using GPS, shown as a banner over the map. */
internal enum class LocationBlock {
    /** Fine location not granted. */
    PERMISSION,

    /** System location switched off. */
    LOCATION_OFF,
}

/** Permission first: with it missing, the on/off switch cannot help yet. */
internal fun locationBlock(hasPermission: Boolean, locationOn: Boolean): LocationBlock? = when {
    !hasPermission -> LocationBlock.PERMISSION
    !locationOn -> LocationBlock.LOCATION_OFF
    else -> null
}

/**
 * Start stays tappable while location is blocked: the tap asks for the permission or shows the
 * turn-on dialog, then continues to the start. Otherwise Start is always enabled (never GPS-gated).
 */
internal fun startEnabled(block: LocationBlock?, startPending: Boolean): Boolean =
    block == null || !startPending

/** A Start tapped while blocked goes through as soon as warm-up is running. */
internal fun shouldAutoStart(startPending: Boolean, block: LocationBlock?, gps: GpsStatus?): Boolean =
    startPending && block == null && gps != null
