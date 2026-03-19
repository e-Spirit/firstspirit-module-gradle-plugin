package org.gradle.plugins.fsm.tasks.bundling

import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME
import org.gradle.plugins.fsm.descriptor.WebAppComponents
import org.gradle.plugins.fsm.descriptor.buildJar
import org.gradle.plugins.fsm.runtimeProjectDependencies
import org.redundent.kotlin.xml.Node
import java.io.File

/**
 * Information of a project defined as a web-app
 */
data class WebAppProjectInfo(
    val group: String,
    val name: String,
    val version: String,
    val jarFile: File,
    val runtimeArtifacts: Set<ResolvedDependencyInfo>,
    val fsmResources: List<Node>
) {
    constructor(webAppProject: Project) : this(
        group = webAppProject.group.toString(),
        name = webAppProject.name,
        version = webAppProject.version.toString(),
        jarFile = webAppProject.buildJar(),
        runtimeArtifacts = webAppProject.configurations
            .getByName(RUNTIME_CLASSPATH_CONFIGURATION_NAME)
            .resolvedConfiguration.resolvedArtifacts
            .map { ResolvedDependencyInfo(it) }.toSet(),
        fsmResources = webAppProject.runtimeProjectDependencies()
            .flatMap { WebAppComponents.fsmResources(it) }
    )
}
