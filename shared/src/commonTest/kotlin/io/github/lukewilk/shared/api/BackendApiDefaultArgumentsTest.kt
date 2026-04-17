package io.github.lukewilk.shared.api

import io.github.lukewilk.shared.HardwareState
import io.github.lukewilk.shared.WaveSpec
import io.github.lukewilk.shared.model.BandPower
import io.github.lukewilk.shared.model.SerialPortSuggestion
import io.github.lukewilk.shared.model.SystemLogEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verifies the default-argument bridges declared on BackendApi.
 */
class BackendApiDefaultArgumentsTest {
	@Test
	fun `backend api default arguments forward the expected fallback values`() = runBlocking {
		// Exercises interface calls that omit optional arguments so the generated default bridges are covered.
		val recordingApi = RecordingBackendApi()
		val api: BackendApi = recordingApi

		assertTrue(api.connect("SYNTHETIC_BOARD"))
		assertTrue(api.enableRLD())
		assertTrue(api.disableRLD())
		assertEquals(emptyList(), api.getSerialPortSuggestions())

		assertEquals(Triple("SYNTHETIC_BOARD", "", 0), recordingApi.connectArgs.single())
		assertEquals(listOf(0), recordingApi.enableRldArgs)
		assertEquals(listOf(0), recordingApi.disableRldArgs)
		assertEquals(listOf<String?>(null), recordingApi.serialPortSuggestionArgs)
	}
}

private class RecordingBackendApi : BackendApi {
	val connectArgs = mutableListOf<Triple<String, String, Int>>()
	val enableRldArgs = mutableListOf<Int>()
	val disableRldArgs = mutableListOf<Int>()
	val serialPortSuggestionArgs = mutableListOf<String?>()

	override suspend fun connect(boardId: String, serialPort: String, timeoutSeconds: Int): Boolean {
		connectArgs += Triple(boardId, serialPort, timeoutSeconds)
		return true
	}

	override suspend fun disconnect(): Boolean = true
	override suspend fun addWave(wave: WaveSpec): Boolean = true
	override suspend fun removeWave(waveIndex: Int): Boolean = true
	override suspend fun editWave(waveIndex: Int, wave: WaveSpec): Boolean = true
	override suspend fun startStreaming(): Boolean = true
	override suspend fun stopStreaming(): Boolean = true
	override suspend fun enableChannel(channelId: Int): Boolean = true
	override suspend fun disableChannel(channelId: Int): Boolean = true
	override suspend fun enableRLD(channelId: Int): Boolean {
		enableRldArgs += channelId
		return true
	}

	override suspend fun disableRLD(channelId: Int): Boolean {
		disableRldArgs += channelId
		return true
	}

	override suspend fun verifyChannels(): Boolean = true
	override suspend fun setSamplingRateHz(rate: Int): Boolean = true
	override fun getState(): HardwareState = HardwareState()
	override fun getBrainflowBoards(): List<String> = emptyList()
	override fun getSerialPortSuggestions(boardId: String?): List<SerialPortSuggestion> {
		serialPortSuggestionArgs += boardId
		return emptyList()
	}

	override val hardwareStateFlow = MutableStateFlow(HardwareState())
	override val systemLogFlow = MutableStateFlow<List<SystemLogEntry>>(emptyList())
	override val filteredFlow: Flow<DoubleArray> = emptyFlow()
	override val bandPowersFlow: Flow<List<BandPower>> = emptyFlow()
	override val fftResultFlow: Flow<Array<Pair<Double, Double>>> = emptyFlow()
	override fun setOnFilteredListener(listener: ((DoubleArray) -> Unit)?) = Unit
	override fun setOnBandPowersListener(listener: ((List<BandPower>) -> Unit)?) = Unit
	override fun setOnFFTResultListener(listener: ((Array<Pair<Double, Double>>) -> Unit)?) = Unit
}

