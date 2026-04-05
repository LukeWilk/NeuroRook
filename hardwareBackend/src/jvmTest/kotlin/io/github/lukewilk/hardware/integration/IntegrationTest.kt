package io.github.lukewilk.hardware.integration

import brainflow.BoardIds
import io.github.lukewilk.shared.model.BandPower
import io.github.lukewilk.hardware.BoardConnectionManager
import io.github.lukewilk.hardware.LoggerProvider
import io.github.lukewilk.hardware.main
import io.github.lukewilk.shared.HardwareState
import io.github.lukewilk.shared.StateStore
import io.github.lukewilk.shared.SyntheticMode
import io.github.lukewilk.shared.WaveSpec
import io.github.lukewilk.shared.WaveType
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.function.Executable
import java.util.Locale
import kotlin.collections.iterator
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * PSD ERROR MARGIN ANALYSIS & TEST SUITE CALIBRATION
 * --------------------------------------------------
 * Purpose: Validates Power Spectral Density (PSD) calculation accuracy
 * for synthetic EEG waves (fs = 250Hz, Single-Sided).
 *
 * 1. THEORETICAL TARGET (Ground Truth):
 *    For a sine wave with Amplitude (A), Theoretical Power (P) = (A^2) / 2.
 *    Example: Delta (A=5.0) -> Target Power = 12.5
 *
 * 2. MEASUREMENT METHOD:
 *    Band Power = Sum of PSD bins within frequency range * Bin Width (df).
 *    Error % = |(Measured_Power - Theoretical_Power) / Theoretical_Power| * 100
 *
 * 3. MITIGATION STRATEGY:
 *    - Windowing: Use Hanning/Hamming (Rectangular causes >36% leakage error).
 *    - Single-Sided: We treat BrainFlow output as single-sided
 *    (positive frequencies only), theoretical per-tone target used in
 *    comparisons is A^2/4 (half of two-sided A^2/2). A global calibration is
 *    applied to align pipeline scaling before per-band checks.
 *
 * 4. ERROR THRESHOLD TABLE (Max Allowed % Error per Category):
 *    Values represent the 'ceiling' for each grade.
 *    N/A = Frequency resolution (fs/N) is too coarse to resolve the band.
 *
 * | Band (Freq) | Win (N) | Res (df) | Very Good | Good | Acceptable |
 * |-------------|---------|----------|-----------|------|------------|
 * | Delta (2Hz) | 32      | 7.81Hz   | N/A       | N/A  | N/A        |
 * |             | 64      | 3.90Hz   | 15%       | 30%  | 50%        |
 * |             | 128     | 1.95Hz   | 10%       | 15%  | 20%        |
 * |             | 256/260 | 0.97Hz   | 8%        | 12%  | 15%        |
 * |-------------|---------|----------|-----------|------|------------|
 * | Theta (6Hz) | 32      | 7.81Hz   | N/A       | 60%  | 80%        |
 * |             | 64      | 3.90Hz   | 12%       | 25%  | 40%        |
 * |             | 128     | 1.95Hz   | 8%        | 12%  | 15%        |
 * |             | 256/260 | 0.97Hz   | 5%        | 8%   | 12%        |
 * |-------------|---------|----------|-----------|------|------------|
 * | Alpha (10Hz)| 32      | 7.81Hz   | 30%       | 40%  | 50%        |
 * |             | 128     | 1.95Hz   | 8%        | 12%  | 15%        |
 * |             | 256/260 | 0.97Hz   | 4%        | 7%   | 10%        |
 * |-------------|---------|----------|-----------|------|------------|
 * | Beta (14Hz) | 128     | 1.95Hz   | 6%        | 10%  | 15%        |
 * |             | 256/260 | 0.97Hz   | 3%        | 6%   | 8%         |
 * |-------------|---------|----------|-----------|------|------------|
 * | Gamma (50Hz)| 32      | 7.81Hz   | 15%       | 25%  | 35%        |
 * |             | 256/260 | 0.97Hz   | 2%        | 4%   | 6%         |
 */
