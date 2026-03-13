package io.github.lukewilk.hardware

import io.github.lukewilk.shared.BandstopConfig
import io.github.lukewilk.hardware.signal.NotchFilterConfig
import io.github.lukewilk.hardware.signal.applyNotchFilter

/**
 * Applies all configured notch (band-stop) filters to the signal as in the main pipeline.
 * Used for unit testing to ensure coverage of the notch filter logic.
 * Accepts BandstopConfig from shared config for test compatibility.
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

