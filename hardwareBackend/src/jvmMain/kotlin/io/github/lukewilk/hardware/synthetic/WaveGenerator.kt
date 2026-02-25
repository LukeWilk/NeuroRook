package io.github.lukewilk.hardware.synthetic

import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Supported waveform types that can be synthesized by the generator.
 *
 * - SINE: continuous sine wave
 * - SQUARE: bipolar square wave derived from the sign of a sine
 * - SAWTOOTH: rising sawtooth in [-1,1]
 * - TRIANGLE: symmetric triangle wave in [-1,1]
 * - NOISE: white noise in [-1,1]
 */
enum class WaveType { SINE, SQUARE, SAWTOOTH, TRIANGLE, NOISE }

/**
 * Specification of a single waveform used by the generator.
 *
 * @property enabled whether the spec contributes to the output
 * @property type waveform shape
 * @property amplitude peak amplitude (multiplies the normalized waveform)
 * @property frequencyHz frequency in Hz
 * @property phaseShiftRad constant phase offset in radians applied on top of
 *                        any accumulated phase from previous calls
 */
data class WaveSpec(
    val enabled: Boolean = false,
    val type: WaveType = WaveType.SINE,
    val amplitude: Double = 1.0,
    val frequencyHz: Double = 1.0,
    val phaseShiftRad: Double = 0.0
)

object WaveGenerator {

    /**
     * Convenience wrapper around [generateSampleArrayWithPhases] that uses a
     * zero-initial-phase vector for all specs.
     *
     * This function synthesizes `samples` output points sampled at
     * `samplingRate` Hz by superposing all enabled [WaveSpec] entries. The
     * returned array length equals `samples` (or zero when `samples <= 0`).
     *
     * Behavior details and edge cases:
     * - If `specs` is empty, or `samples <= 0`, or `samplingRate <= 0`, an
     *   empty (zero-filled) array is returned.
     * - For `WaveType.NOISE` a new pseudorandom value is generated per sample
     *   using Kotlin's `Random`; results are not deterministic across runs
     *   unless the global RNG is seeded externally.
     * - The function returns only the synthesized sample array; it does not
     *   expose phase accumulator state. If you need phase continuity between
     *   calls, use [generateSampleArrayWithPhases] directly.
     *
     * @param specs list of wave specifications to superpose
     * @param samplingRate sampling frequency in Hz (must be > 0)
     * @param samples number of samples to produce (may be 0)
     * @return an array of length `samples` containing the synthesized signal
     */
    fun generateSampleArray(
        specs: List<WaveSpec>,
        samplingRate: Int,
        samples: Int
    ): DoubleArray {
        // wrapper: use zero initial phases
        val (arr, _) = generateSampleArrayWithPhases(specs, samplingRate, samples, List(specs.size) { 0.0 })
        return arr
    }

    /**
     * Phase-aware generator. Produces `samples` output points (superposition)
     * of the provided `specs` sampled at `samplingRate` Hz and returns a pair
     * containing the output array and the updated phase accumulators for each
     * spec.
     *
     * This function is intended for generating continuous waveforms across
     * multiple calls: supply the previously-returned `updatedPhases` as the
     * `initialPhases` argument to continue the waveform seamlessly.
     *
     * Implementation notes:
     * - `initialPhases` length should ideally match `specs.size`; the code
     *   tolerates missing entries by treating absent phases as 0.0.
     * - For each sample index i we compute time t = i / samplingRate and the
     *   instantaneous phase for each spec as:
     *       phase = 2*pi*frequencyHz * t + spec.phaseShiftRad + initialPhase
     *   then evaluate the waveform shape and scale by `amplitude`.
     * - After generating `samples` points, the returned `updatedPhases`
     *   contain the initial phase plus the phase advance over the produced
     *   duration: initial + 2*pi*frequencyHz*(samples/samplingRate).
     * - Sawtooth and triangle use the fractional part of frequency*t plus the
     *   normalized initial phase to compute their waveform value.
     * - Noise uses kotlin.random.Random for each sample; it will produce
     *   different results across runs unless Random is seeded globally.
     *
     * Edge cases:
     * - If `specs` is empty, or `samples <= 0`, or `samplingRate <= 0`, the
     *   function returns a zero-filled array and echoes back `initialPhases`.
     *
     * Thread-safety: the generator uses `Random` and mutable lists locally;
     * it is not explicitly synchronized. If multiple threads share the same
     * phase accumulators externally, synchronize callers accordingly.
     *
     * @param specs list of waveform specifications
     * @param samplingRate sampling frequency in Hz (must be > 0)
     * @param samples number of samples to generate
     * @param initialPhases per-spec initial phase in radians (accumulators);
     *                      values not provided default to 0.0
     * @return Pair where first is the synthesized DoubleArray (length =
     *         max(0, samples)) and second is the list of updated phases
     */
    fun generateSampleArrayWithPhases(
        specs: List<WaveSpec>,
        samplingRate: Int,
        samples: Int,
        initialPhases: List<Double>
    ): Pair<DoubleArray, List<Double>> {
        val out = DoubleArray(maxOf(0, samples)) { 0.0 }
        if (specs.isEmpty() || samples <= 0 || samplingRate <= 0) return Pair(out, initialPhases)
        val phases = initialPhases.toMutableList()
        for (i in 0 until samples) {
            val t = i.toDouble() / samplingRate.toDouble()
            var value = 0.0
            for ((idx, spec) in specs.withIndex()) {
                if (!spec.enabled) continue
                val w = 2.0 * PI * spec.frequencyHz
                val phase = w * t + spec.phaseShiftRad + (phases.getOrNull(idx) ?: 0.0)
                val contribution = when (spec.type) {
                    WaveType.SINE -> spec.amplitude * sin(phase)
                    WaveType.SQUARE -> spec.amplitude * if (sin(phase) >= 0.0) 1.0 else -1.0
                    WaveType.SAWTOOTH -> {
                        val frac = (spec.frequencyHz * t + (phases.getOrNull(idx) ?: 0.0) / (2.0*PI)) % 1.0
                        spec.amplitude * (2.0 * frac - 1.0)
                    }
                    WaveType.TRIANGLE -> {
                        val frac = (spec.frequencyHz * t + (phases.getOrNull(idx) ?: 0.0) / (2.0*PI)) % 1.0
                        val tri = if (frac < 0.5) 4.0 * frac - 1.0 else 3.0 - 4.0 * frac
                        spec.amplitude * tri
                    }
                    WaveType.NOISE -> spec.amplitude * (Random.nextDouble() * 2.0 - 1.0)
                }
                value += contribution
            }
            out[i] = value
        }
        // update phases: add w * (samples/samplingRate) to initial phase
        val updated = specs.mapIndexed { idx, spec ->
            val init = phases.getOrNull(idx) ?: 0.0
            init + 2.0 * PI * spec.frequencyHz * (samples.toDouble() / samplingRate.toDouble())
        }
        return Pair(out, updated)
    }
}
