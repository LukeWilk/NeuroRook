package io.github.lukewilk.shared.api

import io.github.lukewilk.shared.HardwareState
import io.github.lukewilk.shared.WaveSpec
import io.github.lukewilk.shared.model.BandPower
import io.github.lukewilk.shared.model.SerialPortSuggestion
import io.github.lukewilk.shared.model.SystemLogEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface BackendApi {
    suspend fun connect(boardId: String, serialPort: String = "", timeoutSeconds: Int = 0): Boolean
    suspend fun disconnect(): Boolean
    suspend fun addWave(wave: WaveSpec): Boolean
    suspend fun removeWave(waveIndex: Int): Boolean
    suspend fun editWave(waveIndex: Int, wave: WaveSpec): Boolean
    suspend fun startStreaming(): Boolean
    suspend fun stopStreaming(): Boolean
    suspend fun enableChannel(channelId: Int): Boolean
    suspend fun disableChannel(channelId: Int): Boolean
    suspend fun enableRLD(channelId: Int = 0): Boolean
    suspend fun disableRLD(channelId: Int = 0): Boolean
    suspend fun verifyChannels(): Boolean
    suspend fun setSamplingRateHz(rate: Int): Boolean
    fun getState(): HardwareState
    fun getBrainflowBoards(): List<String>
    fun getSerialPortSuggestions(boardId: String? = null): List<SerialPortSuggestion>
    val hardwareStateFlow: StateFlow<HardwareState>
    val systemLogFlow: StateFlow<List<SystemLogEntry>>

    // Preferred: Flow-based data streams
    val filteredFlow: Flow<DoubleArray>
    val bandPowersFlow: Flow<List<BandPower>>
    val fftResultFlow: Flow<Array<Pair<Double, Double>>>

    // Optional: Callback registration for legacy/interop
    fun setOnFilteredListener(listener: ((DoubleArray) -> Unit)?)
    fun setOnBandPowersListener(listener: ((List<BandPower>) -> Unit)?)
    fun setOnFFTResultListener(listener: ((Array<Pair<Double, Double>>) -> Unit)?)
}
