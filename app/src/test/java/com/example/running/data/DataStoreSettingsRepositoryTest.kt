package com.example.running.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.example.running.core.model.AppSettings
import com.example.running.core.model.LocationEngineMode
import com.example.running.core.model.RunnerProfile
import com.example.running.core.model.Sex
import com.example.running.data.settings.DataStoreSettingsRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class DataStoreSettingsRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun defaultsAndWrites() = runTest(UnconfinedTestDispatcher()) {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val store = PreferenceDataStoreFactory.create(scope = scope.backgroundScope) {
            File(tmp.root, "settings.preferences_pb")
        }
        val repo = DataStoreSettingsRepository(store)
        assertThat(repo.current()).isEqualTo(AppSettings())

        repo.setOnboardingComplete(true)
        repo.updateProfile(RunnerProfile(weightKg = 64.5, ageYears = 41, sex = Sex.FEMALE, heightCm = 168, learnedRunStrideM = 9.9))
        repo.setLearnedStrides(1.05, null)
        repo.setLearnedStrides(null, 0.72)
        repo.setEngineMode(LocationEngineMode.BUILT_IN)
        repo.setDevModeUnlocked(true)
        repo.setDevDualEngine(true)
        repo.setBackupFolderUri("content://tree/x")
        repo.setBackupIntervalHours(24)
        repo.setLastBackupAt(123L)
        repo.setBatteryPromptDismissed(true)

        val s = repo.current()
        assertThat(s.onboardingComplete).isTrue()
        assertThat(s.profile).isEqualTo(
            RunnerProfile(weightKg = 64.5, ageYears = 41, sex = Sex.FEMALE, heightCm = 168, learnedRunStrideM = 1.05, learnedWalkStrideM = 0.72)
        )
        assertThat(s.engineMode).isEqualTo(LocationEngineMode.BUILT_IN)
        assertThat(s.devModeUnlocked && s.devDualEngine && s.batteryPromptDismissed).isTrue()
        assertThat(s.backup.folderUri).isEqualTo("content://tree/x")
        assertThat(s.backup.intervalHours).isEqualTo(24)
        assertThat(s.backup.lastBackupAtMillis).isEqualTo(123L)

        repo.updateProfile(s.profile.copy(heightCm = null))
        repo.setBackupFolderUri(null)
        assertThat(repo.current().profile.heightCm).isNull()
        assertThat(repo.current().backup.folderUri).isNull()
    }
}
