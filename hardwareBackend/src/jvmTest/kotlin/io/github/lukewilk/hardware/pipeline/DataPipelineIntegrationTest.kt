package io.github.lukewilk.hardware.pipeline

import brainflow.BoardIds
import brainflow.BoardShim
import brainflow.BrainFlowInputParams
import io.github.lukewilk.shared.HardwareState
import io.github.lukewilk.shared.StateStore
import io.github.lukewilk.shared.SyntheticMode
import io.github.lukewilk.shared.Band
import io.github.lukewilk.shared.WaveSpec
import io.github.lukewilk.shared.WaveType
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.math.PI
import kotlin.math.sin

/**
 * Integration tests for `startDataPipeline` using injected `BoardShim` implementations.
 *
 * These tests focus on multi-channel regressions where interleaved native frames previously lost their source channel
 * or reused smoothing state across channels.
 */
class DataPipelineIntegrationTest {
	/** Verifies the new channel-aware callbacks keep filtered, band-power, and FFT emissions tagged with the right source channel. */
	@Test
	fun testStartDataPipelinePreservesChannelIdsForChannelAwareCallbacks() = runBlocking {
		val windowSize = 32
		val samplingRate = 128
		val state = HardwareState(
			connected = true,
			streaming = true,
			synthetic = false,
			samplingRateHz = samplingRate,
			channels = 3,
			windowSize = windowSize,
			overlap = 0,
			enabledChannels = listOf(0, 1, 2),
			bands = listOf(Band("Alpha", 8.0, 12.0)),
			syntheticMode = SyntheticMode.WAVE_GENERATOR,
			waveSpecs = listOf(WaveSpec(enabled = true, type = WaveType.SINE, amplitude = 1.0, frequencyHz = 10.0))
		)
		val stateStore = StateStore(state)

		val channel0 = DoubleArray(windowSize) { sampleIndex ->
			sin(2.0 * PI * 10.0 * sampleIndex / samplingRate)
		}
		val channel1 = DoubleArray(windowSize) { sampleIndex ->
			sin(2.0 * PI * 6.0 * sampleIndex / samplingRate)
		}
		val channel2 = DoubleArray(windowSize) { sampleIndex ->
			sin(2.0 * PI * 18.0 * sampleIndex / samplingRate)
		}

		val shim = object : BoardShim(BoardIds.NEUROPAWN_KNIGHT_BOARD, BrainFlowInputParams()) {
			override fun prepare_session() = Unit
			override fun start_stream(buffer_size: Int, streamer_params: String?) = Unit
			override fun get_board_data(num_datapoints: Int): Array<DoubleArray> = arrayOf(
				channel0.copyOf(num_datapoints),
				channel1.copyOf(num_datapoints),
				channel2.copyOf(num_datapoints)
			)
		}

		val manager = io.github.lukewilk.hardware.BoardConnectionManager(
			stateStore = stateStore,
			boardShimFactory = { _, _ -> shim },
			samplingRateProvider = { samplingRate }
		)

		assertTrue(manager.connect(BoardIds.NEUROPAWN_KNIGHT_BOARD, serialPort = ""))
		manager.startStream()

		val expectedChannels = state.enabledChannels.toSet()
		val callbackCoverageLatch = CountDownLatch(expectedChannels.size * 3)
		val callbackLock = Any()
		val filteredChannels = mutableSetOf<Int>()
		val bandPowerChannels = mutableSetOf<Int>()
		val fftChannels = mutableSetOf<Int>()

		val job = launch(Dispatchers.Default) {
			startDataPipeline(
				onFilteredByChannel = { emission ->
					if (emission.payload.isNotEmpty()) {
						synchronized(callbackLock) {
							if (filteredChannels.add(emission.channelId)) {
								callbackCoverageLatch.countDown()
							}
						}
					}
				},
				onBandPowersByChannel = { emission ->
					if (emission.payload.isNotEmpty()) {
						synchronized(callbackLock) {
							if (bandPowerChannels.add(emission.channelId)) {
								callbackCoverageLatch.countDown()
							}
						}
					}
				},
				onFFTResultByChannel = { emission ->
					if (emission.payload.isNotEmpty()) {
						synchronized(callbackLock) {
							if (fftChannels.add(emission.channelId)) {
								callbackCoverageLatch.countDown()
							}
						}
					}
				},
				stateStore = stateStore,
				manager = manager
			)
		}

		assertTrue(callbackCoverageLatch.await(5, TimeUnit.SECONDS), "Expected channel-aware callbacks for every enabled channel")

		manager.stopStream()
		manager.awaitRegisteredStreamingJobForTests()
		job.cancel()

		assertEquals(expectedChannels, synchronized(callbackLock) { filteredChannels.toSet() })
		assertEquals(expectedChannels, synchronized(callbackLock) { bandPowerChannels.toSet() })
		assertEquals(expectedChannels, synchronized(callbackLock) { fftChannels.toSet() })
	}

