package io.github.lukewilk.hardware.api

import brainflow.BoardIds
import io.github.lukewilk.hardware.BoardConnectionManager
import io.github.lukewilk.hardware.pipeline.startDataPipeline
import io.github.lukewilk.shared.api.BackendApi
import io.github.lukewilk.shared.HardwareState
import io.github.lukewilk.shared.WaveSpec
import io.github.lukewilk.shared.model.BandPower
import io.github.lukewilk.shared.StateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class HardwareBackendApi : BackendApi {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val stateStore = StateStore(HardwareState())
    private val manager = BoardConnectionManager(stateStore)
    private var connectedBoardId: BoardIds? = null

    private val _filteredFlow = MutableSharedFlow<DoubleArray>(replay = 1)
    override val filteredFlow: Flow<DoubleArray> = _filteredFlow.asSharedFlow()
    private val _bandPowersFlow = MutableSharedFlow<List<BandPower>>(replay = 1)
    override val bandPowersFlow: Flow<List<BandPower>> = _bandPowersFlow.asSharedFlow()
    private val _fftResultFlow = MutableSharedFlow<Array<Pair<Double, Double>>>(replay = 1)
    override val fftResultFlow: Flow<Array<Pair<Double, Double>>> = _fftResultFlow.asSharedFlow()

    private var streamingJob: kotlinx.coroutines.Job? = null

    private var onFiltered: ((DoubleArray) -> Unit)? = null
    private var onBandPowers: ((List<BandPower>) -> Unit)? = null
    private var onFFTResult: ((Array<Pair<Double, Double>>) -> Unit)? = null

    override suspend fun connect(boardId: String): Boolean {
        // Use BrainFlow's BoardIds enum to resolve board names dynamically
        val id = BoardIds.values().find { it.name.equals(boardId, ignoreCase = true) } ?: BoardIds.NO_BOARD
        connectedBoardId = id
        return manager.connect(id, "")
    }

    override suspend fun disconnect(): Boolean {
        manager.close()
        connectedBoardId = null
        return true
    }

    override suspend fun addWave(wave: WaveSpec): Boolean {
        stateStore.update { st ->
            st.copy(waveSpecs = st.waveSpecs + wave)
        }
        return true
    }

    override suspend fun removeWave(waveIndex: Int): Boolean {
        stateStore.update { st ->
            st.copy(waveSpecs = st.waveSpecs.toMutableList().apply { removeAt(waveIndex) })
        }
        return true
    }

    override suspend fun editWave(waveIndex: Int, wave: WaveSpec): Boolean {
        stateStore.update { st ->
            st.copy(waveSpecs = st.waveSpecs.toMutableList().apply { set(waveIndex, wave) })
        }
        return true
    }

    override suspend fun startStreaming(): Boolean {
        if (streamingJob != null) return true
        manager.startStream() // Ensure streamingActive is set for synthetic
        streamingJob = scope.launch {
            startDataPipeline(
                onFiltered = {
                    scope.launch { _filteredFlow.emit(it) }
                    onFiltered?.invoke(it)
                },
                onBandPowers = {
                    scope.launch { _bandPowersFlow.emit(it) }
                    onBandPowers?.invoke(it)
                },
                onFFTResult = {
                    scope.launch { _fftResultFlow.emit(it) }
                    onFFTResult?.invoke(it)
                },
                stateStore = stateStore,
                manager = manager
            )
        }
        return true
    }

    override suspend fun stopStreaming(): Boolean {
        streamingJob?.cancel()
        streamingJob = null
        return true
    }

    override suspend fun enableChannel(channelId: Int): Boolean {
        stateStore.update { st ->
            st.copy(enabledChannels = (st.enabledChannels + channelId).distinct())
        }
        return true
    }

    override suspend fun disableChannel(channelId: Int): Boolean {
        stateStore.update { st ->
            st.copy(enabledChannels = st.enabledChannels.filter { it != channelId })
        }
        return true
    }

    override suspend fun enableRLD(): Boolean {
        stateStore.update { st ->
            st.copy(rldEnabled = listOf(0)) // Example: enable RLD on channel 0
        }
        return true
    }

    override suspend fun disableRLD(): Boolean {
        stateStore.update { st ->
            st.copy(rldEnabled = emptyList())
        }
        return true
    }

    override suspend fun setSamplingRateHz(rate: Int): Boolean {
        if (connectedBoardId != BoardIds.SYNTHETIC_BOARD) {
            throw IllegalStateException("Can only set sampling rate for synthetic board")
        }
        stateStore.update { it.copy(samplingRateHz = rate) }
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
}