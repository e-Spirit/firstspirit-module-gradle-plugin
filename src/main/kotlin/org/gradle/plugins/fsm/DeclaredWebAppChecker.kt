package org.gradle.plugins.fsm

import com.espirit.moddev.components.annotations.WebAppComponent
import io.github.classgraph.AnnotationInfo
import io.github.classgraph.ClassInfo
import org.gradle.api.Project
import org.gradle.plugins.fsm.descriptor.getString
import org.gradle.plugins.fsm.descriptor.isClass

/**
 * Checks correspondence of classes annotated with [WebAppComponent] annotation to gradle subprojects
 * declared in [FSMPluginExtension.getWebApps]. Reports web app annotations that do not have a corresponding
 * declaration or vice-versa.
 */
class DeclaredWebAppChecker(val project: Project, webAppClasses: Collection<ClassInfo>) {

    private val classes = webAppClasses.toMutableList()

    var declaredProjectsWithoutAnnotation: Set<String>? = null
    get() {
        if (field == null) {
            scanWebApps()
        }
        return field
    }

    var webAppAnnotationsWithoutDeclaration: Set<AnnotationInfo>? = null
        get() {
            if (field == null) {
                scanWebApps()
            }
            return field
        }

    private fun scanWebApps() {
        val declaredWebapps = project.extensions.getByType(FSMPluginExtension::class.java).getWebApps()

        val projects = declaredWebapps.keys.toMutableSet()
        val annotations = mutableSetOf<AnnotationInfo>()

        classes.forEach { webAppClass ->
            val annotation = webAppClass.annotationInfo
                    .filter { it.isClass(WebAppComponent::class) }
                    .firstOrNull()
            if (annotation != null) {
                val webAppName = annotation.getString("name")
                if (declaredWebapps.containsKey(webAppName)) {
                    projects.remove(webAppName)
                } else {
                    annotations.add(annotation)
                }
            }
        }

        declaredProjectsWithoutAnnotation = projects
        webAppAnnotationsWithoutDeclaration = annotations
    }

}