package io.github.lukewilk.hardware.pipeline

import io.github.lukewilk.hardware.BoardConnectionManager
import io.github.lukewilk.hardware.pipeline.signal.BandpassFilterConfig
import io.github.lukewilk.hardware.pipeline.signal.ExponentialMovingAverage
import io.github.lukewilk.hardware.pipeline.signal.HighPassConfig
import io.github.lukewilk.hardware.pipeline.signal.NotchFilterConfig
import io.github.lukewilk.hardware.pipeline.signal.WelchConfig
import io.github.lukewilk.hardware.pipeline.signal.WindowType
import io.github.lukewilk.hardware.pipeline.signal.applyBandpassFilter
import io.github.lukewilk.hardware.pipeline.signal.applyHighPassFilter
import io.github.lukewilk.hardware.pipeline.signal.applyNotchFilter
import io.github.lukewilk.hardware.pipeline.signal.applyWindow
import io.github.lukewilk.hardware.pipeline.signal.bandPower
import io.github.lukewilk.hardware.pipeline.signal.computeWelchPSD
import io.github.lukewilk.shared.model.BandPower
import io.github.lukewilk.shared.model.ChannelData
import io.github.lukewilk.shared.FilterConfig
import io.github.lukewilk.shared.HardwareState
import io.github.lukewilk.shared.StateStore
import io.github.lukewilk.shared.logging.LoggerProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Starts the main data processing pipeline for EEG/biopotential streaming.
 *
 * This function orchestrates the acquisition, filtering, feature extraction, and callback invocation
 * for real-time signal processing.
 *
 * @param onBandPowers Callback for band power results (per frame)
 * @param onBandPowersByChannel Optional channel-aware callback for band power results
 * @param onFiltered Callback for filtered signal (per frame)
 * @param onFilteredByChannel Optional channel-aware callback for filtered signal windows
 * @param onFFTResult Callback for FFT/PSD results (per frame)
 * @param onFFTResultByChannel Optional channel-aware callback for FFT/PSD results
 * @param stateStore The state store holding pipeline configuration and state
 * @param manager The board connection manager for hardware communication
 */
