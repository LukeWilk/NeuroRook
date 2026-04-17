package io.github.lukewilk.hardware.pipeline

import kotlinx.coroutines.Job
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Guard-branch tests for `Buffer.kt`.
 */
class BufferGuardTest {

    @Test
    fun `should cancel buffering evaluates overrides and job state`() {
        val activeJob = Job()
        val cancelledJob = Job().apply { cancel() }

        assertTrue(shouldCancelBuffering(null) { true })
        assertFalse(shouldCancelBuffering(activeJob, overrideCheck = null))
        assertTrue(shouldCancelBuffering(cancelledJob, overrideCheck = null))
        assertFalse(shouldCancelBuffering(job = null, overrideCheck = null))

        activeJob.cancel()
    }

    @Test
    fun `slide buffer ignores extra removals after the deque is empty`() {
        val buffer = ArrayDeque(listOf(1.0, 2.0))
        slideBuffer(buffer, hop = 4)
        assertTrue(buffer.isEmpty())
    }

    @Test
    fun `validate stored window rejects negative overlap`() {
        assertFailsWith<IllegalArgumentException> {
            validateStoredWindow(windowSize = 8, overlap = -1, isPowerOfTwo = true)
        }
    }
}
