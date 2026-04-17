package io.github.lukewilk.hardware.utils

import io.github.lukewilk.shared.BandstopConfig
import io.github.lukewilk.hardware.pipeline.signal.NotchFilterConfig
import io.github.lukewilk.hardware.pipeline.signal.applyNotchFilter

/**
 * Applies all configured notch (band-stop) filters to the signal as in the main pipeline.
 * Accepts shared [BandstopConfig] values so callers can reuse the same configuration model across modules.
 */
fun applyConfiguredNotchFilters(
    signal: DoubleArray,
    bandstopFilters: List<BandstopConfig>,
    samplingRate: Double
): DoubleArray {
    var sig = signal.copyOf()
    if (bandstopFilters.isNotEmpty()) {
        bandstopFilters.forEach { bs ->
            val center = (bs.startFreq + bs.stopFreq) / 2.0
            val bw = (bs.stopFreq - bs.startFreq).coerceAtLeast(0.5)
            sig = applyNotchFilter(
                signal = sig,
                config = NotchFilterConfig(
                    centerHz = center,
                    bandwidthHz = bw,
                    order = bs.order.coerceAtLeast(1),
                    samplingRateHz = samplingRate
                )
            )
        }
    }
    return sig
}

