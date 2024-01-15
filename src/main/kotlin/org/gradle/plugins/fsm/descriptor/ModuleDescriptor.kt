package org.gradle.plugins.fsm.descriptor

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.plugins.fsm.FSMPluginExtension
import org.redundent.kotlin.xml.*
import java.io.ByteArrayInputStream

class ModuleDescriptor(private val project: Project) {

    private val pluginExtension = project.extensions.getByType(FSMPluginExtension::class.java)

    val moduleClass: ModuleComponent
    val components: Components
    val resources: Resources
    val node: Node
    val dependencies: List<Node>

    init {
        val componentsNode: Node

        ComponentScan(project).use {
            components = Components(project, it)
            componentsNode = components.node
            resources = Resources(project, components.webXmlPaths)
            moduleClass = ModuleComponent(it)
            dependencies = pluginExtension.fsmDependencies.map { xml("depends") { -it } }

            node = xml("module") {
                includeXmlProlog = true
                version = XmlVersion.V10
                moduleInformation(this)
                "dependencies" {
                    dependencies.forEach(this::addElement)
                }
                moduleClass.nodes.forEach(this::addElement)
                addElement(componentsNode)
                addElement(resources.node)
            }
        }
    }

    override fun toString(): String {
        return node.toString(PRINT_OPTIONS)
    }

    fun reformat(xml: String): String {
        if (xml.isBlank()) {
            throw GradleException("Module descriptor is empty.")
        }

        val bytes = ByteArrayInputStream(xml.toByteArray())
        val parsedNode = parse(bytes)
        parsedNode.includeXmlProlog = true
        return parsedNode.toString(PRINT_OPTIONS)
    }

    fun fsmDependencies(): String {
        return dependencies.joinToString("\n") { it.toString(PRINT_OPTIONS) }
    }

    private fun moduleInformation(descriptor: Node) {
        with(descriptor) {
            "name" { -(pluginExtension.moduleName ?: project.name) }
            "version" { -"${project.version}" }
            pluginExtension.minimalFirstSpiritVersion?.let { if (it.isNotBlank()) { "min-fs-version" { -it } } }
            "displayname" { -(pluginExtension.displayName ?: project.name) }
            "description" { -(project.description ?: project.name) }
            "vendor" { -(pluginExtension.vendor ?: "") }
            "licenses" { -"META-INF/licenses.csv" }
        }
    }

    companion object {
        private val PRINT_OPTIONS = PrintOptions(singleLineTextElements = true)
    }
}