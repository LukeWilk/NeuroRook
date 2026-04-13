package io.github.lukewilk.hardware.pipeline

import io.github.lukewilk.hardware.BoardConnectionManager
import io.github.lukewilk.shared.HardwareState
import io.github.lukewilk.shared.StateStore
import io.github.lukewilk.shared.logging.LoggerProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest

/**
 * Shared fixtures and cleanup helpers for `DataAcquisition` JVM suites.
 */
abstract class DataAcquisitionTestSupport {
    protected val logger = LoggerProvider.getLogger("DataAcquisitionTest")
    protected val stateStore = StateStore(HardwareState())
    protected val manager = BoardConnectionManager(stateStore = stateStore)
    protected val acquisition = DataAcquisition(manager)

    /** Releases any shared BrainFlow session state between tests so later runs stay deterministic. */
    @AfterTest
    fun cleanup() {
        runBlocking {
            manager.close()
            delay(200)
        }
    }
}
