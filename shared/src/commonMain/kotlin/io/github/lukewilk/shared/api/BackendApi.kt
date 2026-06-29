package io.github.lukewilk.shared.api

import io.github.lukewilk.shared.HardwareState
import io.github.lukewilk.shared.WaveSpec
import io.github.lukewilk.shared.model.BandPower
import io.github.lukewilk.shared.model.ChannelData
import io.github.lukewilk.shared.model.SerialPortSuggestion
import io.github.lukewilk.shared.model.SystemLogEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Shared contract used by UI and platform hosts to control hardware streaming and consume processed data.
 *
 * Preferred consumers should observe the flow properties because they preserve the originating channel id for
 * interleaved multi-channel emissions. The callback listeners remain available for legacy callers that only expect
 * payload data.
 */
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
    suspend fun getBrainflowBoards(): List<String>
    suspend fun getSerialPortSuggestions(boardId: String? = null): List<SerialPortSuggestion>
    val hardwareStateFlow: StateFlow<HardwareState>
    val systemLogFlow: StateFlow<List<SystemLogEntry>>

    /** Preferred filtered-signal stream with the zero-based source channel attached to each payload. */
    val filteredFlow: Flow<ChannelData<DoubleArray>>

    /** Preferred smoothed band-power stream with the zero-based source channel attached to each payload. */
    val bandPowersFlow: Flow<ChannelData<List<BandPower>>>

    /** Preferred FFT/PSD stream with the zero-based source channel attached to each payload. */
    val fftResultFlow: Flow<ChannelData<Array<Pair<Double, Double>>>>

    /** Registers a legacy filtered-signal listener that receives payloads without channel metadata. */
    fun setOnFilteredListener(listener: ((DoubleArray) -> Unit)?)

    /** Registers a legacy band-power listener that receives payloads without channel metadata. */
    fun setOnBandPowersListener(listener: ((List<BandPower>) -> Unit)?)

    /** Registers a legacy FFT listener that receives payloads without channel metadata. */
    fun setOnFFTResultListener(listener: ((Array<Pair<Double, Double>>) -> Unit)?)
}
