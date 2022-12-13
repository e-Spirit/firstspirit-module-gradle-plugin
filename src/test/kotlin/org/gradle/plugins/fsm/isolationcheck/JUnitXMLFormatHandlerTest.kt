package org.gradle.plugins.fsm.isolationcheck

import de.espirit.mavenplugins.fsmchecker.Category
import de.espirit.mavenplugins.fsmchecker.ComplianceLevel
import org.assertj.core.api.Assertions.assertThat
import org.json.JSONArray
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMResult
import javax.xml.transform.stream.StreamSource

class JUnitXMLFormatHandlerTest {

    private lateinit var handler: JUnitXMLFormatHandler


    @BeforeEach
    fun setUp(@TempDir tempDir: Path) {
        handler = JUnitXMLFormatHandler(tempDir)
    }


    @Test
    fun empty() {
        val out = ByteArrayOutputStream()
        handler.write(out)
        val document = createDocument(out.toByteArray())
        val testsuites = document.documentElement
        assertThat(testsuites).isNotNull
        assertThat(testsuites.tagName).isEqualTo("testsuites")
        assertThat(testsuites.hasChildNodes()).isFalse
    }


    @Test
    fun `should append two entries when there are two entries`() {
        val classes = JSONArray(
            """
            [
                { "name": "de.espirit.common.gui.SelfDisposingDialog","detailsAvailable": true, "numberOfUsages": 2  },
                { "name": "de.espirit.firstspirit.client.io.WebConnection","detailsAvailable": true, "numberOfUsages": 5  }
            ]
            """.trimIndent()
        )
        handler.handleViolations(ComplianceLevel.DEFAULT, Category.IMPL_USAGE, "", classes)
        val out = ByteArrayOutputStream()
        handler.write(out)
        val document = createDocument(out.toByteArray())
        val testsuites = document.documentElement
        assertThat(testsuites.getAttribute("tests")).isEqualTo("2")
        assertThat(testsuites.getAttribute("failures")).isEqualTo("2")
        assertThat(testsuites.getAttribute("errors")).isEqualTo("0")
        val testsuiteNodeList = testsuites.getElementsByTagName("testsuite")
        assertThat(testsuiteNodeList.length).isEqualTo(1)
        val testsuite = testsuiteNodeList.item(0) as Element
        assertThat(testsuite.getAttribute("name"))
            .isEqualTo(JUnitXMLFormatHandler.getTestSuiteName(Category.IMPL_USAGE))
        assertThat(testsuite.getAttribute("tests")).isEqualTo("2")
        assertThat(testsuite.getAttribute("failures")).isEqualTo("2")
        assertThat(testsuite.getAttribute("errors")).isEqualTo("0")
        val testcaseNodeList = testsuite.getElementsByTagName("testcase")
        assertThat(testcaseNodeList.length).isEqualTo(2)
        val testcase1 = testcaseNodeList.item(0) as Element
        assertThat(testcase1.getAttribute("name")).isEqualTo("de.espirit.common.gui.SelfDisposingDialog")
        assertThat(testcase1.getElementsByTagName("failure").length).isEqualTo(1)
        assertThat((testcase1.getElementsByTagName("failure").item(0) as Element).getAttribute("message"))
            .isEqualTo("2 usages")
        val testcase2 = testcaseNodeList.item(1) as Element
        assertThat(testcase2.getAttribute("name"))
            .isEqualTo("de.espirit.firstspirit.client.io.WebConnection")
        assertThat(testcase2.getElementsByTagName("failure").length).isEqualTo(1)
        assertThat((testcase2.getElementsByTagName("failure").item(0) as Element).getAttribute("message"))
            .isEqualTo("5 usages")
    }


    @Test
    fun `several categories combined`() {
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
            { "name": "de.espirit.firstspirit.io.servlet.WebAuthentication","detailsAvailable": true, "numberOfUsages": 6 }
        ]
        """.trimIndent()
        )
        handler.handleViolations(
            ComplianceLevel.DEFAULT,
            Category.NON_API_USAGE,
            "",
            nonApiClasses
        )
        val out = ByteArrayOutputStream()
        handler.write(out)
        val document = createDocument(out.toByteArray())
        val testsuites = document.documentElement
        assertThat(testsuites.getAttribute("tests")).isEqualTo("3")
        assertThat(testsuites.getAttribute("failures")).isEqualTo("3")
        assertThat(testsuites.getAttribute("errors")).isEqualTo("0")
        val testsuiteNodeList = testsuites.getElementsByTagName("testsuite")
        assertThat(testsuiteNodeList.length).isEqualTo(2)
        val implUsageTestsuite = testsuiteNodeList.item(0) as Element
        assertThat(implUsageTestsuite.getAttribute("name"))
            .isEqualTo(JUnitXMLFormatHandler.getTestSuiteName(Category.IMPL_USAGE))
        assertThat(implUsageTestsuite.getAttribute("tests")).isEqualTo("2")
        assertThat(implUsageTestsuite.getAttribute("failures")).isEqualTo("2")
        assertThat(implUsageTestsuite.getAttribute("errors")).isEqualTo("0")
        val testcase1 = implUsageTestsuite.getElementsByTagName("testcase")
        assertThat(testcase1.length).isEqualTo(2)
        assertThat((testcase1.item(0) as Element).getAttribute("name"))
            .isEqualTo("de.espirit.common.gui.SelfDisposingDialog")
        val nonApiUsageTestsuite = testsuiteNodeList.item(1) as Element
        assertThat(nonApiUsageTestsuite.getAttribute("name"))
            .isEqualTo(JUnitXMLFormatHandler.getTestSuiteName(Category.NON_API_USAGE))
        assertThat(nonApiUsageTestsuite.getAttribute("tests")).isEqualTo("1")
        assertThat(nonApiUsageTestsuite.getAttribute("failures")).isEqualTo("1")
        assertThat(nonApiUsageTestsuite.getAttribute("errors")).isEqualTo("0")
        val testcase2 = nonApiUsageTestsuite.getElementsByTagName("testcase")
        assertThat(testcase2.length).isEqualTo(1)
        assertThat((testcase2.item(0) as Element).getAttribute("name"))
            .isEqualTo("de.espirit.firstspirit.io.servlet.WebAuthentication")
    }


    private fun createDocument(bytes: ByteArray): Document {
        val input = ByteArrayInputStream(bytes)
        val transformer = TransformerFactory.newInstance().newTransformer()
        transformer.setOutputProperty(OutputKeys.METHOD, "xml")
        val domResult = DOMResult()
        transformer.transform(StreamSource(input), domResult)
        return domResult.node as Document
    }

}