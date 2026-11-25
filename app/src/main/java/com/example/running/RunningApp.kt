package com.example.running

import android.app.Application
import com.example.running.core.notifications.NotificationChannels
import com.example.running.di.AppContainer

class RunningApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        NotificationChannels.ensure(this)
        // MapLibre starts lazily (first map or offline call): not for every process start, e.g. the
        // backup worker or a sticky service restart during crash recovery.
    }
}
