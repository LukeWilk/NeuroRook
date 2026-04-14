package io.github.lukewilk.hardware.pipeline

import brainflow.BoardIds
import brainflow.BoardShim
import brainflow.BrainFlowInputParams
import io.github.lukewilk.shared.HardwareState
import io.github.lukewilk.shared.StateStore
import io.github.lukewilk.shared.SyntheticMode
import io.github.lukewilk.shared.WaveSpec
import io.github.lukewilk.shared.WaveType
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class DataPipelineIntegrationTest {
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
		job.cancel()

		assertTrue(filteredOk, "onFiltered should be invoked")
		assertTrue(bandOk, "onBandPowers should be invoked")
		assertTrue(fftOk, "onFFTResult should be invoked")
	}
}




