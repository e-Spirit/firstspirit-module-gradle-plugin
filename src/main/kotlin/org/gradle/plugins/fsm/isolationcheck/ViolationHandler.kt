package org.gradle.plugins.fsm.isolationcheck

import de.espirit.mavenplugins.fsmchecker.Category
import de.espirit.mavenplugins.fsmchecker.ComplianceLevel
import org.json.JSONArray

interface ViolationHandler {

    /**
     * @param complianceLevel The configured compliance level.
     * @param category The category.
     * @param categoryDescription Description for category.
     * @param violationsByClass Violations (class names and usage count) for the category.
     */
    fun handleViolations(
        complianceLevel: ComplianceLevel,
        category: Category,
        categoryDescription: String,
        violationsByClass: JSONArray
    )

    /**
     * Called after the step `category analyze` is finished
     */
    fun onDone(success: Boolean)

}