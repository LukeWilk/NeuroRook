package io.github.lukewilk.hardware.api

import brainflow.BoardIds
import io.github.lukewilk.hardware.BoardConnectionManager
import io.github.lukewilk.hardware.pipeline.startDataPipeline
import io.github.lukewilk.shared.api.BackendApi
import io.github.lukewilk.shared.HardwareState
import io.github.lukewilk.shared.WaveSpec
import io.github.lukewilk.shared.model.BandPower
import io.github.lukewilk.shared.model.ChannelData
import io.github.lukewilk.shared.model.SerialPortSuggestion
import io.github.lukewilk.shared.model.SystemLogEntry
import io.github.lukewilk.shared.model.SystemLogSeverity
import io.github.lukewilk.shared.StateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * JVM backend implementation that bridges board control with the shared `BackendApi` contract.
 *
 * Preferred flows replay the latest processed payload together with its source channel id so new collectors do not
 * lose context when streaming is already active. Legacy listeners remain payload-only for compatibility with older
 * consumers that have not migrated to `ChannelData` yet.
 */
class HardwareBackendApi(
    private val serialPortDiscovery: SerialPortDiscovery = JSerialCommSerialPortDiscovery()
) : BackendApi {
    private companion object {
        const val MAX_SYSTEM_LOG_ENTRIES = 200
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val stateStore = StateStore(HardwareState())
    private val manager = BoardConnectionManager(stateStore)
    private var connectedBoardId: BoardIds? = null
    private val _systemLogFlow = MutableStateFlow<List<SystemLogEntry>>(emptyList())

    override val hardwareStateFlow: StateFlow<HardwareState> = stateStore.state
    override val systemLogFlow: StateFlow<List<SystemLogEntry>> = _systemLogFlow.asStateFlow()

    // Replay the latest channel-tagged filtered window so late subscribers can render immediately without guessing origin.
    private val _filteredFlow = MutableSharedFlow<ChannelData<DoubleArray>>(replay = 1)
    override val filteredFlow: Flow<ChannelData<DoubleArray>> = _filteredFlow.asSharedFlow()
    // Replay the latest channel-tagged feature summary for dashboards that attach mid-stream.
    private val _bandPowersFlow = MutableSharedFlow<ChannelData<List<BandPower>>>(replay = 1)
    override val bandPowersFlow: Flow<ChannelData<List<BandPower>>> = _bandPowersFlow.asSharedFlow()
    // Replay the latest channel-tagged spectrum so FFT viewers keep channel identity across collector restarts.
    private val _fftResultFlow = MutableSharedFlow<ChannelData<Array<Pair<Double, Double>>>>(replay = 1)
    override val fftResultFlow: Flow<ChannelData<Array<Pair<Double, Double>>>> = _fftResultFlow.asSharedFlow()

    private var streamingJob: kotlinx.coroutines.Job? = null

    // Legacy listeners intentionally stay payload-only so existing callers do not break during the flow migration.
    private var onFiltered: ((DoubleArray) -> Unit)? = null
    private var onBandPowers: ((List<BandPower>) -> Unit)? = null
    private var onFFTResult: ((Array<Pair<Double, Double>>) -> Unit)? = null

    override suspend fun connect(boardId: String, serialPort: String, timeoutSeconds: Int): Boolean {
        appendInfo("Connecting to $boardId using ${serialPort.ifBlank { "default port" }} (timeout=${timeoutSeconds}s)...")
        // Use BrainFlow's BoardIds enum to resolve board names dynamically
        val id = BoardIds.entries.find { it.name.equals(boardId, ignoreCase = true) } ?: BoardIds.NO_BOARD
        if (id == BoardIds.NO_BOARD && !boardId.equals(BoardIds.NO_BOARD.name, ignoreCase = true)) {
            appendWarn("Requested board '$boardId' is unknown. Falling back to NO_BOARD before attempting connection.")
        }

        val connected = manager.connect(id, serialPort, timeoutSeconds)
        connectedBoardId = id.takeIf { connected }
        appendLog(
            severity = if (connected) SystemLogSeverity.INFO else SystemLogSeverity.ERROR,
            if (connected) {
                "Connected to ${id.name} with ${stateStore.get().channels} channels at ${stateStore.get().samplingRateHz} Hz."
            } else {
                "Failed to connect to ${id.name}. Check board selection, port, and timeout settings."
            }
        )
        return connected
    }

    override suspend fun disconnect(): Boolean {
        appendInfo("Disconnect requested for ${connectedBoardLabel("current board")}.")
        manager.close()
        streamingJob = null
        clearPreferredFlowReplayCaches()
        connectedBoardId = null
        appendInfo("Disconnected from board.")
        return true
    }

    override suspend fun addWave(wave: WaveSpec): Boolean {
        stateStore.update { st ->
            st.copy(waveSpecs = st.waveSpecs + wave)
        }
        appendInfo("Added ${wave.type.name.lowercase()} wave at ${wave.frequencyHz} Hz.")
        return true
    }

    override suspend fun removeWave(waveIndex: Int): Boolean {
        stateStore.update { st ->
            st.copy(waveSpecs = st.waveSpecs.toMutableList().apply { removeAt(waveIndex) })
        }
        appendInfo("Removed wave at index $waveIndex.")
        return true
    }

    override suspend fun editWave(waveIndex: Int, wave: WaveSpec): Boolean {
        stateStore.update { st ->
            st.copy(waveSpecs = st.waveSpecs.toMutableList().apply { set(waveIndex, wave) })
        }
        appendInfo("Updated wave at index $waveIndex.")
        return true
    }

    override suspend fun startStreaming(): Boolean {
        if (streamingJob != null) {
            appendWarn("Streaming is already active for ${connectedBoardLabel("the connected board")}.")
            return true
        }

        if (!stateStore.get().connected) {
            appendError("Cannot start stream because no board is connected.")
            return false
        }

        appendInfo("Starting stream for ${connectedBoardLabel("the connected board")}...")
        manager.startStream() // Ensure streamingActive is set for synthetic
        if (!stateStore.get().streaming) {
            appendError("Stream failed to start for ${connectedBoardLabel("the connected board")}. Backend did not report an active stream state.")
            return false
        }

        streamingJob = scope.launch {
            startDataPipeline(
                onFiltered = {
                    onFiltered?.invoke(it)
                },
                onFilteredByChannel = {
                    scope.launch { _filteredFlow.emit(it) }
                },
                onBandPowers = {
                    onBandPowers?.invoke(it)
                },
                onBandPowersByChannel = {
                    scope.launch { _bandPowersFlow.emit(it) }
                },
                onFFTResult = {
                    onFFTResult?.invoke(it)
                },
                onFFTResultByChannel = {
                    scope.launch { _fftResultFlow.emit(it) }
                },
                stateStore = stateStore,
                manager = manager
            )
        }
        appendInfo("Stream started for ${connectedBoardLabel("the connected board")}.")
        return true
    }

    override suspend fun stopStreaming(): Boolean {
        if (streamingJob == null && !stateStore.get().streaming) {
            clearPreferredFlowReplayCaches()
            appendWarn("Stop stream requested, but no active stream was running for ${connectedBoardLabel("the current board")}.")
            return true
        }

        appendInfo("Stopping stream for ${connectedBoardLabel("the current board")}...")
        streamingJob?.cancel()
        streamingJob = null
        manager.stopStream()
        clearPreferredFlowReplayCaches()
        appendInfo("Stream stopped for ${connectedBoardLabel("the current board")}.")
        return true
    }

    override suspend fun enableChannel(channelId: Int): Boolean {
        manager.enableChannel(channelId)
        appendInfo("Enabled channel ${channelId + 1}.")
        return true
    }

    override suspend fun disableChannel(channelId: Int): Boolean {
        manager.disableChannel(channelId)
        appendInfo("Disabled channel ${channelId + 1}.")
        return true
    }

    override suspend fun enableRLD(channelId: Int): Boolean {
        manager.enableRLD(channelId)
        appendInfo("Enabled RLD on channel ${channelId + 1}.")
        return true
    }

    override suspend fun disableRLD(channelId: Int): Boolean {
        manager.disableRLD(channelId)
        appendInfo("Disabled RLD on channel ${channelId + 1}.")
        return true
    }

    override suspend fun verifyChannels(): Boolean {
        val state = stateStore.get()
        if (!state.connected) {
            appendWarn("Channel verification skipped because no board is connected.")
            stateStore.update { it.copy(verifiedChannels = emptyList()) }
            return false
        }

        val verifiedChannels = state.enabledChannels.sorted()
        stateStore.update { it.copy(verifiedChannels = verifiedChannels) }
        appendLog(
            severity = if (verifiedChannels.isEmpty()) SystemLogSeverity.WARN else SystemLogSeverity.INFO,
            if (verifiedChannels.isEmpty()) {
                "Verification completed, but there were no enabled channels to check on ${connectedBoardLabel("the connected board")}."
            } else {
                "Verification completed for ${verifiedChannels.size} enabled channel(s): ${verifiedChannels.joinToString { (it + 1).toString() }}."
            }
        )
        return true
    }

    override suspend fun setSamplingRateHz(rate: Int): Boolean {
        if (connectedBoardId != BoardIds.SYNTHETIC_BOARD) {
            appendError("Sampling rate can only be changed for the synthetic board.")
            throw IllegalStateException("Can only set sampling rate for synthetic board")
        }
        stateStore.update { it.copy(samplingRateHz = rate) }
        appendInfo("Sampling rate set to $rate Hz.")
        return true
    }

    override fun getState(): HardwareState = stateStore.get()

    override fun setOnFilteredListener(listener: ((DoubleArray) -> Unit)?) {
        onFiltered = listener
    }
    override fun setOnBandPowersListener(listener: ((List<BandPower>) -> Unit)?) {
        onBandPowers = listener
    }
    override fun setOnFFTResultListener(listener: ((Array<Pair<Double, Double>>) -> Unit)?) {
        onFFTResult = listener
    }

    override fun getBrainflowBoards(): List<String> {
        // Return all board names from BrainFlow's BoardIds enum, except NO_BOARD
        val boards = BoardIds.entries
            .filterNot { it == BoardIds.NO_BOARD }
            .map { it.name }
        appendInfo("Loaded ${boards.size} available boards from BrainFlow.")
        return boards
    }

    override fun getSerialPortSuggestions(boardId: String?): List<SerialPortSuggestion> {
        return serialPortDiscovery.getSuggestions(boardId)
    }

    private fun appendInfo(message: String) = appendLog(SystemLogSeverity.INFO, message)

    private fun appendWarn(message: String) = appendLog(SystemLogSeverity.WARN, message)

    private fun appendError(message: String) = appendLog(SystemLogSeverity.ERROR, message)

    private fun connectedBoardLabel(fallback: String): String {
        val boardId = connectedBoardId
        return if (boardId == null) fallback else boardId.name
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun clearPreferredFlowReplayCaches() {
        _filteredFlow.resetReplayCache()
        _bandPowersFlow.resetReplayCache()
        _fftResultFlow.resetReplayCache()
    }

    private fun appendLog(severity: SystemLogSeverity, message: String) {
        _systemLogFlow.value = (_systemLogFlow.value + SystemLogEntry(
            timestampEpochMillis = System.currentTimeMillis(),
            severity = severity,
            message = message
        )).takeLast(MAX_SYSTEM_LOG_ENTRIES)
    }
}