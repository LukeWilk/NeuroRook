package io.github.lukewilk.shared.logging

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the Android logger configuration lookup uses the Android default behavior on device or emulator.
 */
@RunWith(AndroidJUnit4::class)
class LoggerProviderAndroidInstrumentedTest {
    @Test
    fun readLogLevelFromConfigReturnsNullOnAndroid() {
        // Calls the generated Android top-level function so coverage can land on LoggerProvider.android.kt line 3.
        val loggerProviderClass = Class.forName("io.github.lukewilk.shared.logging.LoggerProvider_androidKt")
        val result = loggerProviderClass.getDeclaredMethod("readLogLevelFromConfig").invoke(null)

        assertNull(result)
    }
}


