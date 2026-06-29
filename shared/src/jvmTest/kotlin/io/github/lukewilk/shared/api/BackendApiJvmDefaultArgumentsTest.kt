package io.github.lukewilk.shared.api

import io.github.lukewilk.shared.HardwareState
import io.github.lukewilk.shared.WaveSpec
import io.github.lukewilk.shared.model.BandPower
import io.github.lukewilk.shared.model.ChannelData
import io.github.lukewilk.shared.model.SerialPortSuggestion
import io.github.lukewilk.shared.model.SystemLogEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * JVM-targeted coverage for BackendApi default-argument bridges.
 *
 * The common test version verifies behavior, but Kover still reports the JVM-generated
 * `BackendApi.DefaultImpls` bridge methods as uncovered unless the same calls run in `jvmTest`.
 */
class BackendApiJvmDefaultArgumentsTest {
    @Test
    fun `backend api jvm default argument bridges forward fallback values`() = runBlocking {
        val recordingApi = RecordingJvmBackendApi()
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

    @Test
    fun `backend api reflected default impl bridges forward fallback values`() {
        val recordingApi = RecordingJvmBackendApi()
        val defaultImplsClass = Class.forName("io.github.lukewilk.shared.api.BackendApi\$DefaultImpls")
        val continuation = object : Continuation<Any?> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(result: Result<Any?>) = Unit
        }

        val connectResult = defaultImplsClass.getDeclaredMethod(
            "connect\$default",
            BackendApi::class.java,
            String::class.java,
            String::class.java,
            Int::class.javaPrimitiveType,
            Continuation::class.java,
            Int::class.javaPrimitiveType,
            Any::class.java
        ).invoke(null, recordingApi, "SYNTHETIC_BOARD", "ignored", 99, continuation, 0b110, null)
        assertEquals(true, connectResult)

        val enableResult = defaultImplsClass.getDeclaredMethod(
            "enableRLD\$default",
            BackendApi::class.java,
            Int::class.javaPrimitiveType,
            Continuation::class.java,
            Int::class.javaPrimitiveType,
            Any::class.java
        ).invoke(null, recordingApi, 42, continuation, 0b1, null)
        assertEquals(true, enableResult)

        val disableResult = defaultImplsClass.getDeclaredMethod(
            "disableRLD\$default",
            BackendApi::class.java,
            Int::class.javaPrimitiveType,
            Continuation::class.java,
            Int::class.javaPrimitiveType,
            Any::class.java
        ).invoke(null, recordingApi, 42, continuation, 0b1, null)
        assertEquals(true, disableResult)

        @Suppress("UNCHECKED_CAST")
        val suggestionsResult = defaultImplsClass.getDeclaredMethod(
            "getSerialPortSuggestions\$default",
            BackendApi::class.java,
            String::class.java,
            Continuation::class.java,
            Int::class.javaPrimitiveType,
            Any::class.java
        ).invoke(null, recordingApi, "ignored", continuation, 0b1, null) as List<SerialPortSuggestion>
        assertEquals(emptyList(), suggestionsResult)

        assertEquals(Triple("SYNTHETIC_BOARD", "", 0), recordingApi.connectArgs.single())
        assertEquals(listOf(0), recordingApi.enableRldArgs)
        assertEquals(listOf(0), recordingApi.disableRldArgs)
        assertEquals(listOf<String?>(null), recordingApi.serialPortSuggestionArgs)
    }
}

private class RecordingJvmBackendApi : BackendApi {
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
    override suspend fun getBrainflowBoards(): List<String> = emptyList()

    override suspend fun getSerialPortSuggestions(boardId: String?): List<SerialPortSuggestion> {
        serialPortSuggestionArgs += boardId
        return emptyList()
    }

    override val hardwareStateFlow = MutableStateFlow(HardwareState())
    override val systemLogFlow = MutableStateFlow<List<SystemLogEntry>>(emptyList())
    override val filteredFlow: Flow<ChannelData<DoubleArray>> = emptyFlow()
    override val bandPowersFlow: Flow<ChannelData<List<BandPower>>> = emptyFlow()
    override val fftResultFlow: Flow<ChannelData<Array<Pair<Double, Double>>>> = emptyFlow()
    override fun setOnFilteredListener(listener: ((DoubleArray) -> Unit)?) = Unit
    override fun setOnBandPowersListener(listener: ((List<BandPower>) -> Unit)?) = Unit
    override fun setOnFFTResultListener(listener: ((Array<Pair<Double, Double>>) -> Unit)?) = Unit
}
