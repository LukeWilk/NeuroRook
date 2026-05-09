package io.github.lukewilk.hardware.synthetic

import io.github.lukewilk.shared.HardwareState
import io.github.lukewilk.shared.WaveSpec as SharedWaveSpec
import io.github.lukewilk.shared.WaveType as SharedWaveType
import co.touchlab.kermit.Logger
import io.github.lukewilk.shared.logging.LoggerProvider

object SyntheticDataGenerator {
    internal var loggerFactory: () -> Logger = { LoggerProvider.getLogger("SyntheticDataGenerator") }
    private val logger: Logger
        get() = loggerFactory()

    /** Keys stored phase continuity by synthetic signal configuration to avoid cross-talk between unrelated generators. */
    private data class PhaseContextKey(
        val samplingRateHz: Int,
        // A normalized signature of wave specs that ignores runtime-only fields like `enabled`
        // and `channels` so that adding/disabling a spec does not reset phase continuity for
        // otherwise-identical waveform configurations.
        val waveSpecSignatures: List<Triple<SharedWaveType, Double, Double>>
    )

    // keep phase accumulator per normalized wave-signature so that the same waveform
    // (type/amplitude/frequency) shares continuity even when the surrounding spec list
    // or targeting changes. This avoids resetting phases when a disabled spec is
    // appended or when channels change.
    private val phaseAccumulatorsBySignature = mutableMapOf<Triple<SharedWaveType, Double, Double>, Double>()

    /** Reset stored phase accumulators (useful for tests). */
    @Synchronized
    fun resetPhases() {
        phaseAccumulatorsBySignature.clear()
        logger.i { "phase accumulators reset" }
    }

    /**
     * Generate synthetic board-shaped data (rows = channels, cols = samples) based on shared HardwareState.
     * The generated waveform is the same on all enabled channels.
     */
    @Synchronized
    fun generate(st: HardwareState, samples: Int): Array<DoubleArray> {
        val channels = st.channels
        val samplingRate = st.samplingRateHz
        val out = Array(maxOf(0, channels)) { DoubleArray(maxOf(0, samples)) }
        if (!st.synthetic) return out
        if (channels <= 0 || samplingRate <= 0 || samples <= 0) return out

        // Build a normalized signature for the wave specs to use as a phase-continuity key.
        val normalizedSignatures = st.waveSpecs.map { s -> Triple(s.type, s.amplitude, s.frequencyHz) }
        val phaseContextKey = PhaseContextKey(
            samplingRateHz = samplingRate,
            waveSpecSignatures = normalizedSignatures
        )
        // Build initialPhases from signature-keyed accumulators (radians)
        val initialPhases = List(st.waveSpecs.size) { idx -> phaseAccumulatorsBySignature[normalizedSignatures.getOrNull(idx)] ?: 0.0 }

        // Map shared WaveSpec -> synthetic.WaveSpec (do NOT add accumulator here)
        val genSpecs = st.waveSpecs.map { s ->
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

        // Prepare per-spec channel targeting information (shared WaveSpec.channels)
        val specChannels: List<List<Int>> = st.waveSpecs.map { it.channels }

        // Generate per-spec contribution arrays so we can apply per-wave channel targeting.
        val contributions: MutableList<DoubleArray> = mutableListOf()
        val updatedPhases: MutableList<Double> = mutableListOf()
        for ((idx, spec) in genSpecs.withIndex()) {
            val init = initialPhases.getOrNull(idx) ?: 0.0
            val (arr, updated) = WaveGenerator.generateSampleArrayWithPhases(listOf(spec), samplingRate, samples, listOf(init))
            contributions.add(arr)
            // updated is a list of one element when we generated a single-spec array
            updatedPhases.add(updated.getOrNull(0) ?: init)
        }

        // store updated phases back (signature-keyed accumulators)
        updatedPhases.forEachIndexed { idx, ph ->
            val sig = normalizedSignatures.getOrNull(idx) ?: return@forEachIndexed
            phaseAccumulatorsBySignature[sig] = ph
        }
        logger.d { "generated $samples samples for ${st.channels} channels (per-spec contributions=${contributions.size})" }

        // Build per-channel output by summing only specs that target each channel.
        for (ch in 0 until channels) {
            // Determine which specs apply to this channel:
            // - If a spec explicitly targets channels (non-empty list), it applies only to those channels.
            // - Otherwise it applies to channels listed in st.enabledChannels.
            val applicableSpecIndices = genSpecs.indices.filter { idx ->
                val targets = specChannels.getOrNull(idx) ?: emptyList()
                if (targets.isNotEmpty()) {
                    targets.contains(ch)
                } else {
                    st.enabledChannels.contains(ch)
                }
            }

            if (applicableSpecIndices.isEmpty()) {
                out[ch] = DoubleArray(samples) { 0.0 }
            } else {
                val arr = DoubleArray(samples) { 0.0 }
                for (i in 0 until samples) {
                    var v = 0.0
                    for (si in applicableSpecIndices) v += contributions[si][i]
                    arr[i] = v
                }
                out[ch] = arr
            }
        }
        return out
    }
}
