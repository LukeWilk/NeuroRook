package io.github.lukewilk.shared

// Shared filter configuration data classes

data class FilterConfig(
    val bandpass: BandpassConfig?,
    val bandstopFilters: List<BandstopConfig>
)
data class BandpassConfig(
    val lowCut: Double,
    val highCut: Double,
    val order: Int,
    val samplingRate: Int,
    val filterType: Int,
    val ripple: Double
)
data class BandstopConfig(
    val startFreq: Double,
    val stopFreq: Double,
    val order: Int,
    val samplingRate: Int,
    val filterType: Int,
    val ripple: Double
)
