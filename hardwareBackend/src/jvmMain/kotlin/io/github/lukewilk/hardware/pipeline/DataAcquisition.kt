package io.github.lukewilk.hardware.pipeline

import brainflow.BoardShim
import io.github.lukewilk.hardware.BoardConnectionManager
import io.github.lukewilk.hardware.HardwareConnector
import io.github.lukewilk.hardware.RawFrame
import io.github.lukewilk.shared.HardwareState
import io.github.lukewilk.shared.logging.LoggerProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.floor

/**
 * DataAcquisition.kt
 *
 * Provides a cooperative, cancellable streaming Flow of RawFrame windows.
 * - If a BoardShim is provided (test or real), data is fetched via cancellable wrappers around CompletableFuture
 *   and emitted as sliding windows.
 * - If no BoardShim is provided and the state is synthetic, synthetic data generator is used.
 */
class DataAcquisition(
    private val connectionManager: BoardConnectionManager,
    private val boardShimProvider: (() -> BoardShim?) = { connectionManager.getBoardShim() },
    private val stateProvider: () -> HardwareState = { connectionManager.state.value },
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) : HardwareConnector {
    private val logger = LoggerProvider.getLogger("DataAcquisition")

    override fun streamRawFrames(): Flow<RawFrame> = flow {
        val state = stateProvider()
        if (!state.connected) {
            logger.w { "Board not connected or BoardShim is null" }
            return@flow
        }

        logger.i { "Streaming from board: $state" }

        val providedShim = boardShimProvider()
        val managerShim = connectionManager.getBoardShim()
        val useInjectedShim = providedShim != null && providedShim !== managerShim
        val windowSize = state.windowSize

        // If state indicates synthetic board, and the provided shim is not an injected test shim,
        // prefer the synthetic generator to avoid calling native BrainFlow API in test JVMs.
        if (!useInjectedShim && state.synthetic) {
            val numChannels = state.channels
            val channelBuffers = Array(numChannels) { ArrayDeque<Double>() }
            try {
                // time-driven generation: compute expected total samples from start time and produce missing samples
                val samplingRate = state.samplingRateHz
                val startMs = timeProvider()
                var totalGenerated = 0L
                while (shouldContinueStreamingLoop(
                    streaming = connectionManager.isStreaming(),
                    connected = stateProvider().connected,
                    isActive = coroutineContext.isActive
                )) {
                    val now = timeProvider()
                    val elapsedMs = (now - startMs).coerceAtLeast(0L)
                    val expectedTotal = (elapsedMs.toDouble() * samplingRate.toDouble() / 1000.0)
                    var toGen = floor(expectedTotal - totalGenerated.toDouble()).toInt()
                    if (toGen <= 0) {
                        delay(5)
                        continue
                    }
                    // cap generation to avoid huge bursts
                    val maxCap = samplingRate * 2
                    if (toGen > maxCap) toGen = maxCap
                    val synth = try { connectionManager.generateSyntheticData(toGen) } catch (_: Exception) { null }
                    if (synth != null) {
                        var enabledChannels = stateProvider().enabledChannels
                        if (enabledChannels.isEmpty()) enabledChannels = (0 until numChannels).toList()
                        for (ch in enabledChannels) {
                            if (ch < 0 || ch >= synth.size) continue
                            channelBuffers[ch].addAll(synth[ch].asList())
                            while (shouldDrainChannelBuffer(
                                bufferedSamples = channelBuffers[ch].size,
                                windowSize = windowSize,
                                streaming = connectionManager.isStreaming(),
                                isActive = coroutineContext.isActive
                            )) {
                                val window = channelBuffers[ch].take(windowSize).toDoubleArray()
                                logger.d { "Emitting RawFrame for channel $ch, size ${window.size}" }
                                emit(
                                    RawFrame(
                                        timestampMs = timeProvider(),
                                        channel = ch,
                                        data = window
                                    )
                                )
                                repeat(windowSize - stateProvider().overlap) { channelBuffers[ch].removeFirstOrNull() }
                            }
                        }
                    }
                    totalGenerated += toGen
                }
            } catch (e: CancellationException) {
                logger.i { "Synthetic streaming cancelled: ${e.message}" }
            } catch (e: Exception) {
                logger.e(e) { "Synthetic streaming error: ${e.message}" }
            } finally {
                logger.i { "Synthetic streaming loop exiting" }
            }

            return@flow
        }

        // If a BoardShim was explicitly provided, prefer native fetch path (tests may inject shims).
        if (providedShim != null) {
            // Native BoardShim path (tests may inject a shim that throws)
            val boardShim = providedShim

            // Do not synchronously probe boardShim here to avoid invoking native code in test JVMs.
            // We'll handle errors when attempting to fetch data in the cancellable fetch loop below.

            val executor = Executors.newSingleThreadExecutor()
            try {
                var channelBuffers: Array<ArrayDeque<Double>>? = null

                while (shouldContinueStreamingLoop(
                    streaming = connectionManager.isStreaming(),
                    connected = stateProvider().connected,
                    isActive = coroutineContext.isActive
                )) {
                    // Fetch one block of data of size windowSize directly (avoid get_board_data_count calls)
                    val data = try {
                        withTimeout(500) {
                            suspendCancellableCoroutine<Array<DoubleArray>> { cont ->
                                val f2 = CompletableFuture.supplyAsync({
                                    boardShim.get_board_data(windowSize)
                                }, executor)
                                f2.whenComplete { r, ex -> if (ex != null) cont.resumeWithException(ex) else cont.resume(r) }
                                cont.invokeOnCancellation { f2.cancel(true) }
                            }
                        }
                    } catch (e: Exception) {
                        logger.e(e) { "get_current_board_data failed: ${e.message}" }
                        try { connectionManager.stopStream() } catch (_: Exception) {}
                        return@flow
                    }

                    if (data.isEmpty() || data[0].isEmpty()) {
                        delay(10)
                        continue
                    }

                    // Initialize channelBuffers based on actual returned channel count
                    if (channelBuffers == null) {
                        channelBuffers = Array(data.size) { ArrayDeque<Double>() }
                    } else if (channelBuffers.size != data.size) {
                        // Recreate buffers if channel count changed
                        channelBuffers = Array(data.size) { ArrayDeque<Double>() }
                    }

                    var enabledChannels = stateProvider().enabledChannels
                    if (enabledChannels.isEmpty()) {
                        enabledChannels = (0 until data.size).toList()
                    }

                    for (ch in enabledChannels) {
                        if (ch < 0 || ch >= data.size) continue
                        channelBuffers[ch].addAll(data[ch].asList())
                        while (shouldDrainChannelBuffer(
                            bufferedSamples = channelBuffers[ch].size,
                            windowSize = windowSize,
                            streaming = connectionManager.isStreaming(),
                            isActive = coroutineContext.isActive
                        )) {
                            val window = channelBuffers[ch].take(windowSize).toDoubleArray()
                            logger.d { "Emitting RawFrame for channel $ch, size ${window.size}" }
                            emit(
                                RawFrame(
                                    timestampMs = timeProvider(),
                                    channel = ch,
                                    data = window
                                )
                            )
                            repeat(windowSize - stateProvider().overlap) { channelBuffers[ch].removeFirstOrNull() }
                        }
                    }
                }
            } catch (e: CancellationException) {
                logger.i { "Streaming loop cancelled: ${e.message}" }
            } catch (e: Exception) {
                logger.e(e) { "Streaming loop error: ${e.message}" }
            } finally {
                try { executor.shutdownNow() } catch (_: Exception) {}
                logger.i { "Streaming loop exiting, executor shutdown" }
            }

            return@flow
        }

        return@flow
    }

    override suspend fun isConnected(): Boolean = connectionManager.state.value.connected
    override suspend fun close() { connectionManager.close() }
}

internal fun shouldContinueStreamingLoop(streaming: Boolean, connected: Boolean, isActive: Boolean): Boolean {
    if (!streaming) return false
    if (!connected) return false
    return isActive
}

internal fun shouldEmitCurrentWindow(streaming: Boolean, isActive: Boolean): Boolean {
    if (!streaming) return false
    return isActive
}

internal fun shouldDrainChannelBuffer(
    bufferedSamples: Int,
    windowSize: Int,
    streaming: Boolean,
    isActive: Boolean
): Boolean {
    if (bufferedSamples < windowSize) return false
    return shouldEmitCurrentWindow(streaming, isActive)
}

