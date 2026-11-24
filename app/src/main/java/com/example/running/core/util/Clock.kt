package com.example.running.core.util

import android.os.SystemClock
import java.time.ZoneId

interface Clock {
    /** Wall clock, epoch ms. */
    fun nowMillis(): Long

    /** Monotonic (android.os.SystemClock in RealClock). */
    fun elapsedRealtimeNanos(): Long

    fun zoneId(): ZoneId
}

/** The only place the real time is read. */
object RealClock : Clock {
    override fun nowMillis(): Long = System.currentTimeMillis()
    override fun elapsedRealtimeNanos(): Long = SystemClock.elapsedRealtimeNanos()
    override fun zoneId(): ZoneId = ZoneId.systemDefault()
}
