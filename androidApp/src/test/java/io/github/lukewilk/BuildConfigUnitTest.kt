package io.github.lukewilk

import org.junit.Test

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/**
 * Verifies generated Android build metadata that the app depends on at runtime.
 */
class BuildConfigUnitTest {
    @Test
    fun `build config exposes the production application identity`() {
        // Guards the generated application id used by deeplinks, manifests, and Android test lookups.
        assertEquals("io.github.lukewilk", BuildConfig.APPLICATION_ID)
    }

    @Test
    fun `build config carries a non-empty version from gradle properties`() {
        // Ensures release metadata is wired into the Android module instead of leaving template defaults.
        assertTrue(BuildConfig.VERSION_NAME.isNotBlank())
        assertTrue(BuildConfig.VERSION_CODE > 0)
    }
}
