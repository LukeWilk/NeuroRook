package io.github.lukewilk.hardware

import io.github.lukewilk.hardware.pipeline.startDataPipeline
import io.github.lukewilk.shared.model.BandPower
import io.github.lukewilk.shared.HardwareState
import io.github.lukewilk.shared.logging.LoggerProvider
import io.github.lukewilk.shared.StateStore
import kotlinx.coroutines.*

// Logger for this module
val logger = LoggerProvider.getLogger("Main")

// Suspending main function for pipeline orchestration and testability
// Accepts optional callbacks and state/manager for flexibility
suspend fun main(
    _args: Array<String>? = null, // Optional command-line arguments
    onBandPowers: ((List<BandPower>) -> Unit)? = null, // Callback for band power results
    onFiltered: ((DoubleArray) -> Unit)? = null, // Callback for filtered data
    onFFTResult: ((Array<Pair<Double, Double>>) -> Unit)? = null, // Callback for FFT results
    stateStore: StateStore<HardwareState>? = null, // Optional custom state store
    manager: BoardConnectionManager? = null, // Optional custom board manager
    scope: CoroutineScope = CoroutineScope(Dispatchers.Default) // Coroutine scope for launching coroutines
) {
    val actualStateStore = stateStore ?: StateStore(HardwareState())
    val actualManager = manager ?: BoardConnectionManager(actualStateStore)
    scope.launch {
        startDataPipeline(
            onBandPowers = onBandPowers,
            onFiltered = onFiltered,
            onFFTResult = onFFTResult,
            stateStore = actualStateStore,
            manager = actualManager
        )
    }.join() // Wait for the pipeline to complete
}

// Standard JVM entry point for runner compatibility
// Launches the suspending main in a coroutine
fun main(args: Array<String>) {
    val job = Job()
    val scope = CoroutineScope(Dispatchers.Default + job)
    runBlocking {
        val mainJob = launch { main(_args = args, scope = scope) }
        kotlinx.coroutines.delay(500) // Run for 500ms
        mainJob.cancelAndJoin() // Cancel and wait for completion
    }
}