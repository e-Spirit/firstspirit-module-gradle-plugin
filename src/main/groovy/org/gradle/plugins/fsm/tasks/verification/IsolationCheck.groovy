package org.gradle.plugins.fsm.tasks.verification

import de.espirit.common.tools.Strings
import de.espirit.mavenplugins.fsmchecker.ComplianceLevel
import de.espirit.mavenplugins.fsmchecker.WebServiceConnector
import de.espirit.mavenplugins.fsmchecker.check.ComplianceCheckImpl
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.Input
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
    }

    @TaskAction
    void check() {

        def pathList = getInputs().files.files.collect { it.toPath() }

        if (Strings.isEmpty(pluginExtension.isolationDetectorUrl) || pathList.isEmpty()) {
            return
        }

        if (Strings.isEmpty(pluginExtension.firstSpiritVersion)) {
            throw new GradleException("Isolation check requires FirstSpirit version to check against. Please consult the README.")
        }

        final URI uri = new URI(pluginExtension.isolationDetectorUrl)

        getLogger().lifecycle("Running isolation check ...")
        getLogger().lifecycle("\tComplianceLevel: '" + getComplianceLevel() + "'")
        getLogger().lifecycle("\tagainst detector: '" + pluginExtension.isolationDetectorUrl+ "'")
        getLogger().lifecycle("\tusing FirstSpirit version: '" + pluginExtension.firstSpiritVersion + "'")
        getLogger().lifecycle("\tfsms: '" + pathList + "'")
        def connector = new WebServiceConnector(uri, pluginExtension.firstSpiritVersion)

        def complianceCheck = new ComplianceCheckImpl(getComplianceLevel(), project.getBuildDir().toPath(), connector)
        if (pluginExtension.isolationDetectorWhitelist != null) {
            pluginExtension.isolationDetectorWhitelist.each { complianceCheck.addWhitelistedResource(it) }
        }
        if (pluginExtension.contentCreatorComponents != null) {
            pluginExtension.contentCreatorComponents.each { complianceCheck.addContentCreatorComponent(it) }
        }

        def checkResult = complianceCheck.check(pathList)

        if (!checkResult.isValid()) {
            getLogger().error("Isolation check failed!" + System.lineSeparator() + "Violation details: " + checkResult.getMessage())
            final List<String> moduleErrors = checkResult.getModuleErrors()
            if (! moduleErrors.isEmpty()) {
                getLogger().error(System.lineSeparator() + "module details:")
                for (final String moduleError : moduleErrors) {
                    getLogger().error("\t" + moduleError)
                }
            }
            throw new GradleException("Isolation check failed!" + System.lineSeparator() + "Violation details: " + checkResult.getMessage())
        } else {
            getLogger().lifecycle(checkResult.getMessage())
        }
    }

    @Input
    String getDetectorUrl() {
        return pluginExtension.isolationDetectorUrl
    }

    void setDetectorUrl(String detectorUrl) {
        pluginExtension.isolationDetectorUrl = detectorUrl
    }

    @Input
    ComplianceLevel getComplianceLevel() {
        return pluginExtension.complianceLevel
    }

    void setComplianceLevel(ComplianceLevel complianceLevel) {
        pluginExtension.complianceLevel = complianceLevel
    }

    Collection<String> whitelistedResources() {
        return pluginExtension.isolationDetectorWhitelist
    }

    void setWhitelistedResources(Collection<String> whitelistedResources) {
        pluginExtension.isolationDetectorWhitelist = whitelistedResources
    }

    Collection<String> contentCreatorComponents() {
        return pluginExtension.contentCreatorComponents
    }

    void setContentCreatorComponents(Collection<String> contentCreatorComponents) {
        pluginExtension.contentCreatorComponents = contentCreatorComponents
    }

    @Input
    String getFirstSpiritVersion() {
        return pluginExtension.firstSpiritVersion
    }

    void setFirstSpiritVersion(String firstSpiritVersion) {
        pluginExtension.firstSpiritVersion = firstSpiritVersion
    }

}
