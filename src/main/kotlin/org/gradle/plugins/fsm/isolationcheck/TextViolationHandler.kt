package org.gradle.plugins.fsm.isolationcheck

import de.espirit.mavenplugins.fsmchecker.Category
import de.espirit.mavenplugins.fsmchecker.ComplianceLevel
import org.json.JSONArray
import org.json.JSONObject

abstract class TextViolationHandler: ViolationHandler {

    private val violationMessage: StringBuilder = StringBuilder()

    init {
        violationMessage.append(System.lineSeparator())
    }

    override fun handleViolations(
        complianceLevel: ComplianceLevel,
        category: Category,
        categoryDescription: String,
        violationsByClass: JSONArray
    ) {
        violationMessage.append("$category ($categoryDescription):").append(System.lineSeparator())
        val ignoredForComplianceLevel = !complianceLevel.getAllCategories().contains(category)
        if (violationsByClass.isEmpty) {
            violationMessage.append(INDENTATION).append("0 violations!").append(System.lineSeparator())
        } else {
            if (ignoredForComplianceLevel) {
                violationMessage.append(INDENTATION).append(violationsByClass.length())
                    .append(" violations ignored for ComplianceLevel '").append(complianceLevel).append("'")
                    .append(System.lineSeparator())
            } else {
                violationMessage.append(violationsByClass.length()).append(" violations need to be resolved")
                    .append(System.lineSeparator())
                violationsByClass
                    .map { it as JSONObject }
                    .forEach { violationMessage.append(INDENTATION)
                        .append("${it.getString("name")} (${it.getInt("numberOfUsages")} usages)")
                        .append(System.lineSeparator())
                    }
            }
        }
    }


    fun getViolationMessage(): String {
        return violationMessage.toString()
    }

    companion object {
        private const val INDENTATION = "  "
    }

}