package com.example.running.core.system

import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import java.util.concurrent.Executor

/**
 * One background thread for location and sensor callbacks, so fixes and sensor events never queue
 * behind UI work on the main thread (or wake it). Callbacks only hand events to flows; the times come
 * from the events themselves, so which thread delivers them changes nothing recorded.
 */
internal object CallbackThread {
    private val thread: HandlerThread by lazy { HandlerThread("Running-callbacks").apply { start() } }

    val looper: Looper get() = thread.looper
    val handler: Handler by lazy { Handler(looper) }
    val executor: Executor by lazy { Executor { handler.post(it) } }
}
