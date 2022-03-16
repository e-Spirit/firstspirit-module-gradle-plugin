package org.gradle.plugins.fsm.descriptor

import com.espirit.moddev.components.annotations.ProjectAppComponent
import de.espirit.firstspirit.module.Configuration
import de.espirit.firstspirit.module.ProjectApp
import de.espirit.firstspirit.server.module.ModuleInfo.Mode
import io.github.classgraph.ScanResult
import org.gradle.api.Project
import org.redundent.kotlin.xml.Node
import org.redundent.kotlin.xml.xml

class ProjectAppComponents(
    project: Project, private val scanResult: ScanResult, private val classLoader: ClassLoader
) : ComponentsWithResources(project) {

    val nodes by lazy {
        scanResult
            .getClassesImplementing(ProjectApp::class.java.name).names
            .map(classLoader::loadClass)
            .mapNotNull(this::nodeForProjectApp)
    }

    @Suppress("DuplicatedCode") // No refactoring possible because of incompatible annotations
    private fun nodeForProjectApp(projectApp: Class<*>): Node? {
        if (projectApp.name in PROJECT_APP_BLACKLIST) {
            return null
        }

        return projectApp.annotations
            .filterIsInstance<ProjectAppComponent>()
            .map { annotation ->
                xml("project-app") {
                    "name" { -annotation.name }
                    "displayname" { -annotation.displayName }
                    "description" { -annotation.description }
                    "class" { -projectApp.name }

                    if (annotation.configurable != Configuration::class) {
                        "configurable" { -annotation.configurable.java.name }
                    }

                    val resources = nodesForResources(annotation)
                    if (resources.isNotEmpty()) {
                        "resources" {
                            resources.forEach(this::addNode)
                        }
                    }
                }
            }
            .firstOrNull()
    }

    private fun nodesForResources(annotation: ProjectAppComponent): List<Node> {
        val resources = annotation.resources
        val nodes = mutableListOf<Node>()

        resources.forEach { resource ->
            val nameFromAnnotation = expand(resource.name, mutableMapOf("project" to project))
            val dependencyForName = getCompileDependencyForName(nameFromAnnotation)
            val context = getContextForCurrentResource(dependencyForName)
            val versionFromAnnotation = expandVersion(resource.version, context, nameFromAnnotation, annotation.name)
            val pathFromAnnotation = expand(resource.path, context)

            nodes.add(xml("resource") {
                attribute("name", nameFromAnnotation)
                attribute("version", versionFromAnnotation)
                if (resource.minVersion.isNotEmpty()) {
                    attribute("minVersion", resource.minVersion)
                }
                if (resource.maxVersion.isNotEmpty()) {
                    attribute("maxVersion", resource.maxVersion)
                }
                attribute("scope", resource.scope.name.lowercase())
                attribute("mode", Mode.ISOLATED.name.lowercase())
                -pathFromAnnotation
            })
        }

        return nodes
    }

    companion object {
        // ContentTransportProjectApp is part of the fs-access.jar
        val PROJECT_APP_BLACKLIST = listOf("de.espirit.firstspirit.feature.ContentTransportProjectApp")
    }

}