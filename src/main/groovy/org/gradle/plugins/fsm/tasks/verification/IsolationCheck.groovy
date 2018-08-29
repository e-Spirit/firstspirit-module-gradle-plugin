package org.gradle.plugins.fsm.tasks.verification

import de.espirit.common.tools.Strings
import de.espirit.mavenplugins.fsmchecker.ComplianceCheckUseCase
import de.espirit.mavenplugins.fsmchecker.ComplianceLevel
import de.espirit.mavenplugins.fsmchecker.WebServiceConnector
import de.espirit.mavenplugins.fsmchecker.check.DefaultComplianceCheck
import de.espirit.mavenplugins.fsmchecker.check.HighestComplianceCheck
import de.espirit.mavenplugins.fsmchecker.check.MinimalComplianceCheck
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.TaskAction
import org.gradle.plugins.fsm.FSMPluginExtension


/**
 * Checks the degree of compliance in terms of isolation a module has towards a given version of FirstSpirit. Depends
 * on the Java-Implementation within the corresponding Maven-Plugin 'fsm-dependency-checker-maven-plugin'. The
 * IsolationCheck-Task is very similar to the Mojo-Implementation 'FsmVerifier' in the above project.
 */
class IsolationCheck extends DefaultTask {

    public FSMPluginExtension pluginExtension

    IsolationCheck() {
        pluginExtension = project.getExtensions().getByType(FSMPluginExtension)
        pluginExtension.complianceLevel = ComplianceLevel.DEFAULT
    }

    @TaskAction
    void check() {

        def pathList = getInputs().files.files.collect { it.toPath() }

        if (Strings.isEmpty(pluginExtension.isolationDetectorUrl) || pathList.isEmpty()) {
            return
        }

        if (pluginExtension.firstSpiritVersion == null || pluginExtension.firstSpiritVersion == "") {
            throw new GradleException("Isolation check requires FirstSpirit version to check against. Please consult the README.")
        }

        final URI uri = new URI(pluginExtension.isolationDetectorUrl)

        getLogger().lifecycle("Running isolation check with ComplianceLevel '" + getComplianceLevel() + "' ..." )
        def connector = new WebServiceConnector(uri, pluginExtension.firstSpiritVersion)
        def useCase = complianceCheckFor(getComplianceLevel(), connector)
        def checkResult = useCase.check(pathList)

        if (!checkResult.isValid()) {
            throw new GradleException("Isolation check failed!" + System.lineSeparator() + "Violation details: " + checkResult.getMessage())
        } else {
            getLogger().lifecycle(checkResult.getMessage())
        }
    }

    String getDetectorUrl() {
        return pluginExtension.isolationDetectorUrl
    }

    void setDetectorUrl(String detectorUrl) {
        pluginExtension.isolationDetectorUrl = detectorUrl
    }

    ComplianceLevel getComplianceLevel() {
        return pluginExtension.complianceLevel
    }

    void setComplianceLevel(ComplianceLevel complianceLevel) {
        pluginExtension.complianceLevel = complianceLevel
    }

    String getFirstSpiritVersion() {
        return pluginExtension.firstSpiritVersion
    }

    void setFirstSpiritVersion(String firstSpiritVersion) {
        pluginExtension.firstSpiritVersion = firstSpiritVersion
    }

    private static ComplianceCheckUseCase complianceCheckFor(ComplianceLevel complianceLevel, WebServiceConnector webserviceConnector) {
        switch (complianceLevel) {
            case ComplianceLevel.MINIMAL:
                return new MinimalComplianceCheck(webserviceConnector)
            case ComplianceLevel.DEFAULT:
                return new DefaultComplianceCheck(webserviceConnector)
            case ComplianceLevel.HIGHEST:
                return new HighestComplianceCheck(webserviceConnector)
            default:
                throw new IllegalStateException("Unknown ComplianceLevel '" + complianceLevel + "'!")
        }

    }
}
