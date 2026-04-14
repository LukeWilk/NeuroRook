package io.github.lukewilk

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.assertEquals

/**
 * Verifies Android runtime metadata that is only available on device or emulator.
 */
@RunWith(AndroidJUnit4::class)
class AndroidAppInstrumentedTest {
    @Test
    fun targetContextUsesTheApplicationPackageName() {
        // Confirms the installed target context resolves the expected application package at runtime.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("io.github.lukewilk", appContext.packageName)
    }

    @Test
    fun targetContextExposesTheBrandedAppName() {
        // Verifies the installed application presents the expected user-facing label.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("NeuroRook", appContext.getString(R.string.app_name))
    }
}

