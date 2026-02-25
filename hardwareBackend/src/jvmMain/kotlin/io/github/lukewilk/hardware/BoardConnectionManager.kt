package io.github.lukewilk.hardware

import brainflow.BoardIds
import brainflow.BoardShim
import brainflow.BrainFlowInputParams
import co.touchlab.kermit.Logger
import io.github.lukewilk.shared.HardwareState
import io.github.lukewilk.shared.StateStore
import io.github.lukewilk.shared.SyntheticMode
import io.github.lukewilk.shared.WaveSpec as SharedWaveSpec
import io.github.lukewilk.shared.WaveType as SharedWaveType
import io.github.lukewilk.hardware.synthetic.SyntheticDataGenerator
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Job


/**
 * Manages connection/disconnection to BrainFlow-compatible board.
 */
class BoardConnectionManager(
    val stateStore: StateStore<HardwareState>,
    private val boardShimFactory: (BoardIds, BrainFlowInputParams) -> BoardShim =
        { boardId, params -> BoardShim(boardId, params) },
    private val samplingRateProvider: (BoardIds) -> Int =
        { boardId -> BoardShim.get_sampling_rate(boardId) }
) {
    private val logger = LoggerProvider.getLogger("BoardConnectionManager")

    private var boardShim: BoardShim? = null
    val state = stateStore.state
    private val streamingActive = AtomicBoolean(false)
    private val streamingJobRef = AtomicReference<Job?>(null)

    fun isStreaming(): Boolean = streamingActive.get()

    fun getBoardShim(): BoardShim? = boardShim

    // Allow caller to hint that this is synthetic (useful during connect while state not updated yet)
    fun getNumberOfChannels(boardId: BoardIds, syntheticHint: Boolean = false): Int {
        return when {
            syntheticHint || stateStore.get().synthetic -> 16 // Synthetic board has 16 EEG channels
            else -> try {
                val channels = BoardShim.get_eeg_channels(boardId).size
                if (channels > 0) channels else 16
            } catch (e: Exception) {
                logger.e(e) { "Error getting EEG channels for boardId=$boardId: ${e.message}" }
                throw e
            }
        }
    }

    fun enableChannel(index: Int) {
        logger.d { "enableChannel($index) called (top of function)" }
        try {
            boardShim?.let { shim ->
                if (stateStore.get().synthetic.not()) {
                    shim.config_board("enable_channel $index")
                }
            }
            stateStore.update {
                if (index in it.enabledChannels) it
                else it.copy(enabledChannels = (it.enabledChannels + index).distinct().sorted())
            }
            logger.d { "After enableChannel($index) state update: ${stateStore.get().enabledChannels}" }
            logger.i { "After enableChannel($index): enabledChannels = ${stateStore.get().enabledChannels}" }
        } catch (e: Exception) {
            logger.e(e) { "Failed to enable channel $index on board: ${e.message}" }
        }
    }

    fun disableChannel(index: Int) {
        try {
            boardShim?.let { shim ->
                if (stateStore.get().synthetic.not()) {
                    shim.config_board("disable_channel $index")
                }
            }
            stateStore.update {
                it.copy(enabledChannels = it.enabledChannels.filter { ch -> ch != index })
            }
        } catch (e: Exception) {
            logger.e(e) { "Failed to disable channel $index on board: ${e.message}" }
        }
    }

    fun enableRLD(index: Int) {
        try {
            boardShim?.let { shim ->
                if (stateStore.get().synthetic.not()) {
                    shim.config_board("enable_rld $index")
                }
            }
            stateStore.update {
                if (index in it.rldEnabled) it
                else it.copy(rldEnabled = (it.rldEnabled + index).distinct().sorted())
            }
        } catch (e: Exception) {
            logger.e(e) { "Failed to enable RLD for channel $index on board: ${e.message}" }
        }
    }

    fun disableRLD(index: Int) {
        try {
            boardShim?.let { shim ->
                if (stateStore.get().synthetic.not()) {
                    shim.config_board("disable_rld $index")
                }
            }
            stateStore.update {
                it.copy(rldEnabled = it.rldEnabled.filter { ch -> ch != index })
            }
        } catch (e: Exception) {
            logger.e(e) { "Failed to disable RLD for channel $index on board: ${e.message}" }
        }
    }

    fun connect(boardId: BoardIds, serialPort: String): Boolean {
        try {
            val params = BrainFlowInputParams()
            params.serial_port = serialPort
            logger.i { "Attempting to connect: " +
                    "boardId=$boardId, " +
                    "serialPort=$serialPort, " +
                    "params=[serial_port=${params.serial_port}," +
                    " ip_address=${params.ip_address}," +
                    " mac_address=${params.mac_address}]" }
            boardShim = boardShimFactory(boardId, params)
            val isSyntheticBoard = boardId == BoardIds.SYNTHETIC_BOARD
            if (isSyntheticBoard) {
                // Create a lightweight synthetic BoardShim subclass which returns generated data without native session
                boardShim = object : BoardShim(boardId, params) {
                    override fun get_board_data(num_datapoints: Int): Array<DoubleArray> {
                        val st = stateStore.get()
                        val samples = if (num_datapoints <= 0) 1 else num_datapoints
                        return SyntheticDataGenerator.generate(st, samples)
                    }

                    override fun get_current_board_data(num_datapoints: Int): Array<DoubleArray> {
                        val st = stateStore.get()
                        val samples = if (num_datapoints <= 0) 1 else num_datapoints
                        return SyntheticDataGenerator.generate(st, samples)
                    }

                    override fun get_board_data_count(): Int {
                        // Report some available data (windowSize) so data acquisition don't block
                        return stateStore.get().windowSize.coerceAtLeast(1)
                    }
                }
            } else {
                boardShim = boardShimFactory(boardId, params)
            }
            // For synthetic board, avoid calling native prepare_session which can conflict with other tests
            if (!isSyntheticBoard) {
                try {
                    boardShim?.prepare_session()
                } catch (e: Exception) {
                    // If another board is already created in this JVM, try to release and retry once
                    if (e is brainflow.BrainFlowError && e.message?.contains("ANOTHER_BOARD_IS_CREATED_ERROR") == true) {
                        logger.w(e) { "prepare_session failed with ANOTHER_BOARD_IS_CREATED_ERROR, attempting release and retry" }
                        try {
                            boardShim?.release_session()
                        } catch (re: Exception) {
                            logger.w(re) { "release_session during retry failed: ${re.message}" }
                        }
                        // retry once
                        boardShim = boardShimFactory(boardId, params)
                        boardShim?.prepare_session()
                    } else {
                        throw e
                    }
                }
            } else {
                // synthetic board: no native session required
                logger.i { "Synthetic board selected; skipping native prepare_session." }
            }
            // Streaming is now started separately
            val detectedSamplingRate = try { samplingRateProvider(boardId) } catch (_: Exception) { 250 }
            val detectedChannels = try { getNumberOfChannels(boardId, isSyntheticBoard) } catch (e: Exception) {
                 logger.e(e) { "Failed to get number of channels, setting channels=0" }
                 0
             }
            val prevEnabledChannels = stateStore.get().enabledChannels
            val prevRldEnabled = stateStore.get().rldEnabled
            val prevSampling = stateStore.get().samplingRateHz
            val prevChannels = stateStore.get().channels
            stateStore.update { it.copy(
                connected = true,
                synthetic = isSyntheticBoard,
                samplingRateHz = if (prevSampling > 0) prevSampling else detectedSamplingRate,
                channels = if (prevChannels > 0) prevChannels else detectedChannels,
                enabledChannels = if (prevEnabledChannels.isNotEmpty()) prevEnabledChannels else emptyList(),
                rldEnabled = if (prevRldEnabled.isNotEmpty()) prevRldEnabled else emptyList()
            ) }
            logger.d { "After connect state update, enabledChannels = ${stateStore.get().enabledChannels}" }
            logger.i { "After connect: enabledChannels = ${stateStore.get().enabledChannels}" }
            return true
        } catch (e: Exception) {
            logger.e(e) { "BrainFlow connection error on $serialPort: ${e.message}" }
            stateStore.update { it.copy(
                connected = false,
                synthetic = false,
                samplingRateHz = 0,
                channels = 0,
                enabledChannels = emptyList(),
                rldEnabled = emptyList()
            ) }
        }
        return false
    }

    fun startStream(bufferSize: Int = 45000, streamerParams: String = "") {
        try {
            // If synthetic board, don't call native start_stream which requires a real BoardShim session
            if (stateStore.get().synthetic) {
                streamingActive.set(true)
                logger.i { "Synthetic board: simulated startStream (no native call)" }
                return
            }
            // For real boards, attempt to start native stream first, then mark streaming active on success
            try {
                boardShim?.start_stream(bufferSize, streamerParams)
                streamingActive.set(true)
                logger.i { "Started stream with bufferSize=$bufferSize, streamerParams='$streamerParams'" }
            } catch (e: Exception) {
                streamingActive.set(false)
                throw e
            }
        } catch (e: Exception) {
            logger.e(e) { "Failed to start stream: ${e.message}" }
        }
    }

    fun stopStream() {
        // Immediately mark as not connected so loops exit quickly
        try {
            stateStore.update { it.copy(connected = false) }
        } catch (e: Exception) {
            logger.e(e) { "Failed to update stateStore before stopStream: ${e.message}" }
        }
        // Mark streaming not active immediately
        streamingActive.set(false)
        // cancel registered job if present
        cancelRegisteredStreamingJob()
        try {
            if (stateStore.get().synthetic) {
                logger.i { "Synthetic board: simulated stopStream (no native call)" }
                return
            }
            // Try to stop native stream on a background thread but wait briefly for it to finish
            val t = Thread {
                try {
                    boardShim?.stop_stream()
                    logger.i { "Stopped stream (native)" }
                } catch (e: Exception) {
                    logger.e(e) { "Failed to stop native stream: ${e.message}" }
                }
            }
            t.isDaemon = true
            t.start()
            try {
                t.join(500) // wait up to 500ms
            } catch (ie: InterruptedException) {
                logger.w { "Interrupted while waiting for native stop_stream thread" }
            }
            if (t.isAlive) {
                logger.w { "Native stop_stream did not finish within timeout, interrupting thread" }
                try { t.interrupt() } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            logger.e(e) { "Failed to schedule stop_stream: ${e.message}" }
        }
    }

    fun isConnected(): Boolean = state.value.connected

    fun close() {
        try {
            stopStream() // Ensure stream is stopped before releasing session
            if (!stateStore.get().synthetic) {
                try { boardShim?.release_session() } catch (_: Exception) {}
            } else {
                logger.i { "Synthetic board: skipping release_session" }
            }
        } catch (_: Exception) {}
        stateStore.update { it.copy(connected = false, synthetic = false, samplingRateHz = 0) }
        boardShim = null
    }

    fun generateSyntheticData(samples: Int): Array<DoubleArray> {
        val st = stateStore.get()
        return if (st.synthetic && st.syntheticMode == SyntheticMode.WAVE_GENERATOR) {
            SyntheticDataGenerator.generate(st, samples)
        } else {
            Array(st.channels) { DoubleArray(samples) }
        }
    }

    // Convenience: set the synthetic mode
    fun setSyntheticMode(mode: SyntheticMode) {
        stateStore.update { it.copy(syntheticMode = mode) }
    }

    // Convenience: update a wave spec by parameters
    fun setWaveSpec(index: Int, enabled: Boolean, type: SharedWaveType, amplitude: Double, frequencyHz: Double, phaseShiftRad: Double) {
        stateStore.update { st ->
            val list = st.waveSpecs.toMutableList()
            if (index in list.indices) {
                list[index] = SharedWaveSpec(enabled = enabled, type = type, amplitude = amplitude, frequencyHz = frequencyHz, phaseShiftRad = phaseShiftRad)
            }
            st.copy(waveSpecs = list)
        }
    }

    // Bands management: up to 10 bands. Overlapping allowed.
    fun setBands(bands: List<io.github.lukewilk.shared.Band>) {
        val trimmed = if (bands.size > 10) bands.take(10) else bands
        stateStore.update { it.copy(bands = trimmed) }
    }

    fun addBand(band: io.github.lukewilk.shared.Band) {
        stateStore.update { st ->
            val list = (st.bands + band).take(10)
            st.copy(bands = list)
        }
    }

    fun removeBand(name: String) {
        stateStore.update { st ->
            val list = st.bands.filterNot { it.name == name }
            st.copy(bands = list)
        }
    }

    fun registerStreamingJob(job: Job?) {
        streamingJobRef.set(job)
        if (job == null) {
            logger.d { "registerStreamingJob: cleared" }
        } else {
            logger.d { "registerStreamingJob: job registered=$job" }
        }
    }

    private fun cancelRegisteredStreamingJob() {
        try {
            val j = streamingJobRef.getAndSet(null)
            if (j != null) {
                logger.d { "cancelRegisteredStreamingJob: cancelling job $j" }
                j.cancel()
                try {
                    kotlinx.coroutines.runBlocking {
                        val waited = kotlinx.coroutines.withTimeoutOrNull(500) {
                            try {
                                j.join()
                                true
                            } catch (_: Exception) { false }
                        }
                        if (waited == true) logger.d { "cancelRegisteredStreamingJob: job joined" } else logger.w { "cancelRegisteredStreamingJob: job did not finish within timeout" }
                    }
                } catch (re: Exception) {
                    logger.w { "cancelRegisteredStreamingJob: runBlocking join failed: ${re.message}" }
                }
            } else {
                logger.d { "cancelRegisteredStreamingJob: no job registered" }
            }
        } catch (_: Exception) {}
    }
}
