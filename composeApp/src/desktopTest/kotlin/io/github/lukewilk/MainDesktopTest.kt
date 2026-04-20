package io.github.lukewilk

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Desktop launch tests focused on real behavior rather than extracted helper layers.
 */
class MainDesktopTest {
    @Test
    fun `run desktop main wires look and feel before launching the compose host`() {
        // Verifies the desktop bootstrap preserves the required order: configure the shell first, then launch Compose.
        val events = mutableListOf<String>()
        runDesktopMain(
            configureLookAndFeel = { events += "configure" },
            launchApp = { events += "launch" }
        )
        assertEquals(listOf("configure", "launch"), events)
    }

    @Test
    fun `configure system look and feel skips when the target class is already active`() {
        // Covers the early-return branch so startup avoids reapplying the same Swing look and feel.
        val result = configureSystemLookAndFeel(
            systemClassName = { "same" },
            activeClassName = { "same" },
            applyLookAndFeel = { error("should not apply") }
        )
        assertEquals(LookAndFeelApplyResult.SKIPPED_ALREADY_ACTIVE, result)
    }

    @Test
    fun `configure system look and feel applies and maps setter failures`() {
        // Verifies both the successful apply path and the failure mapping when Swing rejects the requested look and feel.
        var appliedClass: String? = null
        val applied = configureSystemLookAndFeel(
            systemClassName = { "target" },
            activeClassName = { "other" },
            applyLookAndFeel = { className -> appliedClass = className }
        )
        assertEquals(LookAndFeelApplyResult.APPLIED, applied)
        assertEquals("target", appliedClass)

        val failed = configureSystemLookAndFeel(
            systemClassName = { "next" },
            activeClassName = { "other" },
            applyLookAndFeel = { error("boom") }
        )
        assertEquals(LookAndFeelApplyResult.FAILED, failed)
    }


    @Test
    fun `launch desktop app forwards work to the injected application host`() {
        // Keeps the launch entrypoint test lightweight by asserting it still delegates to the supplied application host seam.
        var applicationHostInvoked = false
        launchDesktopApp(
            applicationHost = { _ ->
                applicationHostInvoked = true
            }
        )
        assertTrue(applicationHostInvoked)
    }
}
