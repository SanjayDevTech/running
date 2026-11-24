package com.example.running.ui.navigation

import kotlinx.serialization.Serializable

@Serializable object OnboardingRoute                              // F
@Serializable object RecordRoute                                  // A  (tab 1, start destination)
@Serializable object ActivitiesRoute                              // D  (tab 2)
@Serializable object ProgressRoute                                // E  (tab 3)
@Serializable object SettingsRoute                                // F  (tab 4)
@Serializable data class RunDetailRoute(val runId: String)        // D
@Serializable object PersonalBestsRoute                           // E
@Serializable data class DevEngineCompareRoute(val runId: String) // A
@Serializable object ProfileEditRoute                             // F
@Serializable object BackupRoute                                  // F
