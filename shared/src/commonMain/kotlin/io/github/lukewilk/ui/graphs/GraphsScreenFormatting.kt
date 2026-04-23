package io.github.lukewilk.ui.graphs

import io.github.lukewilk.shared.model.BandPower
import kotlin.math.round

/** Formats filtered-signal samples into a concise status line for the current channel card. */
internal fun filteredSignalSummary(samples: DoubleArray): String {
    if (samples.isEmpty()) return "No samples received yet."
    val minValue = samples.minOrNull() ?: 0.0
    val maxValue = samples.maxOrNull() ?: 0.0
    val lastValue = samples.lastOrNull() ?: 0.0
    return "Latest ${samples.size} samples • min ${formatGraphNumber(minValue)} • max ${formatGraphNumber(maxValue)} • last ${formatGraphNumber(lastValue)}"
}

/** Formats band-power values into a compact chip-like summary string. */
internal fun bandPowersSummary(bands: List<BandPower>): String {
    if (bands.isEmpty()) return "No band powers received yet."
    return bands.joinToString(separator = " • ") { band ->
        "${band.name}: ${formatGraphNumber(band.power)}"
    }
}

/** Formats FFT bins by surfacing the latest bin count and strongest frequency peak. */
internal fun fftSummary(values: Array<Pair<Double, Double>>): String {
    if (values.isEmpty()) return "No FFT bins received yet."
    val peak = values.maxByOrNull { it.second } ?: (0.0 to 0.0)
    return "Latest ${values.size} bins • peak ${formatGraphNumber(peak.first)} Hz @ ${formatGraphNumber(peak.second)}"
}

/** Rounds graph values to a compact human-readable form for labels and summaries. */
internal fun formatGraphNumber(value: Double): String {
    val rounded = round(value * 100.0) / 100.0
    return if (rounded == rounded.toInt().toDouble()) {
        rounded.toInt().toString()
    } else {
        rounded.toString()
    }
}

