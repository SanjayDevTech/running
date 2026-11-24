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
        container.map.initialize()
    }
}
