package org.gradle.plugins.fsm.classloader

import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.bundling.Jar
import java.net.URL
import java.net.URLClassLoader

private fun getClasspathFiles(project: Project): Array<URL> {
    // Get project jar
    val projectJarTask = project.tasks.getByName(JavaPlugin.JAR_TASK_NAME) as Jar
    val projectJar = projectJarTask.archiveFile.get().asFile

    // Get runtime and compile classpath jars
    val runtimeClasspathConfiguration =
        project.configurations.getByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME)
    val runtimeClasspathJars = runtimeClasspathConfiguration.files.toList()
    val compileClasspathConfiguration =
        project.configurations.getByName(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME)
    val compileClasspathJars = compileClasspathConfiguration.files.toList()

    // Combine and get URLs
    val jars = compileClasspathJars + runtimeClasspathJars + projectJar
    return jars.map { file -> file.toURI().toURL() }.toList().toTypedArray()
}

/**
 * Class Loader used for instantiating components located in an
 * FSM file. For a given FSM project, it will use the project jar
 * and its runtime and compile classpaths
 */
class FsmComponentClassLoader(project: Project) :
    URLClassLoader(getClasspathFiles(project), FsmComponentClassLoader::class.java.classLoader)