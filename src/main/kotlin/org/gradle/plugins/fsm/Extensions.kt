package org.gradle.plugins.fsm

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.plugins.JavaPlugin
import org.gradle.jvm.tasks.Jar
import org.gradle.util.GradleVersion

/**
 * Returns all projects defined as a dependency for this configuration.
 * Does a recursive search so all nested dependencies are also returned.
 */
fun Configuration.projectDependencies(project: Project): List<Project> {
    return allDependencies.withType(ProjectDependency::class.java)
        .map { it.dependencyProject(project) }
        .flatMap { it.runtimeProjectDependencies() }
        .distinct()
}

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
    val compileClasspath = configurations.getByName(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME)
    val projectDependencies = compileClasspath.allDependencies.withType(ProjectDependency::class.java)
        .map { it.dependencyProject(this) }
        .filter { it.plugins.hasPlugin(JavaPlugin::class.java) }
    return listOf(this) + projectDependencies
}

fun Project.runtimeProjectDependencies(): List<Project> {
    val projects = LinkedHashSet<Project>()

    val runtimeClasspath = configurations.findByName("runtimeClasspath") ?: return listOf(this)
    runtimeClasspath.allDependencies.withType(ProjectDependency::class.java).forEach {
        val dependencyProject = it.dependencyProject(this)
        if (!projects.contains(dependencyProject)) {
            projects.addAll(dependencyProject.runtimeProjectDependencies())
        }
    }

    return listOf(this) + projects
}

fun ProjectDependency.dependencyProject(project: Project): Project {
    return if (GradleVersion.current() < GradleVersion.version("8.11")) {
        throw GradleException("Support for Gradle versions older than 8.11 has been removed." +
                " Please update your Gradle wrapper.")
    } else {
        project.project(this.path)
    }
}