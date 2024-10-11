package org.gradle.plugins.fsm.tasks.bundling

import org.assertj.core.api.Assertions.assertThat
import org.gradle.plugins.fsm.FSMPlugin
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Paths
import java.util.zip.ZipFile

class FSMLibraryTest {

    @Test
    fun `webApp libs`(@TempDir testDir: File) {
        val resourcesUrl = FSMLibraryTest::class.java.classLoader.getResource("webapp-project")
            ?: error("test project files not found")
        val resourcesPath = Paths.get(resourcesUrl.toURI())
        resourcesPath.toFile().copyRecursively(testDir)

        // Execute the gradle build
        val result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments(FSMPlugin.FSM_TASK_NAME)
            .withPluginClasspath()
            .build()
        assertThat(result.task(':' + FSMPlugin.FSM_TASK_NAME)?.outcome).isEqualTo(TaskOutcome.SUCCESS)

        // get result
        val fsmFile = testDir.resolve("build/fsm/testproject-webapp-1.0-SNAPSHOT.fsm")
        assertThat(fsmFile).exists()
        ZipFile(fsmFile).use { zipFile ->
            // check contents of lib folder
            assertThat(zipFile.getEntry("lib/slf4j-api-2.0.16.jar")).isNotNull
            assertThat(zipFile.getEntry("lib/fs-isolated-runtime-5.2.241212.jar")).isNull()
            assertThat(zipFile.getEntry("lib/fs-isolated-webrt-5.2.241212.jar")).isNull()
        }
    }
}