package org.gradle.plugins.fsm.tasks.bundling

import org.assertj.core.api.Assertions.assertThat
import org.gradle.plugins.fsm.FSMPlugin
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile
import kotlin.io.path.writeText

class FSMLicenseTest {

    @TempDir
    private lateinit var testDir: Path

    private lateinit var settingsFile: Path
    private lateinit var buildFile: Path

    @BeforeEach
    fun setUp() {
        buildFile = Files.createFile(testDir.resolve("build.gradle.kts"))
        settingsFile = Files.createFile(testDir.resolve("settings.gradle.kts"))
    }

    @Test
    fun `FSM with no dependencies`() {
        settingsFile.writeText("""rootProject.name = "testFsmNoLicenses"""")
        javaClass.classLoader.getResourceAsStream("licenses/fsmnodependency.gradle.kts")?.use {
            buildFile.writeText(it.reader().readText())
        }

        // Execute the gradle build
        val result = GradleRunner.create()
            .withProjectDir(testDir.toFile())
            .withArguments(FSMPlugin.FSM_TASK_NAME)
            .withPluginClasspath()
            .build()

        assertThat(result.task(":" + FSMPlugin.FSM_TASK_NAME)?.outcome).isEqualTo(TaskOutcome.SUCCESS)

        // examine result
        val fsmFile = testDir.resolve("build/fsm/testFsmNoLicenses-1.0-SNAPSHOT.fsm")
        assertThat(fsmFile).exists()
        ZipFile(fsmFile.toFile()).use { zipFile ->
            val licenseReport = zipFile.getEntry("META-INF/licenses.csv")
            zipFile.getInputStream(licenseReport).use { input ->
                // no licenses
                val licenses = input.reader().readText()
                assertThat(licenses).isEqualToNormalizingNewlines(LICENSE_HEADER)
            }
        }
    }

    @Test
    fun `FSM with licenses`() {
        settingsFile.writeText("""rootProject.name = "testFsmWithLicenses"""")
        javaClass.classLoader.getResourceAsStream("licenses/fsmdependency.gradle.kts")?.use {
            buildFile.writeText(it.reader().readText())
        }

        // Execute the gradle build
        val result = GradleRunner.create()
            .withProjectDir(testDir.toFile())
            .withArguments(FSMPlugin.FSM_TASK_NAME)
            .withPluginClasspath()
            .build()
        assertThat(result.task(":" + FSMPlugin.FSM_TASK_NAME)?.outcome).isEqualTo(TaskOutcome.SUCCESS)

        // get result
        val fsmFile = testDir.resolve("build/fsm/testFsmWithLicenses-1.0-SNAPSHOT.fsm")
        assertThat(fsmFile.toFile()).exists()
        ZipFile(fsmFile.toFile()).use { zipFile ->
            // test license file
            // first, get the expected licenses from a resource file
            val expectedLicenses = javaClass.classLoader.getResourceAsStream("licenses/fsmdependency_licenses.csv")?.use {
                it.reader().readText()
            }
            val licenseReport = zipFile.getEntry("META-INF/licenses.csv")
            zipFile.getInputStream(licenseReport).use {
                val licenses = it.reader().readText()
                assertThat(licenses).isEqualToNormalizingNewlines(expectedLicenses)
            }

            // check presence of license files
            assertThat(zipFile.getEntry("META-INF/licenses/jackson-databind-2.10.0.jar/LICENSE.txt")).isNotNull
            assertThat(zipFile.getEntry("META-INF/licenses/jackson-core-2.10.0.jar/LICENSE.txt")).isNotNull
            assertThat(zipFile.getEntry("META-INF/licenses/jackson-annotations-2.10.0.jar/LICENSE.txt")).isNotNull
        }
    }

    @Test
    fun `FSM with licenses in library`() {
        settingsFile.writeText("""rootProject.name = "testFsmLibWithLicenses"""")
        javaClass.classLoader.getResourceAsStream("licenses/fsm-library-dependency.gradle.kts")?.use {
            buildFile.writeText(it.reader().readText())
        }

        // Execute the gradle build
        val result = GradleRunner.create()
            .withProjectDir(testDir.toFile())
            .withArguments(FSMPlugin.FSM_TASK_NAME)
            .withPluginClasspath()
            .build()
        assertThat(result.task(":" + FSMPlugin.FSM_TASK_NAME)?.outcome).isEqualTo(TaskOutcome.SUCCESS)

        // get result
        val fsmFile = testDir.resolve("build/fsm/testFsmLibWithLicenses-1.0-SNAPSHOT.fsm")
        assertThat(fsmFile.toFile()).exists()
        ZipFile(fsmFile.toFile()).use { zipFile ->
            val licenseReport = zipFile.getEntry("META-INF/licenses.csv")
            val licenses = zipFile.getInputStream(licenseReport).use {
                it.reader().readText()
            }

            assertThat(licenses).contains(""""joda-time:joda-time:2.12.2","https://www.joda.org/joda-time/","Apache License, Version 2.0","https://www.apache.org/licenses/LICENSE-2.0.txt"""")
            assertThat(licenses).contains(""""com.fasterxml.jackson.core:jackson-databind:2.10.0","http://github.com/FasterXML/jackson","The Apache Software License, Version 2.0","http://www.apache.org/licenses/LICENSE-2.0.txt"""")

            // check presence of license files

            assertThat(zipFile.getEntry("META-INF/licenses/joda-time-2.12.2.jar/LICENSE.txt")).isNotNull
            assertThat(zipFile.getEntry("META-INF/licenses/jackson-databind-2.10.0.jar/LICENSE.txt")).isNotNull
        }
    }

    companion object {
        private const val LICENSE_HEADER = """"artifact","moduleUrl","moduleLicense","moduleLicenseUrl",
"""
    }

}