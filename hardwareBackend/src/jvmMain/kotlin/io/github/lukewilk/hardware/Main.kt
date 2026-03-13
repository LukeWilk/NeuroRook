package io.github.lukewilk.hardware

import io.github.lukewilk.hardware.LoggerProvider
import io.github.lukewilk.hardware.signal.applyBandpassFilter
import io.github.lukewilk.hardware.signal.applyHighPassFilter
import io.github.lukewilk.hardware.signal.applyNotchFilter
import io.github.lukewilk.hardware.signal.applyWindow
import io.github.lukewilk.hardware.signal.bandPower
import io.github.lukewilk.hardware.signal.BandpassFilterConfig
import io.github.lukewilk.hardware.signal.ExponentialMovingAverage
import io.github.lukewilk.hardware.signal.HighPassConfig
import io.github.lukewilk.hardware.signal.NotchFilterConfig
import io.github.lukewilk.hardware.signal.computeWelchPSD
import io.github.lukewilk.hardware.signal.WelchConfig
import io.github.lukewilk.hardware.signal.WindowType
import io.github.lukewilk.shared.HardwareState
import io.github.lukewilk.shared.StateStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

val logger = LoggerProvider.getLogger("Main")

/**
 * Band power result from the pipeline.
 */
data class BandPower(val name: String, val power: Double)

suspend fun main(
    _args: Array<String>? = null,
    onBandPowers: ((List<BandPower>) -> Unit)? = null,
    onFiltered: ((DoubleArray) -> Unit)? = null,
    onFFTResult: ((Array<Pair<Double, Double>>) -> Unit)? = null,
    stateStore: StateStore<HardwareState>? = null,
    manager: BoardConnectionManager? = null
) {
    val actualStateStore = stateStore ?: StateStore(HardwareState())
    val actualManager = manager ?: BoardConnectionManager(actualStateStore)
    // We'll register the Job inside the coroutineScope below so we register the actual pipeline job

    logger.d { "At start of main, enabledChannels = ${actualStateStore.get().enabledChannels}" }

    // Use structured concurrency for observer and pipeline so cancellation propagates
    coroutineScope {
        // Register this coroutine's Job so manager can cancel it if needed
        val currentJob: Job? = coroutineContext[Job]
        try {
            actualManager.registerStreamingJob(currentJob)
        } catch (e: Exception) {
            logger.e(e) { "Failed to register streaming job: ${e.message}" }
        }

        // Observe filterConfig changes and log or react to updates
        launch {
            actualStateStore.state
                .map { it.filterConfig }
                .distinctUntilChanged()
                .collect { newConfig ->
                    logger.i { "Filter config changed: $newConfig" }
                    // If you need to update hardware or other side effects, do it here
                }
        }

        // Data acquisition
        val acquisition = DataAcquisition(connectionManager = actualManager)
        val rawFlow = acquisition.streamRawFrames() // Flow<RawFrame>

        // Smoothing state per configured band name.
        val bandEma = mutableMapOf<String, ExponentialMovingAverage>()

        buffer(
            inputFlow = rawFlow,
            stateStore = actualStateStore
        ) { frame ->
            logger.d { "Buffer received frame: channel=${frame.channel}, data.size=${frame.data.size}" }

            val st = actualStateStore.get()
            val samplingRate = if (st.samplingRateHz > 0) st.samplingRateHz.toDouble() else 250.0

            // 2) Detrend/high-pass
            var sig = applyHighPassFilter(
                signal = frame.data.copyOf(),
                config = HighPassConfig(cutoffHz = 0.5, order = 2, samplingRateHz = samplingRate)
            )

            // 3) Notch: apply configured band-stop filters from state (common for 50/60Hz cleanup).
            if (st.filterConfig.bandstopFilters.isNotEmpty()) {
                st.filterConfig.bandstopFilters.forEach { bs ->
                    val config = NotchFilterConfig(
                        centerHz = (bs.startFreq + bs.stopFreq) / 2.0,
                        bandwidthHz = (bs.stopFreq - bs.startFreq).coerceAtLeast(0.5),
                        order = bs.order.coerceAtLeast(1),
                        samplingRateHz = samplingRate
                    )
                    sig = applyNotchFilter(signal = sig, config = config)
                }
            }

            // 4) Optional bandpass from filter config.
            st.filterConfig.bandpass?.let { bp ->
                sig = applyBandpassFilter(
                    signal = sig,
                    config = BandpassFilterConfig(
                        lowCutHz = bp.lowCut,
                        highCutHz = bp.highCut,
                        order = bp.order.coerceAtLeast(1),
                        samplingRateHz = samplingRate
                    )
                )
            }

            // 5) Windowing
            val windowType = WindowType.HANN
            val windowed = applyWindow(sig, windowType)

            // 6) Welch/PSD
            val psd = computeWelchPSD(
                windowedSignal = windowed,
                config = WelchConfig(
                    samplingRateHz = samplingRate,
                    windowType = windowType,
                    padToNextPowerOfTwo = true
                )
            )

            // 7) Band power + 8) smoothing (EMA)
            val smoothedBandPowers = st.bands.map { band ->
                val rawPower = bandPower(psd, band.lowHz, band.highHz)
                val ema = bandEma.getOrPut(band.name) { ExponentialMovingAverage(alpha = 0.3) }
                BandPower(band.name, ema.update(rawPower))
            }

            // Invoke callbacks for test data collection
            logger.d { "Invoking callbacks: onFiltered=${onFiltered != null}, onBandPowers=${onBandPowers != null}, onFFTResult=${onFFTResult != null}" }
            onFiltered?.invoke(windowed)  // Pass windowed signal, not raw filtered
            onBandPowers?.invoke(smoothedBandPowers)
            onFFTResult?.invoke(psd)

            logger.v {
                "BandPowers(channel=${frame.channel}): " +
                    smoothedBandPowers.joinToString(", ") { (name, power) -> "$name=${"%.4f".format(power)}" }
            }
        }
    }
    try {
        actualManager.registerStreamingJob(null)
    } catch (e: Exception) {
        logger.e(e) { "Failed to unregister streaming job: ${e.message}" }
    }
    actualManager.close()
}

// Standard JVM entry point for runner compatibility
fun main(args: Array<String>) {
    runBlocking {
        main(_args = args)
    }
}