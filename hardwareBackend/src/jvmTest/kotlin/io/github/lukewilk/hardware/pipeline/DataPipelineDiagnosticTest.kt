package io.github.lukewilk.hardware.pipeline

import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import io.github.lukewilk.shared.HardwareState
import io.github.lukewilk.shared.StateStore
import io.github.lukewilk.shared.SyntheticMode
import io.github.lukewilk.shared.WaveSpec
import io.github.lukewilk.shared.WaveType
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.math.abs

/**
 * Diagnostic integration test to compare raw generator peak vs pipeline filtered peak for a single sine.
 */
class DataPipelineDiagnosticTest : DataPipelineTestSupport() {
    @Test
    fun `diagnose single sine generator vs filtered peaks`() = runBlocking {
        val stateStore = pipelineStateStore(windowSize = 32, overlap = 16, samplingRateHz = 250)
        val manager = io.github.lukewilk.hardware.BoardConnectionManager(stateStore)
        connectAndStartSynthetic(manager)

        // Get a raw generator block for inspection
        io.github.lukewilk.hardware.synthetic.SyntheticDataGenerator.resetPhases()
        val raw = manager.generateSyntheticData(1024)
        val genMax = raw.map { arr -> arr.maxOfOrNull { v -> abs(v) } ?: 0.0 }.maxOrNull() ?: 0.0

        var filteredMax = 0.0
        var filteredCalled = false

        val pipelineJob = launch {
            startDataPipeline(
                onFiltered = {
                    filteredCalled = true
                    val m = it.maxOfOrNull { v -> abs(v) } ?: 0.0
                    if (m > filteredMax) filteredMax = m
                    // stop after we observed some frames
                    if (filteredCalled) manager.stopStream()
                },
                stateStore = stateStore,
                manager = manager
            )
        }

        // wait for pipeline to complete
        waitUntil(timeoutMs = 5000) { pipelineJob.isCompleted }

        println("Diagnostic: generator maxAbs=$genMax, filtered maxAbs=$filteredMax")

        // Generator should respect amplitude=1.0 (allow tiny numerical tolerance)
        assertTrue(genMax <= 1.001, "Generator peak exceeded expected amplitude: $genMax")
        // Ensure we observed filtered output
        assertTrue(filteredCalled, "Expected filtered callback to be invoked")

        manager.close()
    }

    /**
     * Diagnostic integration test to verify two enabled sine waves are visible together in FFT output.
     */
    @Test
    fun `diagnose two-wave superposition appears in fft`() = runBlocking {
        val samplingRateHz = 250
        val stateStore = StateStore(
            HardwareState(
                connected = false,
                streaming = false,
                synthetic = true,
                syntheticMode = SyntheticMode.WAVE_GENERATOR,
                samplingRateHz = samplingRateHz,
                channels = 1,
                enabledChannels = listOf(0),
                windowSize = 256,
                overlap = 128,
                waveSpecs = listOf(
                    WaveSpec(enabled = true, type = WaveType.SINE, amplitude = 1.0, frequencyHz = 10.0, phaseShiftRad = 0.0, channels = listOf(0)),
                    WaveSpec(enabled = true, type = WaveType.SINE, amplitude = 0.8, frequencyHz = 20.0, phaseShiftRad = 0.0, channels = listOf(0))
                )
            )
        )
        val manager = io.github.lukewilk.hardware.BoardConnectionManager(stateStore)
        connectAndStartSynthetic(manager)

        var fftObserved = false
        var peakNear10 = 0.0
        var peakNear20 = 0.0

        val pipelineJob = launch {
            startDataPipeline(
                onFFTResult = { spectrum ->
                    fftObserved = true
                    peakNear10 = maxMagnitudeNear(spectrum, 10.0, toleranceHz = 1.0)
                    peakNear20 = maxMagnitudeNear(spectrum, 20.0, toleranceHz = 1.0)
                    if (peakNear10 > 1e-3 && peakNear20 > 1e-3) {
                        manager.stopStream()
                    }
                },
                stateStore = stateStore,
                manager = manager
            )
        }

        waitUntil(timeoutMs = 7000) { pipelineJob.isCompleted }

        println("Diagnostic two-wave FFT: peak10=$peakNear10, peak20=$peakNear20")

        assertTrue(fftObserved, "Expected FFT callback to be invoked")
        assertTrue(peakNear10 > 1e-3, "Expected a visible FFT component near 10Hz, got $peakNear10")
        assertTrue(peakNear20 > 1e-3, "Expected a visible FFT component near 20Hz, got $peakNear20")
        assertTrue(peakNear20 > peakNear10 * 0.05, "Expected second-wave FFT component to be substantial vs first wave, got peak10=$peakNear10 peak20=$peakNear20")

        manager.close()
    }

    /** Returns the largest PSD magnitude in a narrow window around the requested center frequency. */
    private fun maxMagnitudeNear(spectrum: Array<Pair<Double, Double>>, centerHz: Double, toleranceHz: Double): Double {
        return spectrum
            .asSequence()
            .filter { (frequency, _) -> abs(frequency - centerHz) <= toleranceHz }
            .map { (_, magnitude) -> magnitude }
            .maxOrNull() ?: 0.0
    }
}


