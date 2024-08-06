package org.gradle.plugins.fsm

import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.util.jar.Attributes
import java.util.jar.JarFile
import java.util.jar.Manifest
import java.util.zip.ZipFile

class FSMManifestTest {

    private lateinit var testDir: File

    @BeforeEach
    fun setUp(@TempDir tempDir: File) {
        testDir = tempDir
    }

    @Test
    fun manifest() {
        // Copy test project files from resources folder to temp dir
        val resourcesUrl = FSMManifestTest::class.java.classLoader.getResource("manifest")
            ?: error("test project files not found")
        val resourcesPath = Paths.get(resourcesUrl.toURI())
        resourcesPath.toFile().copyRecursively(testDir)

        // Execute a gradle build
        val result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments(FSMPlugin.FSM_TASK_NAME)
            .withPluginClasspath()
            .build()
        assertThat(result.task(":" + FSMPlugin.FSM_TASK_NAME)!!.outcome).isEqualTo(TaskOutcome.SUCCESS)

        // Now examine the manifests of the resulting ZIP files
        val fsmFile = testDir.resolve("build/fsm/test-manifest-2.7.5.fsm")
        assertThat(fsmFile).exists()
        ZipFile(fsmFile).use { zipFile ->
            // Get all manifests

            // Get manifest located in FSM's META-INF/ directory...
            val fsmManifestEntry = zipFile.getEntry("META-INF/MANIFEST.MF")
            val fsmManifest = zipFile.getInputStream(fsmManifestEntry).use { Manifest(it) }

            // Get FSMs located in JAR libs
            val projectManifest = getManifestFromJar(zipFile, "lib/test-manifest-2.7.5.jar")
            val subprojectManifest = getManifestFromJar(zipFile, "lib/subproject-3.0.0.jar")

            // Finally, verify manifests for...
            // ... the FSM File
            fsmManifest.mainAttributes.let {
                assertThat(it).containsEntry(Attributes.Name("Created-By"),
                    "FirstSpirit Module Gradle Plugin " + System.getProperty("version"))
                assertThat(it).containsEntry(Attributes.Name("Build-Tool"), BUILD_TOOL)
                assertThat(it).containsEntry(Attributes.Name("Build-Jdk"), "Custom-Jdk") // Overridden in build.gradle.kts
                assertThat(it).containsEntry(Attributes.Name("Custom-Key"), "Custom-Value") // Set in build.gradle.kts
            }

            // ... the main Jar
            projectManifest.mainAttributes.let {
                assertThat(it).containsEntry(Attributes.Name("Created-By"), BUILD_TOOL)
                assertThat(it).containsEntry(Attributes.Name("Build-Jdk"), BUILD_JDK)
            }

            // ... and the JAR for the subproject
            subprojectManifest.mainAttributes.let {
                assertThat(it).containsEntry(Attributes.Name("Created-By"), BUILD_TOOL)
                assertThat(it).containsEntry(Attributes.Name("Build-Jdk"), "Custom-Jdk") // Overridden in build.gradle.kts
                assertThat(it).containsEntry(Attributes.Name("Custom-Key"), "Custom-Value") // Set in build.gradle.kts
            }
        }
    }

    /**
     * Reads the META-INF/MANIFEST.MF file from a jar file located in the given zip file.
     *
     * @param zipFile      The [ZipFile] to search
     * @param jarEntryName The path to the jar inside zip-file
     * @return The [Manifest] of the jar file
     * @throws IllegalStateException If the jar file could not be found, or no META-INF/MANIFEST.MF file could be found within it
     * @throws IOException           if an I/O error occurs
     */
    private fun getManifestFromJar(zipFile: ZipFile, jarEntryName: String): Manifest {
        // Get JAR entry in ZIP
        val jarEntry = zipFile.getEntry(jarEntryName)
            ?: error(jarEntryName + " could not be found in zip file " + zipFile.name)

        // Extract JAR file into temp directory
        val outputFile = testDir.resolve("build").resolve("tmp").resolve("zip").resolve(jarEntryName)
        Files.createDirectories(outputFile.parentFile.toPath())

        outputFile.outputStream().buffered().use { output ->
            zipFile.getInputStream(jarEntry).buffered().use { it.copyTo(output) }
        }

        JarFile(outputFile).use { return it.manifest ?: error("Manifest is missing in JAR file $outputFile!") }
    }

    companion object {
        private val BUILD_TOOL = "Gradle " + System.getProperty("gradle.version")
        private val BUILD_JDK = System.getProperty("java.runtime.version") + " (" + System.getProperty("java.vendor") + ')'
    }

}