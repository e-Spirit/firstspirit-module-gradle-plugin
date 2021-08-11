package org.gradle.plugins.fsm

import com.espirit.moddev.components.annotations.WebAppComponent
import org.gradle.api.Project

/**
 * Checks correspondence of classes annotated with [WebAppComponent] annotation to gradle subprojects
 * declared in [FSMPluginExtension#getWebApps()]. Reports web app annotations that do not have a corresponding
 * declaration or vice-versa.
 */
class DeclaredWebAppChecker(val project: Project, webAppClasses: Collection<Class<*>>) {

    private val classes = webAppClasses.toMutableList()

    var declaredProjectsWithoutAnnotation: Set<String>? = null
    get() {
        if (field == null) {
            scanWebApps()
        }
        return field
    }

    var webAppAnnotationsWithoutDeclaration: Set<WebAppComponent>? = null
        get() {
            if (field == null) {
                scanWebApps()
            }
            return field
        }

    private fun scanWebApps() {
        val declaredWebapps = project.extensions.getByType(FSMPluginExtension::class.java).getWebApps()

        val projects = declaredWebapps.keys.toMutableSet()
        val annotations = mutableSetOf<WebAppComponent>()

        classes.forEach { webAppClass ->
            val annotation = webAppClass.annotations.filterIsInstance<WebAppComponent>().firstOrNull()
            if (annotation != null) {
                val webAppName = annotation.name
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