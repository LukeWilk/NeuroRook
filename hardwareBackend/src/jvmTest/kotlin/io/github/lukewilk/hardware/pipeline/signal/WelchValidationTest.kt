package io.github.lukewilk.hardware.pipeline.signal
import kotlin.test.Test
import kotlin.test.assertFailsWith
/**
 * Argument-validation tests for `Welch.kt`.
 */
class WelchValidationTest {
    /** Verifies Welch PSD rejects empty input. */
    @Test
    fun `compute welch psd throws on empty input`() {
        assertFailsWith<IllegalArgumentException> {
            computeWelchPSD(DoubleArray(0), WelchConfig())
        }
    }
    /** Verifies band power rejects invalid bounds and empty PSD inputs. */
    @Test
    fun `band power rejects invalid inputs`() {
        val psd = arrayOf(0.0 to 1.0)
        assertFailsWith<IllegalArgumentException> { bandPower(psd, -1.0, 1.0) }
        assertFailsWith<IllegalArgumentException> { bandPower(psd, 2.0, 1.0) }
        assertFailsWith<IllegalArgumentException> { bandPower(arrayOf(), 0.0, 1.0) }
    }
}
