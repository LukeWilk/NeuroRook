package io.github.lukewilk.hardware.pipeline

import kotlinx.coroutines.Job
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Direct helper and guard-branch tests for `Buffer.kt`.
 */
class BufferHelperTest {

    /** Verifies cancellation checks honor overrides, cancelled jobs, active jobs, and missing jobs. */
    @Test
    fun `should cancel buffering evaluates overrides and job state`() {
        val activeJob = Job()
        val cancelledJob = Job().apply { cancel() }

        // A custom override should win over the default job-state logic.
        assertTrue(shouldCancelBuffering(null) { true })
        // An active job should keep buffering alive when no override is installed.
        assertFalse(shouldCancelBuffering(activeJob, overrideCheck = null))
        // A cancelled job should request buffering cancellation.
        assertTrue(shouldCancelBuffering(cancelledJob, overrideCheck = null))
        // A missing job should be treated as non-cancelled by default.
        assertFalse(shouldCancelBuffering(job = null, overrideCheck = null))

        activeJob.cancel()
    }

    /** Verifies extra slide steps after the deque is empty are ignored safely. */
    @Test
    fun `slide buffer ignores extra removals after the deque is empty`() {
        val buffer = ArrayDeque(listOf(1.0, 2.0))

        // Sliding farther than the available data should empty the buffer without throwing.
        slideBuffer(buffer, hop = 4)

        assertTrue(buffer.isEmpty())
    }

    /** Verifies stored-window validation rejects negative overlap values as invalid input. */
    @Test
    fun `validate stored window rejects negative overlap`() {
        // Negative overlap would make the hop larger than the window and should be rejected.
        assertFailsWith<IllegalArgumentException> {
            validateStoredWindow(windowSize = 8, overlap = -1, isPowerOfTwo = true)
        }
    }
}
