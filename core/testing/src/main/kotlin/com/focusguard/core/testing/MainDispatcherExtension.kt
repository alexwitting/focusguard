package com.focusguard.core.testing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

/**
 * Swaps `Dispatchers.Main` for a [TestDispatcher] around each test, required by anything using
 * `viewModelScope.launch`. Register with `@JvmField @RegisterExtension val mainDispatcherExtension = MainDispatcherExtension()`.
 *
 * Defaults to [UnconfinedTestDispatcher] (not [kotlinx.coroutines.test.StandardTestDispatcher])
 * so `viewModelScope.launch { ... }` bodies run eagerly on the calling thread instead of needing
 * a shared/advanced scheduler with the test's own `runTest` coroutine.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherExtension(
    private val dispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : BeforeEachCallback,
    AfterEachCallback {
    override fun beforeEach(context: ExtensionContext) {
        Dispatchers.setMain(dispatcher)
    }

    override fun afterEach(context: ExtensionContext) {
        Dispatchers.resetMain()
    }
}
