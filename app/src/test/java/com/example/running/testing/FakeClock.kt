package com.example.running.testing

import com.example.running.core.util.Clock
import java.time.ZoneId

/** Manually advanced clock. elapsedRealtimeNanos starts at 0 and moves with [advanceBy]. */
class FakeClock(
    startMillis: Long = 1_764_040_800_000L,
    private val zone: ZoneId = ZoneId.of("Asia/Kolkata"),
) : Clock {
    var millis: Long = startMillis
        private set
    private var elapsedNanos: Long = 0

    override fun nowMillis(): Long = millis
    override fun elapsedRealtimeNanos(): Long = elapsedNanos
    override fun zoneId(): ZoneId = zone

    fun advanceBy(ms: Long) {
        millis += ms
        elapsedNanos += ms * 1_000_000L
    }

    fun setNow(ms: Long) {
        advanceBy(ms - millis)
    }
}
