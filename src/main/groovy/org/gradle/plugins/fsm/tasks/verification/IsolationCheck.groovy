package org.gradle.plugins.fsm.tasks.verification

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.TaskAction
import org.gradle.plugins.fsm.FSMPluginExtension

class IsolationCheck extends DefaultTask {

    public FSMPluginExtension pluginExtension

    IsolationCheck() {
        pluginExtension = project.getExtensions().getByType(FSMPluginExtension)
        pluginExtension.isolationLevel = IsolationLevel.RUNTIME_USAGE
    }

    @TaskAction
    void check() {
        if (pluginExtension.isolationDetectorUrl == null || pluginExtension.isolationDetectorUrl== "") {
            return
        }

        if (pluginExtension.firstSpiritVersion == null || pluginExtension.firstSpiritVersion == "") {
            throw new GradleException("Isolation check requires FirstSpirit version to check against. Please consult the README.")
        }

        final URI uri = new URI(pluginExtension.isolationDetectorUrl)
        WebServiceConnector webServiceConnector = new WebServiceConnector(uri, pluginExtension.firstSpiritVersion, pluginExtension.isolationLevel)
        webServiceConnector.checkFiles(new ArrayList<File>(getInputs().files.files))

        if (!webServiceConnector.isResultValid()) {
            throw new GradleException("Isolation check failed: " + webServiceConnector.resultMessage())
        }
    }

    String getDetectorUrl() {
        return pluginExtension.isolationDetectorUrl
    }

    void setDetectorUrl(String detectorUrl) {
        pluginExtension.isolationDetectorUrl = detectorUrl
    }

    IsolationLevel getIsolationLevel() {
        return pluginExtension.isolationLevel
    }

    void setIsolationLevel(IsolationLevel isolationLevel) {
        pluginExtension.isolationLevel = isolationLevel
    }

    String getFirstSpiritVersion() {
        return pluginExtension.firstSpiritVersion
    }

    void setFirstSpiritVersion(String firstSpiritVersion) {
        pluginExtension.firstSpiritVersion = firstSpiritVersion
    }
}
