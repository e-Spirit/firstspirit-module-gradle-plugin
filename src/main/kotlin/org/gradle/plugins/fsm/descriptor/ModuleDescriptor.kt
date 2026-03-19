package org.gradle.plugins.fsm.descriptor

import org.gradle.api.GradleException
import org.redundent.kotlin.xml.*
import java.io.ByteArrayInputStream

class ModuleDescriptor(private val fsmGradlePluginContext: FSMGradlePluginContext) {

    val moduleClass: ModuleComponent
    val components: Components
    val resources: Resources
    val node: Node
    val dependencies: List<Node>

    init {
        val componentsNode: Node

        ComponentScan(fsmGradlePluginContext.fsmAnnotationsJar, fsmGradlePluginContext.projectJarFiles).use {
            val pluginExtension = fsmGradlePluginContext.extension
            val configurationsPlugin = fsmGradlePluginContext.configurationsPlugin
            components = Components(it, pluginExtension, configurationsPlugin, fsmGradlePluginContext)
            componentsNode = components.node
            resources = Resources(fsmGradlePluginContext, components.webXmlPaths)
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
        val pluginExtension = fsmGradlePluginContext.extension
        with(descriptor) {
            "name" { -(pluginExtension.moduleName ?: fsmGradlePluginContext.projectName.get()) }
            "version" { -fsmGradlePluginContext.projectVersion.get() }
            pluginExtension.minimalFirstSpiritVersion?.let { if (it.isNotBlank()) { "min-fs-version" { -it } } }
            "displayname" { -(pluginExtension.displayName ?: fsmGradlePluginContext.projectName.get()) }
            "description" { -(fsmGradlePluginContext.projectDescription.get()) }
            "vendor" { -(pluginExtension.vendor ?: "") }
            "licenses" { -"META-INF/licenses.csv" }
        }
    }

    companion object {
        private val PRINT_OPTIONS = PrintOptions(singleLineTextElements = true)
    }
}