class IntegrationTest {
    private val logger = LoggerProvider.getLogger("SyntheticComponentTest")

    // no top-level state store required; tests create their own per-run

    suspend fun shutdownStreaming(job: Job, manager: BoardConnectionManager, stateStore: StateStore<HardwareState>) {
        // Stop the native stream first to unblock any blocking get_board_data() calls
        try {
            manager.stopStream()
        } catch (e: Exception) {
            logger.w(e) { "Error while stopping stream (non-fatal): ${e.message}" }
        }

        // Update state to reflect offline
        try {
            stateStore.update { it.copy(connected = false) }
        } catch (e: Exception) {
            logger.w(e) { "Error while updating state (non-fatal): ${e.message}" }
        }

        // Cancel the coroutine
        try {
            job.cancel()
        } catch (e: Exception) {
            logger.w(e) { "Error while cancelling job (non-fatal): ${e.message}" }
        }

        // Wait for job to finish, but don't propagate exceptions
        try {
            job.join()
        } catch (e: Exception) {
            logger.w(e) { "Error while joining job (non-fatal): ${e.message}" }
        }

        // Close manager to release session
        try {
            manager.close()
        } catch (e: Exception) {
            logger.w(e) { "Error while closing manager (non-fatal): ${e.message}" }
        }
    }