suspend fun startDataPipeline(
    onBandPowers: ((List<BandPower>) -> Unit)? = null,
    onBandPowersByChannel: ((ChannelData<List<BandPower>>) -> Unit)? = null,
    onFiltered: ((DoubleArray) -> Unit)? = null,
    onFilteredByChannel: ((ChannelData<DoubleArray>) -> Unit)? = null,
    onFFTResult: ((Array<Pair<Double, Double>>) -> Unit)? = null,
    onFFTResultByChannel: ((ChannelData<Array<Pair<Double, Double>>>) -> Unit)? = null,
    stateStore: StateStore<HardwareState>,
    manager: BoardConnectionManager
) {
    val logger = LoggerProvider.getLogger("DataPipeline")
    logger.d { "At start of pipeline, enabledChannels = ${stateStore.get().enabledChannels}" }
    var failure: Throwable? = null
    try {
        coroutineScope {
            // Register this coroutine's job for cancellation/cleanup
            val currentJob: Job? = coroutineContext[Job]
            try {
                manager.registerStreamingJob(currentJob)
            } catch (e: Exception) {
                logger.e(e) { "Failed to register streaming job: ${e.message}" }
            }
            // React to filter config changes for logging/debugging
            val filterConfigLoggingJob = launch {
                var isFirstConfig = true
                var previousConfig = stateStore.get().filterConfig
                stateStore.state.collect { currentState ->
                    val newConfig = currentState.filterConfig
                    if (shouldLogFilterConfigChange(isFirstConfig, newConfig, previousConfig)) {
                        logger.i { "Filter config changed: $newConfig" }
                        previousConfig = newConfig
                        isFirstConfig = false
                    }
                }
            }
            // Set up data acquisition and streaming
            val acquisition = DataAcquisition(connectionManager = manager)
            val rawFlow = acquisition.streamRawFrames()
            // Keep smoothing state per (channel, band) so alternating channel windows cannot bleed into each other.
            val bandEma = mutableMapOf<Pair<Int, String>, ExponentialMovingAverage>()
            try {
                // Buffer and process each frame
                buffer(
                    inputFlow = rawFlow,
                    stateStore = stateStore
                ) { frame ->
                    logger.d { "Buffer received frame: channel=${frame.channel}, data.size=${frame.data.size}" }
                    val st = stateStore.get()
                    val samplingRate = resolvePipelineSamplingRate(st.samplingRateHz)
                    // High-pass filter
                    var sig = applyHighPassFilter(
                        signal = frame.data.copyOf(),
                        config = HighPassConfig(cutoffHz = 0.5, order = 2, samplingRateHz = samplingRate)
                    )
                    // Notch (bandstop) filters
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
                    // Bandpass filter
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
                    // Windowing
                    val windowType = WindowType.HANN
                    val windowed = applyWindow(sig, windowType)
                    // Power spectral density (PSD) via Welch's method
                    val psd = computeWelchPSD(
                        windowedSignal = windowed,
                        config = WelchConfig(
                            samplingRateHz = samplingRate,
                            windowType = windowType,
                            padToNextPowerOfTwo = true
                        )
                    )
                    // Compute and smooth band powers
                    val smoothedBandPowers = st.bands.map { band ->
                        val rawPower = bandPower(psd, band.lowHz, band.highHz)
                        val ema = bandEma.getOrPut(frame.channel to band.name) { ExponentialMovingAverage(alpha = 0.3) }
                        BandPower(band.name, ema.update(rawPower))
                    }
                    // Preserve the originating channel on preferred-flow payloads so downstream collectors do not infer
                    // channel identity from emission order alone.
                    val filteredByChannel = ChannelData(channelId = frame.channel, payload = windowed)
                    val bandPowersByChannel = ChannelData(channelId = frame.channel, payload = smoothedBandPowers)
                    val fftResultByChannel = ChannelData(channelId = frame.channel, payload = psd)
                    val bandPowerSummary =
                        "BandPowers(channel=${frame.channel}): " +
                            smoothedBandPowers.joinToString(", ") { (name, power) -> "$name=${"%.4f".format(power)}" }
                    // Invoke callbacks for downstream consumers (API, UI, etc.)
                    logger.d { "Invoking pipeline callbacks for channel ${frame.channel}" }
                    onFiltered?.invoke(windowed)
                    onFilteredByChannel?.invoke(filteredByChannel)
                    onBandPowers?.invoke(smoothedBandPowers)
                    onBandPowersByChannel?.invoke(bandPowersByChannel)
                    onFFTResult?.invoke(psd)
                    onFFTResultByChannel?.invoke(fftResultByChannel)
                    logger.d { bandPowerSummary }
                }
            } finally {
                filterConfigLoggingJob.cancel()
            }
        }
    } catch (t: Throwable) {
        failure = t
    }
    completePipelineCleanup(manager, logger)
    rethrowPipelineFailure(failure)
    return
}

internal fun shouldLogFilterConfigChange(
    isFirstConfig: Boolean,
    newConfig: FilterConfig,
    previousConfig: FilterConfig
): Boolean {
    return isFirstConfig || newConfig != previousConfig
}

internal fun resolvePipelineSamplingRate(samplingRateHz: Int): Double {
    return if (samplingRateHz > 0) samplingRateHz.toDouble() else 250.0
}

internal fun rethrowPipelineFailure(failure: Throwable?) {
    if (failure != null) throw failure
}

private fun completePipelineCleanup(
    manager: BoardConnectionManager,
    logger: co.touchlab.kermit.Logger
) {
    // Cleanup: unregister the pipeline job even when the coroutine is cancelled.
    try {
        manager.registerStreamingJob(null)
    } catch (e: Exception) {
        logger.e(e) { "Failed to unregister streaming job: ${e.message}" }
    }
    logger.d { "Pipeline cleanup finished" }
}
