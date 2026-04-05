/**
 * Main entry point for running the hardware backend in development/testing mode.
 *
 * Usage:
 *   - By default, launches with a synthetic board generating a 10 Hz sine wave on channel 0.
 *   - Logs filtered signals, FFT results, and band powers for inspection.
 *   - Used for backend development, debugging, and pipeline validation.
 *
 * Example:
 *   ./gradlew :hardwareBackendRunner:run --args=SYNTHETIC_BOARD
 *
 */

package io.github.lukewilk.hardwareRunner

import io.github.lukewilk.hardware.main
import io.github.lukewilk.hardware.BoardConnectionManager
import io.github.lukewilk.hardware.LoggerProvider
import io.github.lukewilk.shared.model.BandPower
import io.github.lukewilk.shared.StateStore
import io.github.lukewilk.shared.HardwareState
import kotlinx.coroutines.runBlocking

fun main(args: Array<String>): Unit {
    // Set up state store and connection manager
    val stateStore = StateStore(HardwareState())
    val manager = BoardConnectionManager(stateStore)
    // Configure synthetic board state for nonzero data
    stateStore.update { st ->
        st.copy(
            channels = 1,
            enabledChannels = listOf(0),
            syntheticMode = io.github.lukewilk.shared.SyntheticMode.WAVE_GENERATOR,
            waveSpecs = st.waveSpecs.toMutableList().apply {
                this[0] = this[0].copy(enabled = true, type = io.github.lukewilk.shared.WaveType.SINE, amplitude = 1.0, frequencyHz = 10.0, phaseShiftRad = 0.0)
            }
        )
    }
    // Connect first
    val boardId = brainflow.BoardIds.values().find {
        it.name.equals(args.getOrNull(0), ignoreCase = true)
    } ?: brainflow.BoardIds.NO_BOARD
    val serialPort = args.getOrNull(1) ?: ""
    manager.connect(boardId, serialPort)
    manager.startStream()
    val logger = LoggerProvider.getLogger("hardwareRunner.Main")
    manager.enableChannel(0)
    // Now start the main pipeline with the same stateStore and manager
    runBlocking {
        main(
            _args = args,
            onFiltered = { filtered: DoubleArray ->
                logger.v { "[Filtered] ${filtered.joinToString(", ", limit = 10, truncated = "...")}" }
                if (filtered.any { it != 0.0 }) {
                    logger.i { "[Filtered-NonZero] min=${filtered.minOrNull()}, max=${filtered.maxOrNull()}" }
                } else {
                    logger.w { "[Filtered] All zeroes!" }
                }
            },
            onFFTResult = { fftResult: Array<Pair<Double, Double>> ->
                val mags = fftResult.map { it.second }
                logger.v { "[FFT] ${fftResult.take(10).joinToString(", ") { pair -> "(${"%.3f".format(pair.first)}, ${"%.3f".format(pair.second)})" }}..." }
                if (mags.any { it != 0.0 }) {
                    logger.i { "[FFT-NonZero] min=${mags.minOrNull()}, max=${mags.maxOrNull()}" }
                } else {
                    logger.w { "[FFT] All zeroes!" }
                }
            },
            onBandPowers = { bandPowers: List<BandPower> ->
                logger.i { "[BandPowers] ${bandPowers.joinToString(", ") { bp -> "${bp.name}: ${"%.3f".format(bp.power)}" }}" }
                if (bandPowers.any { it.power != 0.0 }) {
                    logger.i { "[BandPowers-NonZero] min=${bandPowers.minOf { it.power }}, max=${bandPowers.maxOf { it.power }}" }
                } else {
                    logger.w { "[BandPowers] All zeroes!" }
                }
            },
            stateStore = stateStore,
            manager = manager
        )
    }
    manager.stopStream()
    manager.close()
}
