package org.gradle.plugins.fsm.isolationcheck

import de.espirit.mavenplugins.fsmchecker.Category
import de.espirit.mavenplugins.fsmchecker.ComplianceLevel
import org.apache.hc.client5.http.HttpResponseException
import org.gradle.plugins.fsm.isolationcheck.VerificationResult.Status.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.Closeable
import java.net.UnknownHostException
import java.nio.file.Path

class ComplianceCheck(private val complianceLevel: ComplianceLevel, targetPath: Path,
                      private val webserviceConnector: WebServiceConnector): Closeable {

    private var resultMessage = ""

    private val violationHandlers = mutableListOf<ViolationHandler>()

    private val whitelistedResources = mutableListOf<String>()

    private val contentCreatorComponents = mutableListOf<String>()

    init {
        violationHandlers.add(object : TextViolationHandler() {
            override fun onDone(success: Boolean) {
                resultMessage = if (success) {
                    "Isolation check passed! ComplianceLevel: '$complianceLevel'"
                } else {
                    getViolationMessage()
                }
            }
        })
        violationHandlers.add(JUnitXMLFormatHandler(targetPath))
    }

    fun addWhitelistedResource(resourceInfo: String) {
        whitelistedResources.add(resourceInfo)
    }

    fun addContentCreatorComponent(componentName: String) {
        contentCreatorComponents.add(componentName)
    }

    fun check(files: List<Path>): VerificationResult {
        if (files.isEmpty()) {
            return VerificationResult(VALID, NO_FILES_SKIP_CHECK_MESSAGE)
        }

        val uploadResult: VerificationResult = uploadFiles(files)

        if (uploadResult.status !== VALID) {
            return uploadResult
        }

        for (resource in whitelistedResources) {
            try {
                webserviceConnector.addWhitelistedResource(resource)
            } catch (e: HttpResponseException) {
                return VerificationResult(
                    CONNECTION_FAILED,
                    "Adding whitelist resource failed with status '${e.statusCode}'"
                )
            }
        }

        for (component in contentCreatorComponents) {
            try {
                webserviceConnector.addContentCreatorComponent(component)
            } catch (e: HttpResponseException) {
                return VerificationResult(
                    CONNECTION_FAILED,
                    "Adding ContentCreator component failed with status '${e.statusCode}'"
                )
            }
        }

        val analyzeResult = analyzeUploadedFiles()
        if (analyzeResult.status === VALID) {
            val categories =
                try {
                    webserviceConnector.requestCategories()
                } catch (e: HttpResponseException) {
                    return VerificationResult(
                        CONNECTION_FAILED,
                        "Retrieving categories failed with status '${e.statusCode}'"
                    )
                }
            val categoriesResult = CategoriesResult(categories)
            return analyzeCategories(categoriesResult)
        } else {
            return analyzeResult
        }
    }

    private fun uploadFiles(files: List<Path>): VerificationResult {
        return try {
            webserviceConnector.uploadRequest(files)
            VerificationResult(VALID, "")
        } catch (e: HttpResponseException) {
            VerificationResult(CONNECTION_FAILED, "Upload failed with status '${e.statusCode}'")
        } catch (e: UnknownHostException) {
            VerificationResult(CONNECTION_FAILED, "Upload failed because of unknown host: '${e.message}'")
        }
    }

    private fun analyzeUploadedFiles(): VerificationResult {
        val analyzeResult = try {
            webserviceConnector.analyzeRequest()
        } catch (e: HttpResponseException) {
            return VerificationResult(
                CONNECTION_FAILED,
                "Analyze failed with status '${e.statusCode}'"
            )
        }

        val jsonResult = JSONObject(analyzeResult)
        val failedModules = jsonResult.getJSONArray("failedModules")
        return if (failedModules.toList().isEmpty()) {
            val checkedFsmFiles = jsonResult.getJSONArray("checkedFsmFiles")
            for (checkedFsmFile in checkedFsmFiles) {
                val jarsWithInvalidBytecode =
                    (checkedFsmFile as JSONObject).getJSONArray("jarsWithInvalidBytecode")
                if (!jarsWithInvalidBytecode.isEmpty) {
                    return VerificationResult(
                        INVALID, "Jars with invalid bytecode level detected: "
                                + jarsWithInvalidBytecode.join(", ")
                    )
                }
                val detectedFirstSpiritJars =
                    checkedFsmFile.getJSONArray("detectedFirstSpiritArtifacts")
                if (!detectedFirstSpiritJars.isEmpty) {
                    return VerificationResult(
                        INVALID, "FirstSpirit artifacts detected: "
                                + detectedFirstSpiritJars.join(", ")
                    )
                }
            }
            VerificationResult(VALID, "")
        } else {
            VerificationResult.createInvalidResultFromJson(failedModules)
        }
    }

    private fun analyzeCategories(categoriesResult: CategoriesResult): VerificationResult {
        for (category in Category.values()) {
            handleViolations(category, categoriesResult)
        }
        val success: Boolean = !isFailure(categoriesResult)
        for (violationHandler in violationHandlers) {
            violationHandler.onDone(success)
        }
        return VerificationResult(if (success) VALID else INVALID, resultMessage)
    }

    private fun handleViolations(category: Category, categoriesResult: CategoriesResult) {
        val classes = if (categoriesResult.violationCountFor(category) > 0) {
            JSONArray(webserviceConnector.requestCategory(category))
        } else {
            JSONArray()
        }
        violationHandlers.forEach {
            it.handleViolations(
                complianceLevel,
                category,
                categoriesResult.descriptionFor(category),
                classes
            )
        }
    }

    private fun isFailure(categoriesResult: CategoriesResult): Boolean {
        return complianceLevel.getAllCategories().any { category ->
            categoriesResult.violationCountFor(category) > 0
        }
    }

    override fun close() {
        webserviceConnector.close()
    }

    companion object {
        private const val NO_FILES_SKIP_CHECK_MESSAGE = "No FirstSpirit module files (.fsm) were configured. -> skipping check."
    }

}