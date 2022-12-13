package org.gradle.plugins.fsm.tasks.verification

import de.espirit.mavenplugins.fsmchecker.ComplianceLevel
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.plugins.fsm.FSMPluginExtension
import org.gradle.plugins.fsm.isolationcheck.ComplianceCheck
import org.gradle.plugins.fsm.isolationcheck.WebServiceConnector
import java.net.URI

/**
 * Checks the degree of compliance in terms of isolation a module has towards a given version of FirstSpirit. Depends
 * on the Java-Implementation within the corresponding Maven-Plugin 'fsm-dependency-checker-maven-plugin'. The
 * IsolationCheck-Task is very similar to the Mojo-Implementation 'FsmVerifier' in the above project.
 */
open class IsolationCheck: DefaultTask() {

    private val pluginExtension = project.extensions.getByType(FSMPluginExtension::class.java)

    @TaskAction
    fun check() {
        val pathList = inputs.files.files.map { it.toPath() }
        val uri = URI.create(pluginExtension.isolationDetectorUrl ?: return)

        if (pathList.isEmpty() || uri.toString().isEmpty()) {
            return
        }

        if (getFirstSpiritVersion().isNullOrEmpty()) {
            throw GradleException("Isolation check requires FirstSpirit version to check against. Please consult the README.")
        }

        logger.lifecycle("Running isolation check ...")
        logger.lifecycle("\tComplianceLevel: '${getComplianceLevel()}'")
        logger.lifecycle("\tmaximum bytecode version: '${getMaxBytecodeVersion()}'")
        logger.lifecycle("\tagainst detector: '${pluginExtension.isolationDetectorUrl}'")
        if (getIsolationDetectorUsername() != null) {
            logger.lifecycle("\tauthenticating as: '${getIsolationDetectorUsername()}'")
        }
        logger.lifecycle("\tusing FirstSpirit version: '${getFirstSpiritVersion()}'")
        logger.lifecycle("\tfsms: '$pathList'")
        val connector = WebServiceConnector(uri, getFirstSpiritVersion(), getMaxBytecodeVersion(),
            getIsolationDetectorUsername(), getIsolationDetectorPassword())

        val complianceCheck = ComplianceCheck(getComplianceLevel(), project.buildDir.toPath(), connector)
        pluginExtension.isolationDetectorWhitelist.forEach { complianceCheck.addWhitelistedResource(it) }
        pluginExtension.contentCreatorComponents.forEach { complianceCheck.addContentCreatorComponent(it) }

        val checkResult = complianceCheck.use { it.check(pathList) }

        if (!checkResult.isValid()) {
            logger.error("Isolation check failed!\nViolation details: " + checkResult.message)
            val moduleErrors = checkResult.moduleErrors
            if (moduleErrors.isNotEmpty()) {
                logger.error("\nmodule details:")
                for (moduleError in moduleErrors) {
                    logger.error("\t" + moduleError)
                }
            }
            throw GradleException("Isolation check failed!\nViolation details: " + checkResult.message)
        } else {
            logger.lifecycle(checkResult.message)
        }
    }

    @Input
    @Optional
    fun getDetectorUrl(): String? {
        return pluginExtension.isolationDetectorUrl
    }

    fun setDetectorUrl(detectorUrl: String) {
        pluginExtension.isolationDetectorUrl = detectorUrl
    }

    @Input
    @Optional
    fun getComplianceLevel(): ComplianceLevel {
        return pluginExtension.complianceLevel
    }

    fun setComplianceLevel(complianceLevel: ComplianceLevel) {
        pluginExtension.complianceLevel = complianceLevel
    }

    @Input
    fun getMaxBytecodeVersion(): Int {
        return pluginExtension.maxBytecodeVersion
    }

    fun setMaxBytecodeVersion(maxBytecodeVersion: Int) {
        pluginExtension.maxBytecodeVersion = maxBytecodeVersion
    }

    @Input
    @Optional
    fun getWhitelistedResources(): Collection<String> {
        return pluginExtension.isolationDetectorWhitelist
    }

    fun setWhitelistedResources(whitelistedResources: Collection<String>) {
        pluginExtension.isolationDetectorWhitelist = whitelistedResources
    }

    @Input
    @Optional
    fun getContentCreatorComponents(): Collection<String> {
        return pluginExtension.contentCreatorComponents
    }

    fun setContentCreatorComponents(contentCreatorComponents: Collection<String>) {
        pluginExtension.contentCreatorComponents = contentCreatorComponents
    }

    @Input
    @Optional
    fun getFirstSpiritVersion(): String? {
        return pluginExtension.firstSpiritVersion
    }

    fun setFirstSpiritVersion(firstSpiritVersion: String) {
        pluginExtension.firstSpiritVersion = firstSpiritVersion
    }

    @Input
    @Optional
    fun getIsolationDetectorUsername(): String? {
        return pluginExtension.isolationDetectorUsername
    }

    fun setIsolationDetectorUsername(isolationDetectorUsername: String) {
        pluginExtension.isolationDetectorUsername = isolationDetectorUsername
    }

    @Input
    @Optional
    fun getIsolationDetectorPassword(): String? {
        return pluginExtension.isolationDetectorPassword
    }

    fun setIsolationDetectorPassword(isolationDetectorPassword: String) {
        pluginExtension.isolationDetectorPassword = isolationDetectorPassword
    }

}