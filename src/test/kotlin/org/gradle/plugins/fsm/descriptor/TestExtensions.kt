package org.gradle.plugins.fsm.descriptor

import org.gradle.api.Project
import org.gradle.api.plugins.ExtraPropertiesExtension
import org.gradle.api.plugins.JavaPlugin
import org.gradle.jvm.tasks.Jar
import org.redundent.kotlin.xml.Node
import org.redundent.kotlin.xml.TextElement
import java.io.File
import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Copies the jar with all test classes generated by Gradle to the jar output file.
 * This simulates a project containing all those components. Some classes would break
 * all others tests and have to be added with [Project#addClassToTestJar]
 */
fun Project.copyTestJar() {
    val testJar = File(System.getProperty("testJar"))
    val jar = tasks.getByName(JavaPlugin.JAR_TASK_NAME) as Jar
    testJar.copyTo(jar.archiveFile.get().asFile)
}

fun Project.addClassToTestJar(pathToClassFile: String) {
    val jar = tasks.getByName(JavaPlugin.JAR_TASK_NAME) as Jar
    val testJar = jar.archiveFile.get().asFile
    val classesDir = Paths.get(System.getProperty("classesDir"))
    val classToAdd = classesDir.resolve("groovy/test/$pathToClassFile")
    FileSystems.newFileSystem(URI.create("jar:${testJar.toURI()}"), emptyMap<String, Any>()).use {
        val newClass = it.getPath(pathToClassFile)
        Files.copy(classToAdd, newClass)
    }
}

fun Project.setArtifactoryCredentialsFromLocalProperties() {
    val ext = project.extensions.getByType(ExtraPropertiesExtension::class.java)

    ext.set("artifactory_username", System.getProperty("artifactory_username"))
    ext.set("artifactory_password", System.getProperty("artifactory_password"))
}

fun Project.defineArtifactoryForProject() {
    this.repositories.maven { repo ->
        repo.setUrl("https://artifactory.e-spirit.de/artifactory/repo")
        repo.credentials { credentials ->
            credentials.username = property("artifactory_username") as String
            credentials.password = property("artifactory_password") as String
        }
    }
}

fun Node.textContent(): String {
    return (children[0] as TextElement).text
}

fun Node.childText(name: String): String {
    return (first(name).children[0] as TextElement).text
}