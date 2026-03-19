package org.gradle.plugins.fsm.descriptor

import org.gradle.api.artifacts.Configuration
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Provider
import org.gradle.plugins.fsm.FSMPluginExtension
import org.gradle.plugins.fsm.tasks.bundling.ResolvedDependencyInfo
import org.redundent.kotlin.xml.Node
import org.redundent.kotlin.xml.xml

class LibraryComponents(
    private val fsmGradlePluginContext: FSMGradlePluginContext,
    resolvedArtifacts: Provider<Set<ResolvedDependencyInfo>>
): ComponentsWithResources(resolvedArtifacts) {

    val nodes by lazy {
        nodesForLibraries(fsmGradlePluginContext.extension, fsmGradlePluginContext.libraryResources.get())
    }

    private fun nodesForLibraries(extension: FSMPluginExtension, libraryResources: Map<String, Set<ResolvedDependencyInfo>?>): Sequence<Node> {
        return extension.libraries.asSequence()
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
                        libraryResources[library.name]?.let { artifacts ->
                            val nodes = artifacts.map { Resource(fsmGradlePluginContext, it, "server").node }

                            if (nodes.isEmpty()) {
                                LOGGER.warn("Library '${library.name}' does not specify any resources.")
                            }

                            nodes.forEach(::addElement)
                        }
                    }
                }
            }
    }

    companion object {
        val LOGGER: Logger = Logging.getLogger(LibraryComponents::class.java)

        fun getResolvedDependencies(allRuntimeDependencies: Set<ResolvedDependencyInfo>, configuration: Configuration): Set<ResolvedDependencyInfo> {
            // We might find the same dependencies in different subprojects / configurations, but with different versions
            // Because only one version ends up in the FSM archive, we need to make sure we always use the correct version
            return configuration.resolvedConfiguration.resolvedArtifacts
                .map { allRuntimeDependencies.find { runtime -> runtime.hasSameModuleAs(it) } ?: ResolvedDependencyInfo(it) }
                .toSet()
        }
    }

}

