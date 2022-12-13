package org.gradle.plugins.fsm.isolationcheck

import org.gradle.plugins.fsm.isolationcheck.VerificationResult.Status.INVALID
import org.gradle.plugins.fsm.isolationcheck.VerificationResult.Status.VALID
import org.json.JSONArray
import org.json.JSONObject

class VerificationResult private constructor(val status: Status, val message: String, val moduleErrors: List<String>) {

    enum class Status {
        VALID, INVALID, CONNECTION_FAILED
    }

    constructor(
        status: Status,
        message: String
    ) : this(status, message, emptyList())

    override fun toString(): String {
        return "VerificationResult{$status, '$message'}"
    }

    fun isValid(): Boolean {
        return status == VALID
    }

    companion object {
        fun createInvalidResultFromJson(failedModules: JSONArray): VerificationResult {
            return VerificationResult(INVALID, "Unable to process module", parseFailedModules(failedModules))
        }

        /*
        {
            "failedFile":"saml-loginmodule-1.0-SNAPSHOT.fsm",
            "errorMessage":"null - java.lang.NullPointerException at de.espirit.firstspirit.module.descriptor.AbstractDescriptor$ResourceDescriptor.create(AbstractDescriptor.java:551)"
         }
        */
        private fun parseFailedModules(failedModules: JSONArray): List<String> {
            return failedModules
                .map { it as JSONObject }
                .map { "${it.getString("failedFile")} --> ${it.getString("errorMessage")}" }
        }
    }

}