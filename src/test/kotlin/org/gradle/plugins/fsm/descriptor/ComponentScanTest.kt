package org.gradle.plugins.fsm.descriptor

import org.assertj.core.api.Assertions.assertThat
import org.gradle.plugins.fsm.FSMManifestTest
import org.gradle.plugins.fsm.FSMPlugin
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.redundent.kotlin.xml.Node
import org.redundent.kotlin.xml.parse
import java.io.File
import java.util.zip.ZipFile

/**
 * Tests the [ComponentScan] by executing a Gradle build on a test project. Checks that exactly the expected components
 * were included in the module-isolated.xml. See [ComponentScan] for info on how class scanning and class loading works.
 */
class ComponentScanTest {

    @TempDir
    private lateinit var testDir: File

    @Test
    fun test() {
        // Copy Test project into temp dir
        val resourcesUrl = FSMManifestTest::class.java.classLoader.getResource("components")
                ?: error("Components project dir not found")
        File(resourcesUrl.toURI()).copyRecursively(testDir)

        // Execute Gradle build
        val result = GradleRunner.create()
                .withProjectDir(testDir)
                .withArguments(FSMPlugin.FSM_TASK_NAME)
                .withPluginClasspath()
                .build()
        assertThat(result.task(":${FSMPlugin.FSM_TASK_NAME}")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)

        val outputFile = testDir.resolve(OUTPUT_FSM_FILE)
        val moduleDescriptor = getModuleDescriptor(outputFile)

        // We expect exactly 2 components
        val components = moduleDescriptor.first("components")
        assertThat(components.filter { true }.size == 2)

        // Test that certain components are included, but others were not.
        // See the test project for details
        val abstractWebAppImpl = componentWithName(components, "AbstractWebAppImpl")
        assertThat(abstractWebAppImpl).describedAs("WebApp 'AbstractWebAppImpl' should be included in module-isolated.xml!").isNotNull
        assertThat(abstractWebAppImpl!!.childText("class")).isEqualTo("de.espirit.AbstractWebAppImpl")

        val myAbstractWebAppImpl = componentWithName(components, "MyAbstractWebAppImpl")
        assertThat(myAbstractWebAppImpl).describedAs("WebApp 'MyAbstractWebAppImpl' should be included in module-isolated.xml!").isNotNull
        assertThat(myAbstractWebAppImpl!!.childText("class")).isEqualTo("de.espirit.MyAbstractWebAppImpl")

        // Test external components that should not have been included
        val unrelatedWebApp = componentWithName(components, "UnrelatedWebApp")
        assertThat(unrelatedWebApp).describedAs("WebApp 'UnrelatedWebApp' should not be included in module-isolated.xml!").isNull()
    }

    private fun getModuleDescriptor(fsmFile: File): Node {
        ZipFile(fsmFile).use { zipFile ->
            val moduleXmlEntry = zipFile.getEntry("META-INF/module-isolated.xml")
            assertThat(moduleXmlEntry).isNotNull
            zipFile.getInputStream(moduleXmlEntry).buffered().use {
                return parse(it)
            }
        }
    }

    private fun componentWithName(components: Node, name: String): Node? {
        return components.filter { it.childText("name") == name }.firstOrNull()
    }

    companion object {
        const val OUTPUT_FSM_FILE = "build/fsm/test-components-scan-1.0.fsm"
    }

}