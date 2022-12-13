package org.gradle.plugins.fsm.isolationcheck

import de.espirit.mavenplugins.fsmchecker.Category
import de.espirit.mavenplugins.fsmchecker.ComplianceLevel
import org.assertj.core.api.Assertions.assertThat
import org.json.JSONArray
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TextViolationHandlerTest {

    private lateinit var handler: TextViolationHandler


    @BeforeEach
    fun setUp() {
        handler = object : TextViolationHandler() {
            override fun onDone(success: Boolean) {
                // noop
            }
        }
    }


    @Test
    fun `should append IsolationLevel and description when there is at least one entry`() {
        val classes = JSONArray(
            """[{"name": "de.espirit.common.gui.SelfDisposingDialog","detailsAvailable": true, "numberOfUsages": 3 }]"""
        )
        handler.handleViolations(ComplianceLevel.DEFAULT, Category.IMPL_USAGE, "", classes)
        assertThat(handler.getViolationMessage()).contains(Category.IMPL_USAGE.name)
    }


    @Test
    fun `write an entry on a new line and with indentation`() {
        val classes = JSONArray(
            """[{"name": "de.espirit.common.gui.SelfDisposingDialog","detailsAvailable": true, "numberOfUsages": 3 }]"""
        )
        handler.handleViolations(ComplianceLevel.DEFAULT, Category.IMPL_USAGE, "", classes)
        assertThat(handler.getViolationMessage()).contains("  de.espirit.common.gui.SelfDisposingDialog (3 usages)")
    }


    @Test
    fun `should append two entries when there are two entries`() {
        val classes = JSONArray("""
        [
            { "name": "de.espirit.common.gui.SelfDisposingDialog","detailsAvailable": true, "numberOfUsages": 2 },
            { "name": "de.espirit.firstspirit.client.io.WebConnection","detailsAvailable": true, "numberOfUsages": 5 }
        ]
        """.trimIndent()
        )
        handler.handleViolations(ComplianceLevel.DEFAULT, Category.IMPL_USAGE, "", classes)
        assertThat(handler.getViolationMessage()).contains("de.espirit.common.gui.SelfDisposingDialog (2 usages)")
        assertThat(handler.getViolationMessage()).contains("de.espirit.firstspirit.client.io.WebConnection (5 usages)")
    }


    @Test
    fun `several outputWriters may be combined`() {
        val implClasses = JSONArray("""
        [
            { "name": "de.espirit.common.gui.SelfDisposingDialog","detailsAvailable": true, "numberOfUsages": 2 },
            { "name": "de.espirit.firstspirit.client.io.WebConnection","detailsAvailable": true, "numberOfUsages": 5 }
        ]
        """.trimIndent()
        )
        handler.handleViolations(ComplianceLevel.DEFAULT, Category.IMPL_USAGE, "", implClasses)
        val nonApiClasses = JSONArray("""
        [
            { "name": "de.espirit.firstspirit.io.servlet.WebAuthentication","detailsAvailable": true, "numberOfUsages": 6  },
            { "name": "de.espirit.firstspirit.webedit.server.HttpSessionAgent","detailsAvailable": true, "numberOfUsages": 11 }
        ]    
        """.trimIndent()
        )
        handler.handleViolations(
            ComplianceLevel.DEFAULT,
            Category.NON_API_USAGE,
            "",
            nonApiClasses
        )
        assertThat(handler.getViolationMessage()).contains("de.espirit.common.gui.SelfDisposingDialog (2 usages)")
        assertThat(handler.getViolationMessage()).contains("de.espirit.firstspirit.io.servlet.WebAuthentication (6 usages)")
    }


    @Test
    fun `should write zero violations`() {
        handler.handleViolations(ComplianceLevel.DEFAULT, Category.IMPL_USAGE, "", JSONArray())
        assertThat(handler.getViolationMessage()).contains("0 violations")
    }
    
}