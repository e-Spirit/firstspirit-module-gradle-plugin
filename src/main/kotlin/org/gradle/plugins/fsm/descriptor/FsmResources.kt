package org.gradle.plugins.fsm.descriptor

import org.gradle.api.Project
import org.gradle.plugins.fsm.configurations.FSMConfigurationsPlugin
import org.gradle.plugins.fsm.configurations.FSMConfigurationsPlugin.Companion.FS_MODULE_COMPILE_CONFIGURATION_NAME
import org.gradle.plugins.fsm.configurations.FSMConfigurationsPlugin.Companion.FS_SERVER_COMPILE_CONFIGURATION_NAME
import org.gradle.plugins.fsm.projectDependencies
import org.redundent.kotlin.xml.Node
import org.redundent.kotlin.xml.xml

/**
 * All resources (single files or directories) placed in the src/main/fsm-resources
 * directory of this project or in projects added as dependencies
 */
class FsmResources(private val project: Project, private val webXmlPaths: List<String>) {

    fun fsmResources(): List<Node> {
        val resources = LinkedHashMap<String, Node>()

        for ((project, scope) in scopedProjectDependencies()) {
            for ((relativePath, node) in fsmResources(project, scope)) {
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

                if (resources.containsKey(relativePath)) {
                    if (resources[relativePath]!!.attributes["scope"] == "module"
                        && node.attributes["scope"] == "server") {
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

    private fun scopedProjectDependencies(): List<ScopedProjectDependency> {
        val projects = mutableListOf<ScopedProjectDependency>()
        projects.add(ScopedProjectDependency(project, "module"))

        listOf(FS_MODULE_COMPILE_CONFIGURATION_NAME, FS_SERVER_COMPILE_CONFIGURATION_NAME).forEach { configurationName ->
            val configuration = project.configurations.getByName(configurationName)
            val scope = if (configurationName == FS_MODULE_COMPILE_CONFIGURATION_NAME) { "module" } else { "server" }
            configuration.projectDependencies(project)
                .map { ScopedProjectDependency(it, scope) }
                .forEach { projects.add(it) }
        }

        return projects
    }

    private fun fsmResources(project: Project, scope: String): List<ResourceEntry> {
        val resourcesDir = project.projectDir.resolve(FSMConfigurationsPlugin.FSM_RESOURCES_PATH)
        if (!resourcesDir.isDirectory) {
            return emptyList()
        }

        val fsmResources = mutableListOf<ResourceEntry>()
        val files = resourcesDir.listFiles() ?: return fsmResources

        for (resource in files) {
            val relativePath = resourcesDir.toPath().relativize(resource.toPath())
            val node = xml("resource") {
                attribute("name", "${project.group}:${project.name}-${relativePath}")
                attribute("version", project.version)
                attribute("scope", scope)
                attribute("mode", "isolated")
                text(relativePath.toString())
            }
            fsmResources.add(ResourceEntry(relativePath.toString(), node))
        }

        return fsmResources
    }

    private data class ScopedProjectDependency(val project: Project, val scope: String)

    private data class ResourceEntry(val relativePath: String, val node: Node)

}