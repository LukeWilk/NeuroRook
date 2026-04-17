package io.github.lukewilk.hardware.pipeline

import io.github.lukewilk.shared.BandpassConfig
import io.github.lukewilk.shared.FilterConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Direct branch tests for `DataPipeline` utility logic.
 */
class DataPipelineGuardTest {

    @Test
    fun `should log filter config change covers all branches`() {
        val config = FilterConfig(bandpass = null, bandstopFilters = emptyList())
        val changedConfig = FilterConfig(
            bandpass = BandpassConfig(1.0, 20.0, 2, 250, 0, 0.1),
            bandstopFilters = emptyList()
        )

        assertTrue(shouldLogFilterConfigChange(true, config, config))
        assertTrue(!shouldLogFilterConfigChange(false, config, config))
        assertTrue(shouldLogFilterConfigChange(false, changedConfig, config))
    }

    @Test
    fun `resolve pipeline sampling rate covers explicit and fallback values`() {
        assertEquals(500.0, resolvePipelineSamplingRate(500))
        assertEquals(250.0, resolvePipelineSamplingRate(0))
        assertEquals(250.0, resolvePipelineSamplingRate(-5))
    }

    @Test
    fun `rethrow pipeline failure covers null and non null failures`() {
        rethrowPipelineFailure(null)

        val failure = assertFailsWith<IllegalStateException> {
            rethrowPipelineFailure(IllegalStateException("boom"))
        }

        assertEquals("boom", failure.message)
    }
}
