package org.gradle.plugins.fsm.descriptor

import de.espirit.firstspirit.server.module.ModuleInfo
import org.gradle.api.Project
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.plugins.fsm.FSMPluginExtension
import org.gradle.plugins.fsm.configurations.FSMConfigurationsPlugin
import org.redundent.kotlin.xml.xml

class Resource(val project: Project, val dependency: ResolvedArtifact, val scope: String, includeMode: Boolean = true) {

    private val pluginExtension = project.extensions.getByType(FSMPluginExtension::class.java)
    private val appendDefaultMinVersion = pluginExtension.appendDefaultMinVersion

    val node by lazy {
        val dependencyId = dependency.moduleVersion.id
        val dependencyAsString = "${dependencyId.group}:${dependencyId.name}"

        // Construct file name in FSM
        val fileClassifier = if (dependency.classifier.isNullOrEmpty()) { "" } else { "-${dependency.classifier}" }
        // DEVEX-530: special handling for project dependencies: take the name of the artifact itself
        val filename = if (dependency.id.componentIdentifier is ProjectComponentIdentifier) {
            dependency.file.name
        } else {
            "${dependencyId.name}-${dependencyId.version}$fileClassifier.${dependency.extension}"
        }

        // Construct resource identifier
        val resourceExtension = if (dependency.extension.isEmpty() || dependency.extension == "jar") {
            "" // Special case for "jar", as the "default" extension we do not put it here
        } else {
            "@${dependency.extension}"
        }
        val resourceClassifier = if (dependency.classifier.isNullOrEmpty()) { "" } else { ":${dependency.classifier}" }
        val resourceIdentifier = "${dependencyAsString}${resourceClassifier}${resourceExtension}"
        val minMaxVersionDefinitions = project.plugins.getPlugin(FSMConfigurationsPlugin::class.java).getDependencyConfigurations()

        val optionalMinMaxVersion = minMaxVersionDefinitions.find { it.dependency.startsWith(dependencyAsString) }

        xml("resource") {
            attribute("name", resourceIdentifier)
            if (scope.isNotEmpty()) {
                attribute("scope", scope)
            }
            if (includeMode) {
                attribute("mode", ModuleInfo.Mode.ISOLATED.name.lowercase())
            }
            attribute("version", dependencyId.version)
            if (appendDefaultMinVersion || optionalMinMaxVersion?.minVersion != null) {
                attribute("minVersion", optionalMinMaxVersion?.minVersion ?: dependencyId.version)
            }
            if (optionalMinMaxVersion?.maxVersion != null) {
                attribute("maxVersion", optionalMinMaxVersion.maxVersion)
            }

            -"lib/${filename}"
        }
    }

}