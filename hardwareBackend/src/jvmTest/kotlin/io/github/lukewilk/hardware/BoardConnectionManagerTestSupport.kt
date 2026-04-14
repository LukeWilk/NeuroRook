package io.github.lukewilk.hardware

import brainflow.BoardIds
import brainflow.BoardShim
import brainflow.BrainFlowInputParams
import io.github.lukewilk.shared.HardwareState
import io.github.lukewilk.shared.StateStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertTrue
import org.junit.After

/**
 * Shared fixture for `BoardConnectionManager` unit tests so split suites keep one setup style.
 */
@Suppress("unused")
abstract class BoardConnectionManagerTestSupport {
    protected val stateStore = StateStore(HardwareState())
    protected val manager = BoardConnectionManager(stateStore)

    /** Reads the private registered streaming job reference so shutdown tests can assert cleanup happened. */
    @Suppress("UNCHECKED_CAST")
    protected fun registeredStreamingJob(manager: BoardConnectionManager): Job? {
        val field = BoardConnectionManager::class.java.getDeclaredField("streamingJobRef").apply { isAccessible = true }
        return (field.get(manager) as AtomicReference<Job?>).get()
    }

    /** Invokes the private cancellation helper to assert the registered job reference is cleared on every branch. */
    protected fun invokeCancelRegisteredStreamingJob(manager: BoardConnectionManager) {
        val method = BoardConnectionManager::class.java.getDeclaredMethod("cancelRegisteredStreamingJob")
        method.isAccessible = true
        method.invoke(manager)
    }

    /** Replaces the private board shim for tests that need to simulate missing or swapped sessions directly. */
    protected fun setBoardShim(manager: BoardConnectionManager, shim: BoardShim?) {
        val field = BoardConnectionManager::class.java.getDeclaredField("boardShim").apply { isAccessible = true }
        field.set(manager, shim)
    }

    /** Creates a real-board manager with a predictable sampling rate so lifecycle tests can focus on behavior. */
    protected fun realBoardManager(
        initialState: HardwareState = HardwareState(),
        boardShimFactory: (BoardIds, BrainFlowInputParams) -> BoardShim
    ): BoardConnectionManager = BoardConnectionManager(
        stateStore = StateStore(initialState),
        boardShimFactory = boardShimFactory,
        samplingRateProvider = { 128 }
    )

    /** Connects a manager through the real-board path and fails fast when test setup unexpectedly breaks. */
    protected fun connectRealBoard(
        manager: BoardConnectionManager,
        boardId: BoardIds = BoardIds.NEUROPAWN_KNIGHT_BOARD,
        serialPort: String = ""
    ) {
        assertTrue(
            manager.connect(boardId = boardId, serialPort = serialPort),
            "Expected the real-board test fixture to connect successfully"
        )
    }

    /** Ensures each split suite releases native and synthetic resources after every test. */
    @After
    fun tearDownBoardConnectionManager() {
        runBlocking {
            manager.close()
        }
    }
}
