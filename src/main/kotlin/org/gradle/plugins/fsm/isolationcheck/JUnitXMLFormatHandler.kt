package org.gradle.plugins.fsm.isolationcheck

import de.espirit.mavenplugins.fsmchecker.Category
import de.espirit.mavenplugins.fsmchecker.ComplianceLevel
import org.json.JSONArray
import org.json.JSONObject
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/**
 * Violation handler creating a junit format result.
 */
class JUnitXMLFormatHandler(private val targetPath: Path): ViolationHandler {

    private val document: Document
    private val testSuites: Element

    init {
        val documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        document = documentBuilder.newDocument()
        document.xmlVersion = "1.0"
        testSuites = document.createElement("testsuites")
        document.appendChild(testSuites)
    }


    override fun handleViolations(
        complianceLevel: ComplianceLevel,
        category: Category,
        categoryDescription: String,
        violationsByClass: JSONArray
    ) {
        // create a test suite for the category
        val testSuite = testSuites.ownerDocument.createElement("testsuite")
        testSuite.setAttribute("name", getTestSuiteName(category))
        if (!violationsByClass.isEmpty) {
            val ignoredForComplianceLevel = category !in complianceLevel.getAllCategories()
            // append a test case element for each class
            violationsByClass
                .map { it as JSONObject }
                .forEach { clazzJson ->
                    val testCase = testSuite.ownerDocument.createElement("testcase")
                    testCase.setAttribute("name", clazzJson.getString("name"))
                    if (ignoredForComplianceLevel) {
                        testCase.appendChild(testCase.ownerDocument.createElement("skipped"))
                    }
                    val failure = testCase.ownerDocument.createElement("failure")
                    val numberOfUsages = clazzJson.getInt("numberOfUsages")
                    failure.setAttribute(
                        "message",
                        if (numberOfUsages == 1) "1 usage" else "$numberOfUsages usages"
                    )
                    testCase.appendChild(failure)
                    testSuite.appendChild(testCase)
                }
        } else {
            // append a successful noViolations test case
            val testCase = testSuite.ownerDocument.createElement("testcase")
            testCase.setAttribute("name", "noViolations")
            testSuite.appendChild(testCase)
        }
        // set test suite counters
        testSuite.setAttribute("tests", testSuite.getElementsByTagName("testcase").length.toString())
        testSuite.setAttribute("errors", "0")
        testSuite.setAttribute("skipped", testSuite.getElementsByTagName("skipped").length.toString())
        testSuite.setAttribute("failures", testSuite.getElementsByTagName("failure").length.toString())
        testSuites.appendChild(testSuite)

        // update overall counters of test suites
        var errors = 0
        var failures = 0
        val testSuiteList = testSuites.getElementsByTagName("testsuite")
        for (i in 0 until testSuiteList.length) {
            val item = testSuiteList.item(i) as Element
            errors += item.getAttribute("errors").toInt()
            failures += item.getAttribute("failures").toInt()
        }
        testSuites.setAttribute("tests", testSuites.getElementsByTagName("testcase").length.toString())
        testSuites.setAttribute("errors", errors.toString())
        testSuites.setAttribute("failures", failures.toString())
    }


    override fun onDone(success: Boolean) {
        val junitReportFile = targetPath.resolve(JUNIT_REPORTS_DIR).resolve("TEST-complianceCheck.xml")
        Files.createDirectories(junitReportFile.parent)
        Files.newOutputStream(junitReportFile).use { out -> write(out) }
    }


    fun write(out: OutputStream) {
        val transformer = TransformerFactory.newInstance().newTransformer()
        transformer.setOutputProperty(OutputKeys.METHOD, "xml")
        transformer.setOutputProperty(OutputKeys.INDENT, "yes")
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4")
        transformer.transform(DOMSource(document), StreamResult(out))
    }

    companion object {
        private const val JUNIT_REPORTS_DIR = "fsmchecker-reports"

        fun getTestSuiteName(category: Category): String {
            for (complianceLevel in ComplianceLevel.values()) {
                if (complianceLevel.categories.contains(category)) {
                    return complianceLevel.name + " | " + category.name
                }
            }
            return category.name
        }
    }

}