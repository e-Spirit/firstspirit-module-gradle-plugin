package org.gradle.plugins.fsm.descriptor

import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.plugins.fsm.configurations.FSMConfigurationsPlugin
import org.gradle.plugins.fsm.configurations.FSMConfigurationsPlugin.Companion.FS_MODULE_COMPILE_CONFIGURATION_NAME
import org.redundent.kotlin.xml.Node
import org.redundent.kotlin.xml.PrintOptions
import org.redundent.kotlin.xml.xml
import java.io.File
import java.util.jar.JarFile

class Resources(private val project: Project, private val webXmlPaths: List<String>) {

    val node by lazy {
        xml("resources") {
            projectResource()?.let(this::addElement)
            fsmResources().forEach(this::addElement)
            dependencies().forEach(this::addElement)
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
    private fun projectResource(): Node? {
        val jarFile = project.buildJar()
        if (!jarFile.exists()) {
            LOGGER.warn("Jar file '$jarFile' not found!")
            return null
        } else if (isEmptyJarFile(jarFile)) {
            LOGGER.info("Skipping empty Jar file.")
            return null
        }

        return xml("resource") {
            attribute("name", "${project.group}:${project.name}")
            attribute("version", project.version)
            attribute("scope", "module")
            attribute("mode", "isolated")
            -"lib/${jarFile.name}"
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
                attribute("mode", "isolated")
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

        project.serverScopeDependencies()
            .map { Resource(project, it, "server").node }
            .forEach(dependencies::add)

        project.moduleScopeDependencies()
            .map { Resource(project, it, "module").node }
            .forEach(dependencies::add)

        return dependencies
    }

    companion object {
        val LOGGER: Logger = Logging.getLogger(Resources::class.java)
        private val PRINT_OPTIONS = PrintOptions(singleLineTextElements = true)

        fun isEmptyJarFile(file: File): Boolean {
            JarFile(file).use { jar ->
                val entries = jar.entries()
                val ignored = listOf("META-INF/", "META-INF/MANIFEST.MF")
                while (entries.hasMoreElements()) {
                    if (!ignored.contains(entries.nextElement().name)) {
                        return false
                    }
                }
            }

            return true
        }
    }

}