package org.gradle.plugins.fsm.descriptor

import de.espirit.firstspirit.server.module.ModuleInfo
import org.gradle.plugins.fsm.tasks.bundling.ResolvedDependencyInfo
import org.redundent.kotlin.xml.xml

class Resource(
    val fsmGradlePluginContext: FSMGradlePluginContext,
    val dependency: ResolvedDependencyInfo,
    val scope: String,
    includeMode: Boolean = true
) {

    private val appendDefaultMinVersion = fsmGradlePluginContext.extension.appendDefaultMinVersion

    val node by lazy {
        val dependencyAsString = "${dependency.groupId}:${dependency.moduleName}"
        val filename = dependency.file.name

        // Construct resource identifier
        val extension = dependency.extension ?: ""
        val resourceExtension = if (extension.isEmpty() || extension == "jar") {
            "" // Special case for "jar", as the "default" extension we do not put it here
        } else {
            "@${dependency.extension}"
        }
        val resourceClassifier = if (dependency.classifier.isNullOrEmpty()) { "" } else { ":${dependency.classifier}" }
        val resourceIdentifier = "${dependencyAsString}${resourceClassifier}${resourceExtension}"
        val minMaxVersionDefinitions = fsmGradlePluginContext.configurationsPlugin.getDependencyConfigurations()

        val optionalMinMaxVersion = minMaxVersionDefinitions.find { it.dependency.startsWith(dependencyAsString) }

        xml("resource") {
            attribute("name", resourceIdentifier)
            if (scope.isNotEmpty()) {
                attribute("scope", scope)
            }
            if (includeMode) {
                attribute("mode", ModuleInfo.Mode.ISOLATED.name.lowercase())
            }
            attribute("version", dependency.moduleVersion)
            if (appendDefaultMinVersion || optionalMinMaxVersion?.minVersion != null) {
                attribute("minVersion", optionalMinMaxVersion?.minVersion ?: dependency.moduleVersion)
            }
            if (optionalMinMaxVersion?.maxVersion != null) {
                attribute("maxVersion", optionalMinMaxVersion.maxVersion)
            }

            -"lib/${filename}"
        }
    }

}