package org.gradle.plugins.fsm.descriptor

import com.espirit.moddev.components.annotations.ProjectAppComponent
import de.espirit.firstspirit.module.Configuration
import de.espirit.firstspirit.module.ProjectApp
import de.espirit.firstspirit.server.module.ModuleInfo.Mode
import io.github.classgraph.AnnotationInfo
import io.github.classgraph.ClassInfo
import io.github.classgraph.ScanResult
import org.gradle.api.Project
import org.redundent.kotlin.xml.Node
import org.redundent.kotlin.xml.xml

class ProjectAppComponents(project: Project, private val scanResult: ScanResult) : ComponentsWithResources(project) {

    val nodes by lazy {
        scanResult.getClassesImplementing(ProjectApp::class.java.name)
            .mapNotNull(this::nodeForProjectApp)
    }

    @Suppress("DuplicatedCode") // No refactoring possible because of incompatible annotations
    private fun nodeForProjectApp(projectApp: ClassInfo): Node? {
        if (projectApp.name in PROJECT_APP_BLACKLIST) {
            return null
        }

        return projectApp.annotationInfo
            .filter { it.isClass(ProjectAppComponent::class) }
            .map { annotation ->
                xml("project-app") {
                    "name" { -annotation.getString("name") }
                    "displayname" { -annotation.getString("displayName") }
                    "description" { -annotation.getString("description") }
                    "class" { -projectApp.name }
                    annotation.getClassNameOrNull("configurable", Configuration::class)?.let { "configurable" { -it } }

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

    private fun nodesForResources(annotation: AnnotationInfo): List<Node> {
        val resources = annotation.getAnnotationValues("resources")
        val nodes = mutableListOf<Node>()

        resources.forEach { resource ->
            val nameFromAnnotation = expand(resource.getString("name"), mutableMapOf("project" to project))
            val dependencyForName = getCompileDependencyForName(nameFromAnnotation)
            val context = getContextForCurrentResource(dependencyForName)
            val versionFromAnnotation = expandVersion(resource.getString("version"), context, nameFromAnnotation, annotation.getString("name"))
            val pathFromAnnotation = expand(resource.getString("path"), context)

            nodes.add(xml("resource") {
                attribute("name", nameFromAnnotation)
                attribute("version", versionFromAnnotation)
                resource.getStringOrNull("minVersion", "")?.let { attribute("minVersion", it) }
                resource.getStringOrNull("maxVersion", "")?.let { attribute("maxVersion", it) }
                attribute("scope", resource.getEnumValue("scope").valueName.lowercase())
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