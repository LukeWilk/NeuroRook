package io.github.lukewilk.hardware.pipeline

import brainflow.BoardIds
import io.github.lukewilk.hardware.BoardConnectionManager
import io.github.lukewilk.shared.Band
import io.github.lukewilk.shared.BandpassConfig
import io.github.lukewilk.shared.BandstopConfig
import io.github.lukewilk.shared.FilterConfig
import io.github.lukewilk.shared.HardwareState
import io.github.lukewilk.shared.StateStore
import io.github.lukewilk.shared.WaveSpec
import io.github.lukewilk.shared.WaveType
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlin.test.assertTrue

/**
 * Shared fixtures and polling helpers for `DataPipeline` JVM suites.
 */
abstract class DataPipelineTestSupport {

    /** Builds a synthetic pipeline state with sensible defaults and optional bands/filters. */
    protected fun pipelineStateStore(
        windowSize: Int = 32,
        overlap: Int = 16,
        samplingRateHz: Int = 250,
        bands: List<Band> = emptyList(),
        filterConfig: FilterConfig = FilterConfig(bandpass = null, bandstopFilters = emptyList())
    ): StateStore<HardwareState> = StateStore(
        HardwareState(
            windowSize = windowSize,
            overlap = overlap,
            samplingRateHz = samplingRateHz,
            channels = 1,
            enabledChannels = listOf(0),
            bands = bands,
            filterConfig = filterConfig,
            waveSpecs = listOf(
                WaveSpec(
                    enabled = true,
                    type = WaveType.SINE,
                    amplitude = 1.0,
                    frequencyHz = 10.0,
                    phaseShiftRad = 0.0
                )
            )
        )
    )

    /** Creates the filter-heavy state used by the full-callback pipeline scenario. */
    protected fun filteredPipelineStateStore(): StateStore<HardwareState> = pipelineStateStore(
        bands = listOf(Band("Alpha", 8.0, 12.0)),
        filterConfig = FilterConfig(
            bandpass = BandpassConfig(
                lowCut = 6.0,
                highCut = 30.0,
                order = 2,
                samplingRate = 250,
                filterType = 0,
                ripple = 0.1
            ),
            bandstopFilters = listOf(
                BandstopConfig(
                    startFreq = 49.0,
                    stopFreq = 51.0,
                    order = 0,
                    samplingRate = 250,
                    filterType = 0,
                    ripple = 0.1
                )
            )
        )
    )

    /** Connects and starts a synthetic board so pipeline tests can focus on callbacks and cleanup logic. */
    protected fun connectAndStartSynthetic(manager: BoardConnectionManager) {
        assertTrue(manager.connect(BoardIds.SYNTHETIC_BOARD, serialPort = ""))
        manager.startStream()
    }

    /** Polls until a condition becomes true, keeping callback-oriented tests deterministic. */
    protected suspend fun waitUntil(timeoutMs: Long = 3_000, pollMs: Long = 25, condition: () -> Boolean) {
        withTimeout(timeoutMs) {
            while (!condition()) {
                delay(pollMs)
            }
        }
    }
}
