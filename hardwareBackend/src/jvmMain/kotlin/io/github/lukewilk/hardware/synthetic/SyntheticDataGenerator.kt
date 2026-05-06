package io.github.lukewilk.hardware.synthetic

import io.github.lukewilk.shared.HardwareState
import io.github.lukewilk.shared.WaveType as SharedWaveType
import co.touchlab.kermit.Logger
import io.github.lukewilk.shared.logging.LoggerProvider
import kotlin.math.min

object SyntheticDataGenerator {
    internal var loggerFactory: () -> Logger = { LoggerProvider.getLogger("SyntheticDataGenerator") }
    private val logger: Logger
        get() = loggerFactory()

    /** Phase continuity depends on the waveform definition, not on whether a slot is currently toggled on. */
    private data class PhaseWaveSpecKey(
        val type: SharedWaveType,
        val frequencyHz: Double,
        val phaseShiftRad: Double
    )

    /** Keys stored phase continuity by synthetic signal configuration to avoid cross-talk between unrelated generators. */
    private data class PhaseContextKey(
        val samplingRateHz: Int,
        val waveSpecs: List<PhaseWaveSpecKey>
    )

    // keep phase accumulator per wave index and per signal configuration for continuity across calls
    private val phaseAccumulatorsByContext = mutableMapOf<PhaseContextKey, MutableMap<Int, Double>>()

    /** Prefix-compatible contexts can seed phases for unchanged leading waves after appending/removing later slots. */
    private fun PhaseContextKey.sharesWavePrefixWith(other: PhaseContextKey): Boolean {
        val sharedSize = min(waveSpecs.size, other.waveSpecs.size)
        if (sharedSize <= 0) return false
        return (0 until sharedSize).all { index -> waveSpecs[index] == other.waveSpecs[index] }
    }

    /** Carry forward phase accumulators for unchanged leading waves when a new context is created mid-stream. */
    private fun seedPhaseAccumulatorsForContext(phaseContextKey: PhaseContextKey): MutableMap<Int, Double> {
        val latestCompatibleContext = phaseAccumulatorsByContext.entries.lastOrNull { (existingKey, _) ->
            existingKey.samplingRateHz == phaseContextKey.samplingRateHz &&
                existingKey.sharesWavePrefixWith(phaseContextKey)
        } ?: return mutableMapOf()

        val seeded = mutableMapOf<Int, Double>()
        val sharedSize = min(latestCompatibleContext.key.waveSpecs.size, phaseContextKey.waveSpecs.size)
        for (index in 0 until sharedSize) {
            latestCompatibleContext.value[index]?.let { seeded[index] = it }
        }
        return seeded
    }

    /** Reset stored phase accumulators (useful for tests). */
    @Synchronized
    fun resetPhases() {
        phaseAccumulatorsByContext.clear()
        logger.i { "phase accumulators reset" }
    }

    /**
     * Generate synthetic board-shaped data (rows = channels, cols = samples) based on shared HardwareState.
     * Each shared `WaveSpec` may target specific channel indices via its `channels`
     * property. If a spec's channels list is empty it is applied to all enabled
     * channels (preserves previous behaviour).
     */
    @Synchronized
    fun generate(st: HardwareState, samples: Int): Array<DoubleArray> {
        val channels = st.channels
        val samplingRate = st.samplingRateHz
        val out = Array(maxOf(0, channels)) { DoubleArray(maxOf(0, samples)) }
        if (!st.synthetic) return out
        if (channels <= 0 || samplingRate <= 0 || samples <= 0) return out

        val phaseContextKey = PhaseContextKey(
            samplingRateHz = samplingRate,
            waveSpecs = st.waveSpecs.map { spec ->
                PhaseWaveSpecKey(
                    type = spec.type,
                    frequencyHz = spec.frequencyHz,
                    phaseShiftRad = spec.phaseShiftRad
                )
            }
        )
        val phaseAccumulators = phaseAccumulatorsByContext.getOrPut(phaseContextKey) {
            seedPhaseAccumulatorsForContext(phaseContextKey)
        }

        // Build initialPhases from accumulators (radians)
        val initialPhases = List(st.waveSpecs.size) { idx -> phaseAccumulators[idx] ?: 0.0 }

        // Map shared WaveSpec -> synthetic.WaveSpec (preserve channels)
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
                phaseShiftRad = s.phaseShiftRad, // base offset only
                channels = s.channels
            )
        }

        // For channel-specific emission we generate per-spec waveforms and
        // accumulate them only into the channels the spec targets. If a spec's
        // channels list is empty, it is treated as "apply to all enabled
        // channels" to preserve previous behaviour.
        //
        // Align enabled specs only when a spec has no existing phase accumulator yet.
        // Reapplying a shared baseline on every call corrupts per-wave continuity and
        // can suppress secondary-frequency components in superposition/FFT views.
        val firstEnabledIndex = genSpecs.indexOfFirst { it.enabled }
        val baselinePhase = if (firstEnabledIndex >= 0) initialPhases.getOrNull(firstEnabledIndex) ?: 0.0 else 0.0
        for ((idx, spec) in genSpecs.withIndex()) {
            val storedPhase = phaseAccumulators[idx]
            // New enabled specs (no stored phase yet) inherit baseline so they start aligned.
            val initPhase = if (spec.enabled && storedPhase == null && firstEnabledIndex >= 0) {
                baselinePhase
            } else {
                storedPhase ?: initialPhases.getOrNull(idx) ?: 0.0
            }
            if (!spec.enabled) {
                // Disabled specs intentionally keep no stored accumulator so re-enabling can align to the live baseline.
                phaseAccumulators.remove(idx)
                continue
            }

            val (specArr, updated) = WaveGenerator.generateSampleArrayWithPhases(listOf(spec), samplingRate, samples, listOf(initPhase))
            // updated is single-element list
            phaseAccumulators[idx] = updated.getOrNull(0) ?: initPhase

            // Determine channels that should receive this spec's contribution.
            // If the spec explicitly lists channels, treat that list as authoritative
            // (filter only by valid indices). Otherwise fall back to the global
            // enabledChannels selection.
            val targetChannels: List<Int> = if (spec.channels.isEmpty()) {
                st.enabledChannels
            } else {
                spec.channels.filter { ch -> ch in 0 until channels }
            }

            for (ch in targetChannels) {
                val dest = out.getOrNull(ch) ?: continue
                for (i in 0 until samples) {
                    dest[i] = dest[i] + specArr.getOrElse(i) { 0.0 }
                }
                out[ch] = dest
            }
        }

        logger.d { "generated $samples samples for $channels channels (per-spec routed)" }
        // Diagnostic: log max absolute sample per channel to help track unexpected amplitude scaling
        try {
            val maxPerChannel = out.mapIndexed { idx, arr -> idx to (arr.maxOfOrNull { kotlin.math.abs(it) } ?: 0.0) }
            logger.d { "synthetic max abs per channel: ${maxPerChannel.joinToString { "ch${it.first}=${"%.4f".format(it.second)}" }}" }
        } catch (_: Exception) {
            // ignore logging errors
        }
        return out
    }
}
