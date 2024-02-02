package org.gradle.plugins.fsm

import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.plugins.JavaPlugin
import org.gradle.jvm.tasks.Jar

/**
 * Sets a parameter for a [Jar] task's manifest, if it wasn't already set before.
 * If the parameter was already set, doesn't do anything.
 *
 * @param name    The name of the attribute to configure
 * @param value   The value of the attribute
 */
fun Jar.addManifestAttribute(name: String, value: Any) {
    manifest.attributes.putIfAbsent(name, value)
}

/**
 * Returns a list with the current project and all its projects registered as a compile dependency
 */
fun Project.compileDependencies(): List<Project> {
    val compileClasspath = project.configurations.getByName(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME)
    val projectDependencies = compileClasspath.allDependencies.withType(ProjectDependency::class.java)
        .map { it.dependencyProject }
        .filter { it.plugins.hasPlugin(JavaPlugin::class.java) }
    return listOf(project) + projectDependencies
}