	/** Verifies per-band smoothing state stays isolated when alternating channel windows carry very different signals. */
	@Test
	fun testStartDataPipelineKeepsBandSmoothingIndependentPerChannel() = runBlocking {
		val windowSize = 32
		val samplingRate = 128
		val state = HardwareState(
			connected = true,
			streaming = true,
			synthetic = false,
			samplingRateHz = samplingRate,
			channels = 2,
			windowSize = windowSize,
			overlap = 0,
			enabledChannels = listOf(0, 1),
			bands = listOf(Band("Alpha", 8.0, 12.0)),
			syntheticMode = SyntheticMode.WAVE_GENERATOR,
			waveSpecs = listOf(WaveSpec(enabled = true, type = WaveType.SINE, amplitude = 1.0, frequencyHz = 10.0))
		)
		val stateStore = StateStore(state)

		val alphaWave = DoubleArray(windowSize) { sampleIndex ->
			sin(2.0 * PI * 10.0 * sampleIndex / samplingRate)
		}
		val quietWave = DoubleArray(windowSize) { 0.0 }

		val shim = object : BoardShim(BoardIds.NEUROPAWN_KNIGHT_BOARD, BrainFlowInputParams()) {
			override fun prepare_session() = Unit
			override fun start_stream(buffer_size: Int, streamer_params: String?) = Unit
			override fun get_board_data(num_datapoints: Int): Array<DoubleArray> = arrayOf(
				alphaWave.copyOf(num_datapoints),
				quietWave.copyOf(num_datapoints)
			)
		}

		val manager = io.github.lukewilk.hardware.BoardConnectionManager(
			stateStore = stateStore,
			boardShimFactory = { _, _ -> shim },
			samplingRateProvider = { samplingRate }
		)

		assertTrue(manager.connect(BoardIds.NEUROPAWN_KNIGHT_BOARD, serialPort = ""))
		manager.startStream()

		val bandPowersLatch = CountDownLatch(4)
		val alphaPowers = mutableListOf<Double>()

		val job = launch(Dispatchers.Default) {
			startDataPipeline(
				onBandPowers = { bandPowers ->
					alphaPowers += bandPowers.single { it.name == "Alpha" }.power
					bandPowersLatch.countDown()
				},
				stateStore = stateStore,
				manager = manager
			)
		}

		assertTrue(bandPowersLatch.await(5, TimeUnit.SECONDS), "Expected alternating channel band-power callbacks")

		manager.stopStream()
		manager.awaitRegisteredStreamingJobForTests()
		job.cancel()

		assertEquals(4, alphaPowers.size)
		assertTrue(alphaPowers[0] > alphaPowers[1], "Channel 0 should produce stronger alpha power than channel 1")
		assertEquals(alphaPowers[0], alphaPowers[2], 1e-9, "Channel 0 alpha smoothing should not be contaminated by channel 1")
		assertEquals(alphaPowers[1], alphaPowers[3], 1e-9, "Channel 1 alpha smoothing should remain stable across alternating frames")
	}

	/** Verifies the legacy filtered, band-power, and FFT callbacks still fire when a deterministic shim is injected. */
	@Test
	fun testStartDataPipelineInvokesCallbacksWithInjectedShim() = runBlocking {
		// Small deterministic state: 1 channel, small window so pipeline emits quickly
		val state = HardwareState(
			connected = true,
			streaming = true,
			synthetic = false,
			samplingRateHz = 128,
			channels = 1,
			windowSize = 8,
			overlap = 0,
			enabledChannels = listOf(0),
			syntheticMode = SyntheticMode.WAVE_GENERATOR,
			waveSpecs = listOf(WaveSpec(enabled = true, type = WaveType.SINE, amplitude = 1.0, frequencyHz = 8.0))
		)
		val stateStore = StateStore(state)

		// Create a test BoardShim that returns deterministic data blocks
		val shim = object : BoardShim(BoardIds.NEUROPAWN_KNIGHT_BOARD, BrainFlowInputParams()) {
			override fun prepare_session() = Unit
			override fun start_stream(buffer_size: Int, streamer_params: String?) = Unit
			override fun get_board_data(num_datapoints: Int): Array<DoubleArray> {
				// return one channel containing `num_datapoints` samples of value 1.0
				return Array(1) { DoubleArray(num_datapoints) { 1.0 } }
			}
		}

		val manager = io.github.lukewilk.hardware.BoardConnectionManager(
			stateStore = stateStore,
			boardShimFactory = { _, _ -> shim },
			samplingRateProvider = { 128 }
		)

		// Connect and start native streaming path (shim is injected)
		assertTrue(manager.connect(BoardIds.NEUROPAWN_KNIGHT_BOARD, serialPort = ""))
		manager.startStream()

		val filteredLatch = CountDownLatch(1)
		val bandpowersLatch = CountDownLatch(1)
		val fftLatch = CountDownLatch(1)

		val onFiltered: (DoubleArray) -> Unit = { _ -> filteredLatch.countDown() }
		val onBandPowers: (List<io.github.lukewilk.shared.model.BandPower>) -> Unit = { _ -> bandpowersLatch.countDown() }
		val onFFTResult: (Array<Pair<Double, Double>>) -> Unit = { _ -> fftLatch.countDown() }

		// Run pipeline on a background dispatcher so the blocking latch waits do not starve the coroutine.
		val job = launch(Dispatchers.Default) {
			startDataPipeline(
				onBandPowers = onBandPowers,
				onFiltered = onFiltered,
				onFFTResult = onFFTResult,
				stateStore = stateStore,
				manager = manager
			)
		}

		// Wait up to 5 seconds for callbacks
		val filteredOk = filteredLatch.await(5, TimeUnit.SECONDS)
		val bandOk = bandpowersLatch.await(5, TimeUnit.SECONDS)
		val fftOk = fftLatch.await(5, TimeUnit.SECONDS)

		// Stop streaming and cancel job
		manager.stopStream()
		manager.awaitRegisteredStreamingJobForTests()
		job.cancel()

		assertTrue(filteredOk, "onFiltered should be invoked")
		assertTrue(bandOk, "onBandPowers should be invoked")
		assertTrue(fftOk, "onFFTResult should be invoked")
	}
}




