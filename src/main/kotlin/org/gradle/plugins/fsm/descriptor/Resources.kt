package org.gradle.plugins.fsm.descriptor

import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.plugins.fsm.FSMPluginExtension
import org.redundent.kotlin.xml.Node
import org.redundent.kotlin.xml.PrintOptions
import org.redundent.kotlin.xml.xml
import java.io.File
import java.util.jar.JarFile

class Resources(private val project: Project, private val webXmlPaths: List<String>) {

    val node by lazy {
        xml("resources") {
            projectResource()?.let(this::addElement)
            FsmResources(project, webXmlPaths).fsmResources().forEach(this::addElement)
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
            attribute("scope", project.extensions.getByType(FSMPluginExtension::class.java).projectJarScope)
            attribute("mode", "isolated")
            -"lib/${jarFile.name}"
        }
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