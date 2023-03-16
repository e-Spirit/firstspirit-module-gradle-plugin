package org.gradle.plugins.fsm.descriptor

import org.gradle.api.Project
import org.gradle.api.plugins.ExtraPropertiesExtension
import java.io.File
import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Paths
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

/**
 * Copies the jar with all test classes generated by Gradle to the jar output file.
 * This simulates a project containing all those components. Some classes would break
 * all others tests and have to be added with [Project.addClassToTestJar]
 */
fun Project.copyTestJar() {
    val testJar = File(System.getProperty("testJar"))
    testJar.copyTo(buildJar())
}

fun Project.addClassToTestJar(pathToClassFile: String) {
    val classesDir = Paths.get(System.getProperty("classesDir"))
    val classToAdd = classesDir.resolve("kotlin/test/$pathToClassFile")
    FileSystems.newFileSystem(URI.create("jar:${buildJar().toURI()}"), emptyMap<String, Any>()).use {
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

fun Project.writeJarFileWithEntries(vararg entries: String) {
    val jarFile = buildJar()
    Files.createDirectories(jarFile.parentFile.toPath())
    jarFile.createNewFile()
    JarOutputStream(jarFile.outputStream()).use { jar ->
        jar.putNextEntry(JarEntry("META-INF/"))
        jar.putNextEntry(JarEntry("META-INF/MANIFEST.MF"))
        entries.forEach { jar.putNextEntry(JarEntry(it)) }
    }
}

