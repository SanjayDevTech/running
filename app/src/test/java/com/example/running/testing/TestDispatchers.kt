package com.example.running.testing

import com.example.running.core.util.AppDispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/** Every dispatcher is the same test dispatcher, so virtual time controls everything. */
class TestAppDispatchers(val testDispatcher: TestDispatcher = StandardTestDispatcher()) : AppDispatchers {
    override val main: CoroutineDispatcher get() = testDispatcher
    override val io: CoroutineDispatcher get() = testDispatcher
    override val default: CoroutineDispatcher get() = testDispatcher
}

/** Replaces Dispatchers.Main (viewModelScope) for the duration of a test. */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(val testDispatcher: TestDispatcher = StandardTestDispatcher()) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(testDispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}
