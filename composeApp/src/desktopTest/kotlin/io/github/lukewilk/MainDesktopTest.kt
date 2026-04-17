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
        val events = mutableListOf<String>()
        runDesktopMain(
            configureLookAndFeel = { events += "configure" },
            launchApp = { events += "launch" }
        )
        assertEquals(listOf("configure", "launch"), events)
    }

    @Test
    fun `configure system look and feel skips when the target class is already active`() {
        val result = configureSystemLookAndFeel(
            systemClassName = { "same" },
            activeClassName = { "same" },
            applyLookAndFeel = { error("should not apply") }
        )
        assertEquals(LookAndFeelApplyResult.SKIPPED_ALREADY_ACTIVE, result)
    }

    @Test
    fun `configure system look and feel applies and maps setter failures`() {
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
    fun `default desktop window spec matches the published shell geometry`() {
        val spec = defaultDesktopWindowSpec()
        assertEquals("NeuroRook", spec.title)
        assertEquals(1440, spec.widthDp)
        assertEquals(960, spec.heightDp)
        assertEquals("neuroRook.png", spec.iconResourcePath)
    }

    @Test
    fun `launch desktop app forwards work to the injected application host`() {
        var applicationHostInvoked = false
        launchDesktopApp(
            applicationHost = { _ ->
                applicationHostInvoked = true
            }
        )
        assertTrue(applicationHostInvoked)
    }
}