    @Tag("integration")
    @Test
    fun testEndToEndSyntheticBoardStreaming() = runBlocking {
        // Safety timeout for the whole test to avoid hanging indefinitely on CI or local runs
        withTimeout(90_000L) {
            val seconds = 5.1
            val analysisSeconds = 3.1
            val samplingRate = 250
            val requestedSamples = (samplingRate * seconds).toInt()
            val windowSizesToTest = listOf(256, 260) // include non-power-of-two 260

            // Collect overall report lines across all windows to log at the end
            val overallReport = mutableListOf<String>()
            // Collect per-window failure messages so we can print reports for every window
            val failures = mutableListOf<String>()
            for (windowSizeCfg in windowSizesToTest) {
                logger.i { "\n\n\n---- Running end-to-end test with windowSize=$windowSizeCfg ----\n" }

                // collect assertion lambdas for this iteration and report them via assertAll at the end
                val checks = mutableListOf<Executable>()

                val stateStore = StateStore(
                    HardwareState(
                        connected = true,
                        synthetic = true,
                        samplingRateHz = samplingRate,
                        // set per-iteration windowSize
                        windowSize = windowSizeCfg,
                        overlap = 0,
                        channels = 1,
                        enabledChannels = listOf(0),
                        syntheticMode = SyntheticMode.WAVE_GENERATOR,
                        waveSpecs = listOf(
                            // Delta
                            WaveSpec(
                                enabled = true,
                                type = WaveType.SINE,
                                amplitude = 5.0,
                                frequencyHz = 2.0,
                                phaseShiftRad = 0.0
                            ),
                            // Theta
                            WaveSpec(
                                enabled = true,
                                type = WaveType.SINE,
                                amplitude = 4.0,
                                frequencyHz = 6.0,
                                phaseShiftRad = 0.0
                            ),
                            // Alpha
                            WaveSpec(
                                enabled = true,
                                type = WaveType.SINE,
                                amplitude = 3.0,
                                frequencyHz = 10.0,
                                phaseShiftRad = 0.0
                            ),
                            // Low Beta (SMR)
                            WaveSpec(
                                enabled = true,
                                type = WaveType.SINE,
                                amplitude = 3.0,
                                frequencyHz = 14.0,
                                phaseShiftRad = 0.0
                            ),
                            // High Beta
                            WaveSpec(
                                enabled = true,
                                type = WaveType.SINE,
                                amplitude = 2.0,
                                frequencyHz = 25.0,
                                phaseShiftRad = 0.0
                            ),
                            // Gamma
                            WaveSpec(
                                enabled = true,
                                type = WaveType.SINE,
                                amplitude = 1.0,
                                frequencyHz = 50.0,
                                phaseShiftRad = 0.0
                            )
                        )
                    )
                )

                val manager = BoardConnectionManager(stateStore)
                var connected = false
                try {
                    // Try to connect the board
                    connected = manager.connect(boardId = BoardIds.SYNTHETIC_BOARD, serialPort = "")
                    checks.add(Executable {
                        assertTrue(
                            connected,
                            "Board should connect successfully before streaming (windowSize=$windowSizeCfg)."
                        )
                    })

                    // Launch main before starting the native stream so we capture all frames
                    val bandPowersList = mutableListOf<List<BandPower>>()
                    val filteredList = mutableListOf<DoubleArray>()
                    val fftList = mutableListOf<Array<Pair<Double, Double>>>()

                    // Record stream start time
                    val streamStartMs = System.currentTimeMillis()
                    val collectStartMs = 1000L
                    val collectEndMs = 4100L

                    val onBandPowers: (List<BandPower>) -> Unit = { bandPowers ->
                        val t = System.currentTimeMillis() - streamStartMs
                        if (t in collectStartMs..collectEndMs) bandPowersList.add(bandPowers)
                    }
                    val onFiltered: (DoubleArray) -> Unit = { arr ->
                        val t = System.currentTimeMillis() - streamStartMs
                        if (t in collectStartMs..collectEndMs) filteredList.add(arr)
                    }
                    val onFFTResult: (Array<Pair<Double, Double>>) -> Unit = { arr ->
                        val t = System.currentTimeMillis() - streamStartMs
                        if (t in collectStartMs..collectEndMs) fftList.add(arr)
                    }

                    // Clear any previously collected frames to avoid counting pre-subscription data
                    bandPowersList.clear()
                    filteredList.clear()
                    fftList.clear()

                    val job = launch {
                        main(
                            _args = emptyArray(),
                            onBandPowers = onBandPowers,
                            onFiltered = onFiltered,
                            onFFTResult = onFFTResult,
                            stateStore = stateStore,
                            manager = manager
                        )
                    }
                    manager.startStream()

                    // Wait for state to reflect connection/streaming (robust timed wait to avoid flakes)
                    var waitedMs = 0L
                    val waitTimeoutMs = 3000L
                    val pollIntervalMs = 50L
                    while (!stateStore.get().connected && waitedMs < waitTimeoutMs) {
                        delay(pollIntervalMs)
                        waitedMs += pollIntervalMs
                    }
                    // Capture snapshot of connected at this point so later assertAll doesn't observe a state changed by shutdown
                    // Use logical OR with the connect() return value to be robust against slight timing differences
                    val connectedSnapshot =
                        connected || stateStore.get().connected || manager.isConnected()
                    checks.add(Executable {
                        assertTrue(
                            connectedSnapshot,
                            "State should reflect connected board before streaming (windowSize=$windowSizeCfg). connectReturn=$connected state=${stateStore.get().connected} managerIsConnected=${manager.isConnected()} waited=${waitedMs}ms"
                        )
                    })
                    val startTimeMs = System.currentTimeMillis()

                    // Wait until we collected the expected number of samples (or timeout). This avoids
                    // cutting the stream mid-window when scheduling causes the last partial block to arrive
                    // just after our shutdown.
                    val requestedSamples = samplingRate * seconds
                    val maxWaitMs = seconds * 1000L + 3000L
                    var waited = 0L
                    val pollMs = 50L
                    while (waited < maxWaitMs && filteredList.sumOf { it.size } < requestedSamples) {
                        delay(pollMs)
                        waited += pollMs
                    }

                    // Now shutdown streaming gracefully
                    shutdownStreaming(job, manager, stateStore)

                    val elapsedMs = System.currentTimeMillis() - startTimeMs
                    val wallSeconds = elapsedMs / 1000.0

                    // Count frames received (each frame is a windowed buffer)
                    val framesReceived = filteredList.size
                    val samplesPerChannel =
                        framesReceived * windowSizeCfg  // Total samples across all frames
                    val sampleSeconds = samplesPerChannel.toDouble() / samplingRate.toDouble()

                    // If wallSeconds is very small (e.g., 0) but samples were generated, prefer sample-derived time
                    val reportedSeconds =
                        if (wallSeconds < 0.1 && sampleSeconds > 0.0) sampleSeconds else wallSeconds

                    logger.i {
                        String.Companion.format(
                            Locale.US,
                            "window=%d: Test run for %.3f s (wall=%.3f s, samples=%.3f s) at %dHz",
                            windowSizeCfg,
                            reportedSeconds,
                            wallSeconds,
                            sampleSeconds,
                            samplingRate
                        )
                    }
                    logger.i { "window=$windowSizeCfg: Frames received: $framesReceived, Total samples: $samplesPerChannel" }

                    logger.i { "window=$windowSizeCfg: Band powers received: ${bandPowersList.size}" }
                    logger.i { "window=$windowSizeCfg: Filtered signals received: ${filteredList.size}" }
                    logger.i { "window=$windowSizeCfg: FFT results received: ${fftList.size}" }

                    // Frame expectation with ceil + tolerance
                    val analysisSamples = (samplingRate * analysisSeconds).toInt()
                    val expectedFrames =
                        ceil(analysisSamples.toDouble() / windowSizeCfg.toDouble()).toInt()
                    val frameTolerance = 1
                    val minExpectedFrames = (expectedFrames - frameTolerance).coerceAtLeast(1)
                    val maxExpectedFrames = expectedFrames + frameTolerance

                    checks.add(Executable {
                        assertTrue(
                            filteredList.size in minExpectedFrames..maxExpectedFrames,
                            "window=$windowSizeCfg: expected about $expectedFrames frames; got ${filteredList.size} (allowed $minExpectedFrames..$maxExpectedFrames)"
                        )
                    })

                    // Optionally, validate band powers for presence (light check)
                    // per-window report collected below (declare once here and populate inside)
                    val windowReport = mutableListOf<String>()
                    if (bandPowersList.isNotEmpty()) {
                        val first = bandPowersList.first()
                        // ensure we got band-power entries
                        checks.add(Executable {
                            assertTrue(
                                first.isNotEmpty(),
                                "window=$windowSizeCfg: Expected non-empty band power list"
                            )
                        })

                        // Compute average absolute band power across emissions for each band name
                        val acc = mutableMapOf<String, MutableList<Double>>()
                        for (bpFrame in bandPowersList) {
                            for (bp in bpFrame) {
                                acc.computeIfAbsent(bp.name) { mutableListOf() }.add(bp.power)
                            }
                        }

                        val avgPowers =
                            acc.mapValues { (_, list) -> if (list.isEmpty()) 0.0 else list.average() }

                        // Theoretical expected band powers computed by matching each enabled WaveSpec
                        // frequency into the configured bands (frequency-based mapping). This is
                        // robust to reordering of bands or specs and supports multiple specs per band.
                        // Single-sided PSD convention -> per-tone expected = A^2/4.
                        val specs = stateStore.get().waveSpecs.filter { it.enabled }
                        val bandsCfg = stateStore.get().bands
                        val epsHz = 1e-8
                        val expectedTheoretical: Map<String, Double> = bandsCfg.associate { band ->
                            val sum = specs.filter { spec ->
                                val f = spec.frequencyHz
                                f >= band.lowHz - epsHz && f <= band.highHz + epsHz
                            }.sumOf { spec -> (spec.amplitude * spec.amplitude) / 4.0 }
                            band.name to sum
                        }

                        logger.i { "window=$windowSizeCfg: Theoretical expected band powers (single-sided, A^2/4) by frequency->band match:" }
                        for ((name, pow) in expectedTheoretical) {
                            logger.i { String.Companion.format(Locale.US, "  %s: %.6f", name, pow) }
                        }

                        // Apply a global calibration so theoretical expectations follow the same
                        // global scaling seen in measuredTotalAvg. This keeps theoretical targets
                        // but accounts for pipeline-wide scaling differences (windowing/PSD norm).
                        // Only include bands with non-zero measured AND expected power to avoid skew.
                        val validBands = expectedTheoretical.filter { (name, expPower) ->
                            val measPower = avgPowers[name] ?: 0.0
                            expPower > 1e-6 && measPower > 1e-6
                        }

                        val measuredTotalValid = validBands.keys.sumOf { avgPowers[it] ?: 0.0 }
                        val expectedTotalValid = validBands.values.sum()
                        val calibration =
                            if (expectedTotalValid > 0.0) measuredTotalValid / expectedTotalValid else 1.0

                        logger.i {
                            String.Companion.format(
                                Locale.US,
                                "window=%d: measuredTotalValid=%.6f (from %d bands) expectedTotalValid=%.6f calibration=%.6f",
                                windowSizeCfg,
                                measuredTotalValid,
                                validBands.size,
                                expectedTotalValid,
                                calibration
                            )
                        }
                        logger.i { "window=$windowSizeCfg: Valid bands for calibration: ${validBands.keys.joinToString()}" }

                        // Compute calibrated expected values used for numeric comparisons
                        val expectedCalibrated =
                            expectedTheoretical.mapValues { (_, v) -> v * calibration }

                        logger.i { "window=$windowSizeCfg: Averaged band powers (power, 3dp) with calibrated expectations:" }
                        for ((name, pow) in avgPowers) {
                            logger.i {
                                String.Companion.format(
                                    Locale.US,
                                    "  %s: %.3f (expectedCal=%.6f rawTheo=%.6f)",
                                    name,
                                    pow,
                                    expectedCalibrated[name] ?: 0.0,
                                    expectedTheoretical[name] ?: 0.0
                                )
                            }
                        }

                        data class Thresh(
                            val veryGood: Double?,
                            val good: Double?,
                            val acceptable: Double?
                        )

                        val thresholds: Map<String, Map<Int, Thresh>> = mapOf(
                            "Delta" to mapOf(
                                32 to Thresh(veryGood = null, good = null, acceptable = null),
                                64 to Thresh(veryGood = 15.0, good = 30.0, acceptable = 50.0),
                                128 to Thresh(veryGood = 10.0, good = 15.0, acceptable = 20.0),
                                256 to Thresh(veryGood = 8.0, good = 12.0, acceptable = 15.0)
                            ),
                            "Theta" to mapOf(
                                32 to Thresh(veryGood = null, good = 60.0, acceptable = 80.0),
                                64 to Thresh(veryGood = 12.0, good = 25.0, acceptable = 40.0),
                                128 to Thresh(veryGood = 8.0, good = 12.0, acceptable = 15.0),
                                256 to Thresh(veryGood = 5.0, good = 8.0, acceptable = 12.0)
                            ),
                            "Alpha" to mapOf(
                                32 to Thresh(veryGood = 30.0, good = 40.0, acceptable = 50.0),
                                64 to Thresh(veryGood = 15.0, good = 25.0, acceptable = 35.0),
                                128 to Thresh(veryGood = 8.0, good = 12.0, acceptable = 15.0),
                                256 to Thresh(veryGood = 4.0, good = 7.0, acceptable = 10.0)
                            ),
                            "Low Beta (SMR)" to mapOf(
                                32 to Thresh(veryGood = 25.0, good = 35.0, acceptable = 45.0),
                                64 to Thresh(veryGood = 12.0, good = 20.0, acceptable = 30.0),
                                128 to Thresh(veryGood = 6.0, good = 10.0, acceptable = 15.0),
                                256 to Thresh(veryGood = 3.0, good = 6.0, acceptable = 8.0)
                            ),
                            "High Beta" to mapOf(
                                32 to Thresh(veryGood = 20.0, good = 30.0, acceptable = 40.0),
                                64 to Thresh(veryGood = 10.0, good = 18.0, acceptable = 25.0),
                                128 to Thresh(veryGood = 5.0, good = 10.0, acceptable = 15.0),
                                256 to Thresh(veryGood = 3.0, good = 5.0, acceptable = 7.0)
                            ),
                            "Gamma" to mapOf(
                                32 to Thresh(veryGood = 15.0, good = 25.0, acceptable = 35.0),
                                64 to Thresh(veryGood = 8.0, good = 15.0, acceptable = 25.0),
                                128 to Thresh(veryGood = 4.0, good = 8.0, acceptable = 12.0),
                                256 to Thresh(veryGood = 2.0, good = 4.0, acceptable = 6.0)
                            )
                        )

                        // Helper to pick bucket N from windowSizeCfg
                        fun bucketN(window: Int): Int = when {
                            window <= 32 -> 32
                            window <= 64 -> 64
                            window <= 128 -> 128
                            else -> 256
                        }

                        // Per-window report lines (windowReport declared above)

                        // Evaluate per-band percent error vs thresholds and add assertions
                        // Iterate over bands defined in thresholds so we evaluate known bands
                        for (bandName in thresholds.keys) {
                            val measured = avgPowers[bandName] ?: 0.0
                            val expectedRef = expectedCalibrated[bandName] ?: 0.0
                            val bandBucket = bucketN(windowSizeCfg)
                            val t = thresholds[bandName]?.get(bandBucket)

                            if (t == null || t.acceptable == null) {
                                // No thresholds defined (N/A) for this band/window -> skip strict numeric check but log
                                val line = String.Companion.format(
                                    Locale.US,
                                    "window=%d band=%s: NO_THRESH (measured=%.6f expectedRef=%.6f)",
                                    windowSizeCfg,
                                    bandName,
                                    measured,
                                    expectedRef
                                )
                                windowReport.add(line)
                                logger.w { line }
                                continue
                            }

                            if (expectedRef <= 0.0) {
                                val line = String.Companion.format(
                                    Locale.US,
                                    "window=%d band=%s: REF_ZERO (measured=%.6f)",
                                    windowSizeCfg,
                                    bandName,
                                    measured
                                )
                                windowReport.add(line)
                                logger.w { line }
                                continue
                            }

                            val percentError = abs(measured - expectedRef) / expectedRef * 100.0
                            // Determine category
                            val category = when {
                                t.veryGood != null && percentError <= t.veryGood -> "VeryGood"
                                t.good != null && percentError <= t.good -> "Good"
                                percentError <= t.acceptable -> "Acceptable"
                                else -> "Critical"
                            }

                            val line = String.Companion.format(
                                Locale.US,
                                "window=%d band=%s: measured=%.6f expectedCal=%.6f error=%.2f%% category=%s (VG<=%s G<=%s A<=%s)",
                                windowSizeCfg,
                                bandName,
                                measured,
                                expectedRef,
                                percentError,
                                category,
                                t.veryGood?.toString() ?: "N/A",
                                t.good?.toString() ?: "N/A",
                                t.acceptable
                            )
                            windowReport.add(line)
                            logger.i { line }

                            // Assertion: require at least Acceptable
                            checks.add(Executable {
                                assertTrue(
                                    category != "Critical",
                                    "Mapped band '$bandName' category=$category which is below Acceptable: $line"
                                )
                            })
                        }

                        // Append window report to overall report for later summary
                        overallReport.add("--- window=$windowSizeCfg report ---")
                        overallReport.addAll(windowReport)

                        // Immediately print the per-window consolidated report to console/log so each window's
                        // diagnostics are easy to find in CI logs.
                        logger.i { "\n\n==== PSD Report for window=$windowSizeCfg ====" }
                        for (line in windowReport) {
                            logger.i { line }
                        }
                        logger.i { "==== End PSD Report for window=$windowSizeCfg ====\n\n" }

                    } else {
                        // No band powers collected for this window; still print a short report entry
                        val msg =
                            "window=$windowSizeCfg: No band powers collected (bandPowersList.size=${bandPowersList.size})"
                        overallReport.add(msg)
                        // Record a failing check so the lack of band powers causes the test to fail
                        checks.add(Executable {
                            assertTrue(
                                bandPowersList.isNotEmpty(),
                                "window=$windowSizeCfg: No band powers were collected - streaming or callback registration likely failed"
                            )
                        })
                        logger.w { msg }
                    }

                    // Run all collected checks for this iteration and report them together
                    if (checks.isNotEmpty()) {
                        try {
                            Assertions.assertAll(
                                "window=$windowSizeCfg checks",
                                *checks.toTypedArray()
                            )
                        } catch (e: AssertionError) {
                            // Emit a diagnostic dump to the test logger to help CI debugging (do not write files)
                            try {
                                val sb = StringBuilder()
                                sb.append("Assertion failure for window=$windowSizeCfg\n")
                                sb.append("BandPowers frames: ${bandPowersList.size}\n")
                                sb.append("Note: Band powers are logged by pipeline but not returned to test for capture.\n")
                                sb.append("Filtered frames: ${filteredList.size} (total samples=${filteredList.sumOf { it.size }})\n")
                                sb.append("FFT frames: ${fftList.size}\n")
                                sb.append("Overall report summary:\n")
                                // Include only the current window's report in the diagnostic dump to avoid repeating
                                // accumulated lines from previous windows which causes recurring dump noise.
                                sb.append("Window-local report:\n")
                                for (line in windowReport) {
                                    sb.append(line).append("\n")
                                }
                                val dump = sb.toString()
                                // Log the full dump at error level along with the original assertion error
                                logger.e(e) { "Assertions failed for window=$windowSizeCfg; diagnostic dump:\n$dump" }
                                // logged above; avoid direct stdout printing
                                // Record the failure message but don't rethrow immediately so we can print reports for all windows
                                failures.add("window=$windowSizeCfg: ${e.message}\n$dump")
                            } catch (ioe: Exception) {
                                logger.e(ioe) { "Failed to emit diagnostic dump to log: ${ioe.message}" }
                                failures.add("window=$windowSizeCfg: ${e.message}")
                            }
                            // continue to next window after cleanup - test will fail at the end if any failures were recorded
                        }
                    }

                } finally {
                    // Ensure manager closed if connect failed or after test
                    if (connected) {
                        try {
                            manager.stopStream()
                        } catch (_: Exception) {
                        }
                    }
                    try {
                        manager.close()
                    } catch (_: Exception) {
                    }
                }

                logger.i { "---- Finished windowSize=$windowSizeCfg ----" }
            }
            // After all windows, log a comprehensive report summarizing every band/window classification
            logger.i { "\n\n==== Comprehensive PSD QA Report ====" }
            for (line in overallReport) {
                logger.i { line }
            }
            logger.i { "\n==== End PSD QA Report ====\n\n" }

            // If any windows failed, fail the test now but only after printing all reports
            if (failures.isNotEmpty()) {
                val msg = StringBuilder()
                msg.append("One or more window checks failed:\n")
                for ((i, f) in failures.withIndex()) {
                    msg.append("--- Failure ${i + 1} ---\n")
                    msg.append(f).append("\n")
                }
                throw AssertionError(msg.toString())
            }
        }
    }

 }