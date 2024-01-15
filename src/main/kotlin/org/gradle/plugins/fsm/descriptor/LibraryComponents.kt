package org.gradle.plugins.fsm.descriptor

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.plugins.JavaPlugin
import org.gradle.plugins.fsm.FSMPluginExtension
import org.redundent.kotlin.xml.Node
import org.redundent.kotlin.xml.xml

class LibraryComponents(project: Project): ComponentsWithResources(project) {

    val nodes by lazy {
        nodesForLibraries(project)
    }

    private fun nodesForLibraries(project: Project): Sequence<Node> {
        return project.extensions.getByType(FSMPluginExtension::class.java).libraries.asSequence()
            .map { library ->
                xml("library") {
                    "name" { -library.name }
                    if (library.displayName.isNotEmpty()) {
                        "displayname" { -library.displayName }
                    }
                    if (library.description.isNotEmpty()) {
                        "description" { -library.description }
                    }
                    if (library.hidden) {
                        "hidden" { -"true" }
                    }
                    if (library.configurable.isNotEmpty()) {
                        "configurable" { -library.configurable }
                    }
                    "resources" {
                        library.configuration?.let {
                            val nodes = nodesForLibraryResources(project, it)

                            if (nodes.isEmpty()) {
                                LOGGER.warn("Library '${library.name}' does not specify any resources.")
                            }

                            nodes.forEach(::addElement)
                        }
                    }
                }
            }
    }

    private fun nodesForLibraryResources(project: Project, configuration: Configuration): List<Node> {
        return getResolvedDependencies(project, configuration).map { artifact ->
            Resource(project, artifact, "server").node
        }
    }

    companion object {
        val LOGGER: Logger = Logging.getLogger(LibraryComponents::class.java)

        fun getResolvedDependencies(project: Project, configuration: Configuration): Set<ResolvedArtifact> {
            // We might find the same dependencies in different subprojects / configurations, but with different versions
            // Because only one version ends up in the FSM archive, we need to make sure we always use the correct version
            val allRuntimeDependencies = project.configurations.getByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME)
                .resolvedConfiguration.resolvedArtifacts

            return configuration.resolvedConfiguration.resolvedArtifacts
                .map { allRuntimeDependencies.find { runtime -> runtime.hasSameModuleAs(it) } ?: it }
                .toSet()
        }
    }

}

