package com.example.running.di

import android.app.Application
import com.example.running.core.util.AppDispatchers
import com.example.running.core.util.Clock
import com.example.running.data.db.RunningDatabase
import com.example.running.data.repo.RunRepository
import com.example.running.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope

/** Everything a track module may depend on. Built once by AppContainer. */
class CoreDeps(
    val app: Application,
    val clock: Clock,
    val dispatchers: AppDispatchers,
    /** SupervisorJob + dispatchers.default; lives as long as the process. */
    val appScope: CoroutineScope,
    val database: RunningDatabase,
    val runRepository: RunRepository,
    val settings: SettingsRepository,
)
