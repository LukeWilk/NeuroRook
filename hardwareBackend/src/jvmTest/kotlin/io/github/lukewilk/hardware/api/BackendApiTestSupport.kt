package io.github.lukewilk.hardware.api

import brainflow.BoardIds
import brainflow.BoardShim
import io.github.lukewilk.hardware.BoardConnectionManager
import io.github.lukewilk.shared.HardwareState
import io.github.lukewilk.shared.StateStore
import io.github.lukewilk.shared.WaveSpec
import io.github.lukewilk.shared.WaveType
import io.github.lukewilk.shared.model.SystemLogSeverity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before

/**
 * Shared fixture and reflection helpers for `HardwareBackendApi` unit tests.
 */
abstract class BackendApiTestSupport {
    protected lateinit var api: HardwareBackendApi

    /** Creates a fresh API instance for each test so lifecycle assertions remain isolated. */
    @Before
    fun createApi() {
        api = HardwareBackendApi()
    }

    /** Cleans up any active streaming session so later tests start from a predictable state. */
    @After
    fun cleanupApi() {
        runBlocking {
            kotlin.runCatching { api.stopStreaming() }
            kotlin.runCatching { api.disconnect() }
        }
    }

    /** Reaches the private `stateStore` for state-seeding tests that exercise fallback branches. */
    @Suppress("UNCHECKED_CAST")
    protected fun stateStore(): StateStore<HardwareState> {
        val field = HardwareBackendApi::class.java.getDeclaredField("stateStore").apply { isAccessible = true }
        return field.get(api) as StateStore<HardwareState>
    }

    /** Reaches the private `manager` so unit tests can inject mocked BoardShim instances. */
    protected fun manager(): BoardConnectionManager {
        val field = HardwareBackendApi::class.java.getDeclaredField("manager").apply { isAccessible = true }
        return field.get(api) as BoardConnectionManager
    }

    /** Seeds the cached board id so log-format branches can be asserted directly. */
    protected fun setConnectedBoardId(boardId: BoardIds?) {
        HardwareBackendApi::class.java.getDeclaredField("connectedBoardId").apply {
            isAccessible = true
            set(api, boardId)
        }
    }

    /** Seeds the cached streaming job so already-streaming branches can be asserted directly. */
    protected fun setStreamingJob(job: Job?) {
        HardwareBackendApi::class.java.getDeclaredField("streamingJob").apply {
            isAccessible = true
            set(api, job)
        }
    }

    /** Injects a BoardShim into the internal manager to force explicit start/stop failure branches. */
    protected fun setManagerBoardShim(boardShim: BoardShim) {
        BoardConnectionManager::class.java.getDeclaredField("boardShim").apply {
            isAccessible = true
            set(manager(), boardShim)
        }
    }

    /** Supplies the standard synthetic wave used by most streaming tests. */
    protected fun standardWave(): WaveSpec = WaveSpec(
        enabled = true,
        type = WaveType.SINE,
        amplitude = 1.0,
        frequencyHz = 10.0,
        phaseShiftRad = 0.0
    )

    /** Prepares the minimal synthetic-board configuration required for the pipeline to emit data. */
    protected suspend fun prepareSyntheticStreamingScenario() {
        api.connect("SYNTHETIC_BOARD")
        api.enableChannel(0)
        api.addWave(standardWave())
        api.setSamplingRateHz(250)
    }

    /** Waits for an eventually consistent condition so asynchronous listener tests can stay compact and reusable. */
    protected suspend fun awaitCondition(
        timeoutMillis: Long = 2_000,
        pollIntervalMillis: Long = 50,
        condition: () -> Boolean
    ): Boolean {
        val startedAt = System.currentTimeMillis()
        while (!condition() && System.currentTimeMillis() - startedAt < timeoutMillis) {
            delay(pollIntervalMillis)
        }
        return condition()
    }

    /** Asserts that a matching system log entry was appended for the behavior under test. */
    protected fun assertSystemLogContains(severity: SystemLogSeverity, fragment: String) {
        kotlin.test.assertTrue(
            api.systemLogFlow.value.any { it.severity == severity && it.message.contains(fragment) },
            "Expected a $severity log containing '$fragment'"
        )
    }
}
