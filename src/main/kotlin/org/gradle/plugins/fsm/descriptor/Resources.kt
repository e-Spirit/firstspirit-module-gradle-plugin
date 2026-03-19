package org.gradle.plugins.fsm.descriptor

import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.redundent.kotlin.xml.Node
import org.redundent.kotlin.xml.PrintOptions
import org.redundent.kotlin.xml.xml
import java.io.File
import java.util.jar.JarFile

class Resources(
    private val fsmGradlePluginContext: FSMGradlePluginContext,
    private val webXmlPaths: List<String>
) {

    val node by lazy {
        xml("resources") {
            projectResource()?.let(this::addElement)
            FsmResources(fsmGradlePluginContext.scopedResources, webXmlPaths).fsmResources().forEach(this::addElement)
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
        val jarFile = fsmGradlePluginContext.buildJar.get()
        if (!jarFile.exists()) {
            LOGGER.warn("Jar file '$jarFile' not found!")
            return null
        } else if (isEmptyJarFile(jarFile)) {
            LOGGER.info("Skipping empty Jar file.")
            return null
        }

        return xml("resource") {
            attribute("name", "${fsmGradlePluginContext.projectGroup.get()}:${fsmGradlePluginContext.projectName.get()}")
            attribute("version", fsmGradlePluginContext.projectVersion.get())
            attribute("scope", fsmGradlePluginContext.extension.projectJarScope)
            attribute("mode", "isolated")
            -"lib/${jarFile.name}"
        }
    }

    /**
     * Library dependencies specified in one of the many supported configurations
     */
    private fun dependencies(): List<Node> {
        val dependencies = mutableListOf<Node>()

        fsmGradlePluginContext.serverScopeDependencies.get()
            .map { Resource(fsmGradlePluginContext, it, "server").node }
            .forEach(dependencies::add)

        fsmGradlePluginContext.moduleScopeDependencies.get()
            .map { Resource(fsmGradlePluginContext, it, "module").node }
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