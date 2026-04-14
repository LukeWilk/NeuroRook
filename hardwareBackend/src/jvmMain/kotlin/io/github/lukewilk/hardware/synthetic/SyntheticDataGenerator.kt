package io.github.lukewilk.hardware.synthetic

import io.github.lukewilk.shared.HardwareState
import io.github.lukewilk.shared.WaveType as SharedWaveType
import co.touchlab.kermit.Logger
import io.github.lukewilk.shared.logging.LoggerProvider

object SyntheticDataGenerator {
    private val logger = LoggerProvider.getLogger("SyntheticDataGenerator")

    // keep phase accumulator per wave index for continuity across calls
    private val phaseAccumulators = mutableMapOf<Int, Double>()

    /** Reset stored phase accumulators (useful for tests). */
    fun resetPhases() {
        phaseAccumulators.clear()
        logger.i { "phase accumulators reset" }
    }

    /**
     * Generate synthetic board-shaped data (rows = channels, cols = samples) based on shared HardwareState.
     * The generated waveform is the same on all enabled channels.
     */
    fun generate(st: HardwareState, samples: Int): Array<DoubleArray> {
        val channels = st.channels
        val samplingRate = st.samplingRateHz
        val out = Array(maxOf(0, channels)) { DoubleArray(maxOf(0, samples)) }
        if (!st.synthetic) return out
        if (channels <= 0 || samplingRate <= 0 || samples <= 0) return out

        // Build initialPhases from accumulators (radians)
        val initialPhases = List(st.waveSpecs.size) { idx -> phaseAccumulators[idx] ?: 0.0 }

        // Map shared WaveSpec -> synthetic.WaveSpec (do NOT add accumulator here)
        val genSpecs = st.waveSpecs.mapIndexed { idx, s ->
            WaveSpec(
                enabled = s.enabled,
                type = when (s.type) {
                    SharedWaveType.SINE -> WaveType.SINE
                    SharedWaveType.SQUARE -> WaveType.SQUARE
                    SharedWaveType.SAWTOOTH -> WaveType.SAWTOOTH
                    SharedWaveType.TRIANGLE -> WaveType.TRIANGLE
                    SharedWaveType.NOISE -> WaveType.NOISE
                },
                amplitude = s.amplitude,
                frequencyHz = s.frequencyHz,
                phaseShiftRad = s.phaseShiftRad // base offset only
            )
        }

        val (base, updatedPhases) = WaveGenerator.generateSampleArrayWithPhases(genSpecs, samplingRate, samples, initialPhases)

        // store updated phases back (accumulators)
        updatedPhases.forEachIndexed { idx, ph -> phaseAccumulators[idx] = ph }
        logger.d { "generated ${base.size} samples for ${st.channels} channels" }

        for (ch in 0 until channels) {
            if (st.enabledChannels.contains(ch)) {
                out[ch] = base.copyOf()
            } else {
                out[ch] = DoubleArray(samples) { 0.0 }
            }
        }
        return out
    }
}
