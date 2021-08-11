package org.gradle.plugins.fsm.descriptor

import de.espirit.firstspirit.server.module.ModuleInfo.Mode
import groovy.lang.MissingPropertyException
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.plugins.JavaPlugin
import org.gradle.jvm.tasks.Jar
import org.gradle.plugins.fsm.FSMPluginExtension
import org.gradle.plugins.fsm.configurations.FSMConfigurationsPlugin
import org.gradle.plugins.fsm.configurations.FSMConfigurationsPlugin.Companion.FS_MODULE_COMPILE_CONFIGURATION_NAME
import org.gradle.plugins.fsm.configurations.FSMConfigurationsPlugin.Companion.FS_SERVER_COMPILE_CONFIGURATION_NAME
import org.gradle.plugins.fsm.configurations.FSMConfigurationsPlugin.Companion.FS_SKIPPED_IN_LEGACY_CONFIGURATION_NAME
import org.redundent.kotlin.xml.Node
import org.redundent.kotlin.xml.PrintOptions
import org.redundent.kotlin.xml.xml
import java.util.*

class Resources(private val project: Project, private val webXmlPaths: List<String>,
                private val isolatedModuleXml: Boolean) {

    private val pluginExtension = project.extensions.getByType(FSMPluginExtension::class.java)

    private val globalResourcesMode: Mode = pluginExtension.resourceMode

    val node by lazy {
        xml("resources") {
            addNode(projectResource())
            fsmResources().forEach(this::addNode)
            dependencies().forEach(this::addNode)
        }
    }

    override fun toString(): String {
        return node.toString(PRINT_OPTIONS)
    }

    fun innerResourcesToString(): String {
        return node.filter { true }.joinToString("\n") { it.toString(PRINT_OPTIONS) }
    }

    /**
     * The jar file assembled for the current project
     */
    private fun projectResource(): Node {
        return xml("resource") {
            attribute("name", "${project.group}:${project.name}")
            attribute("version", project.version)
            attribute("scope", "module")
            attribute("mode", globalResourcesMode.name.toLowerCase(Locale.ROOT))
            val jarTask = project.tasks.getByName("jar") as Jar
            -"lib/${jarTask.archiveFileName.get()}"
        }
    }

    /**
     * All resources (single files or directories) placed in the src/main/fsm-resources
     * directory of this project or in projects added as dependencies
     */
    private fun fsmResources(): List<Node> {
        val resources = LinkedHashMap<String, Node>()

        for (projectEntry in scopedProjectDependencies()) {
            for (fsmResource in fsmResources(projectEntry.first, projectEntry.second)) {
                val relativePath = fsmResource.first
                if (webXmlPaths.contains(relativePath)) {
                    continue
                }

                val containsTrimmedPath = webXmlPaths
                    .filter { it.lastIndexOf('/') == 0 } // Simple file with slash prefix
                    .map { it.substring(1) }
                    .any(relativePath::equals)

                if (containsTrimmedPath) {
                    continue
                }

                val node = fsmResource.second

                if (resources.containsKey(relativePath)) {
                    if (resources[relativePath]!!.attributes["scope"] == "module"
                        && fsmResource.second.attributes["scope"] == "server") {
                        // Same resource exists with module scope but server scope takes precedence
                        resources[relativePath] = node
                    }
                } else {
                    resources[relativePath] = node
                }
            }
        }

        return resources.values.toList()
    }

    private fun scopedProjectDependencies(): List<Pair<Project, String>> {
        val projects = mutableListOf<Pair<Project, String>>()
        projects.add(project to "module")

        FSMConfigurationsPlugin.FS_CONFIGURATIONS.forEach {
            val config = project.configurations.getByName(it)
            val projectDependencies = config.allDependencies.withType(ProjectDependency::class.java)
            for (projectDependency in projectDependencies) {
                val scope = if (it == FS_MODULE_COMPILE_CONFIGURATION_NAME) { "module" } else { "server" }
                projects.add(projectDependency.dependencyProject to scope)
            }
        }

        return projects
    }

    private fun fsmResources(project: Project, scope: String): List<Pair<String, Node>> {
        val resourcesDir = project.projectDir.resolve(FSMConfigurationsPlugin.FSM_RESOURCES_PATH)
        if (!resourcesDir.isDirectory) {
            return emptyList()
        }

        val fsmResources = mutableListOf<Pair<String, Node>>()

        for (resource in resourcesDir.listFiles()!!) {
            val relativePath = resourcesDir.toPath().relativize(resource.toPath())
            val node = xml("resource") {
                attribute("name", "${project.group}:${project.name}-${relativePath}")
                attribute("version", project.version)
                attribute("scope", scope)
                attribute("mode", globalResourcesMode.name.toLowerCase(Locale.ROOT))
                text(relativePath.toString())
            }
            fsmResources.add(relativePath.toString() to node)
        }

        return fsmResources
    }

    /**
     * Library dependencies specified in one of the many supported configurations
     */
    private fun dependencies(): List<Node> {
        val dependencies = mutableListOf<Node>()

        val configurations = project.configurations
        val fsModuleCompileConfiguration = configurations.getByName(FS_MODULE_COMPILE_CONFIGURATION_NAME)
        val fsServerCompileConfiguration = configurations.getByName(FS_SERVER_COMPILE_CONFIGURATION_NAME)
        val uncleanedDependenciesModuleScoped = fsModuleCompileConfiguration.resolvedConfiguration.resolvedArtifacts
        val resolvedServerScopeArtifacts = fsServerCompileConfiguration.resolvedConfiguration.resolvedArtifacts

        val compileDependenciesServerScoped = uncleanedDependenciesModuleScoped.filter {
                moduleScoped -> resolvedServerScopeArtifacts.any { it.hasSameModuleAs(moduleScoped) }
        }.toMutableSet()
        val cleanedCompileDependenciesModuleScoped = uncleanedDependenciesModuleScoped.toMutableSet()
        cleanedCompileDependenciesModuleScoped.removeAll(compileDependenciesServerScoped)

        logIgnoredModuleScopeDependencies(uncleanedDependenciesModuleScoped, cleanedCompileDependenciesModuleScoped)

        val fsProvidedCompileConfiguration = configurations.getByName(FSMConfigurationsPlugin.PROVIDED_COMPILE_CONFIGURATION_NAME)
        val providedCompileDependencies = fsProvidedCompileConfiguration.resolvedConfiguration.resolvedArtifacts

        val legacyModuleXml = !isolatedModuleXml
        if (legacyModuleXml) {
            val runtimeConfiguration = project.configurations.getByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME)
            val allCompileDependencies = runtimeConfiguration.resolvedConfiguration.resolvedArtifacts
            val skippedInLegacyDependencies = getAllSkippedInLegacyDependencies(project, allCompileDependencies)
            compileDependenciesServerScoped.removeAll(skippedInLegacyDependencies)
            cleanedCompileDependenciesModuleScoped.removeAll(skippedInLegacyDependencies)
            providedCompileDependencies.removeAll(skippedInLegacyDependencies)
            LOGGER.debug("Dependencies skipped for (legacy) module.xml:")
        }

        compileDependenciesServerScoped
            .filter { !providedCompileDependencies.contains(it) }
            .map { Resource(project, it, "server").node }
            .forEach(dependencies::add)

        cleanedCompileDependenciesModuleScoped
            .filter { !providedCompileDependencies.contains(it) }
            .map { Resource(project, it, "module").node }
            .forEach(dependencies::add)

        return dependencies
    }

    private fun getAllSkippedInLegacyDependencies(project: Project, allCompileDependencies: Set<ResolvedArtifact>): List<ResolvedArtifact> {
        return project.rootProject.allprojects.flatMap {
            try {
                getResolvedDependencies(it, allCompileDependencies)
            } catch (e: MissingPropertyException) {
                Logging.getLogger(Resources::class.java).trace("No skipInLegacy configuration found for project $it (probably not using fsm plugins at all)", e)
                emptyList()
            }
        }
    }

    /**
     * Finds all dependencies of a given configuration and finds the global version of each dependency
     *
     * @param project           The project
     * @param allDependencies   All dependencies of the project, with the correct version
     * @return The dependencies of `configurationName`, with the correct version
     */
    private fun getResolvedDependencies(project: Project, allDependencies: Set<ResolvedArtifact>): List<ResolvedArtifact> {
        val configuration = project.configurations.findByName(FS_SKIPPED_IN_LEGACY_CONFIGURATION_NAME) ?: return emptyList()
        val resolvedArtifacts = configuration.resolvedConfiguration.resolvedArtifacts
        return allDependencies.filter { resource -> resolvedArtifacts.any {it.hasSameModuleAs(resource) }}
    }

    private fun logIgnoredModuleScopeDependencies(uncleanedDependenciesModuleScoped: Set<ResolvedArtifact>, compileDependenciesModuleScoped: Set<ResolvedArtifact>) {
        val ignoredDependenciesModuleScoped = uncleanedDependenciesModuleScoped - compileDependenciesModuleScoped
        LOGGER.debug("The following dependencies are found on both module and server scope. The scope will be resolved to server.")
        ignoredDependenciesModuleScoped.forEach {
            LOGGER.debug(it.toString())
        }
    }

    companion object {
        val LOGGER: Logger = Logging.getLogger(Resources::class.java)
        private val PRINT_OPTIONS = PrintOptions(singleLineTextElements = true)
    }

}