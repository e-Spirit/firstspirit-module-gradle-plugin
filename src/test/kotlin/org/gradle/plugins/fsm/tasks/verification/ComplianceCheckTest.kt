package org.gradle.plugins.fsm.tasks.verification

import org.assertj.core.api.Assertions.assertThat
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.plugins.fsm.FSMPlugin
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Paths

class ComplianceCheckTest {

    @Test
    fun `pass for valid project`(@TempDir testDir: File) {
        val resourcesUrl = ComplianceCheckTest::class.java.classLoader.getResource("webapp-project")
            ?: error("test project files not found")
        val resourcesPath = Paths.get(resourcesUrl.toURI())
        resourcesPath.toFile().copyRecursively(testDir)

        // Execute the gradle build
        val result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments(FSMPlugin.COMPLIANCE_CHECK_TASK_NAME)
            .withPluginClasspath()
            .build()
        assertThat(result.task(':' + FSMPlugin.COMPLIANCE_CHECK_TASK_NAME)?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    }

    @Test
    fun `detect non-api return value`(@TempDir testDir: File) {
        testWithNonApiClass(testDir, """
            import de.espirit.common.FactoryRegistry;
            
            public class Broken {
                public FactoryRegistry getFactoryRegistry() { return null; }
            }
        """.trimIndent())
    }

    @Test
    fun `detect non-api parameter`(@TempDir testDir: File) {
        testWithNonApiClass(testDir, """
            import de.espirit.common.FactoryRegistry;
            
            public class Broken {
                public void useFactoryRegistry(FactoryRegistry registry) { }
            }
        """.trimIndent())
    }

    @Test
    fun `detect non-api inheritance`(@TempDir testDir: File) {
        testWithNonApiClass(testDir, """
            import de.espirit.common.JarFileCache;
            
            public class Broken extends JarFileCache {
            	public Broken(String name) {
            		super(name);
            	}
            }
        """.trimIndent())
    }

    @Test
    fun `detect non-api exception thrown`(@TempDir testDir: File) {
        testWithNonApiClass(testDir, """
            import de.espirit.firstspirit.common.DuplicateGidException;
            
            public class Broken {
            	public void breakNow() throws DuplicateGidException { }
            }
        """.trimIndent())
    }

    @Test
    fun `detect non-extendable inheritance`(@TempDir testDir: File) {
        testWithNonApiClass(testDir, """
            import de.espirit.firstspirit.access.store.templatestore.gom.DefaultGomEntryList;
            
            public class Broken extends DefaultGomEntryList { }
        """.trimIndent())
    }

    @Test
    fun `detect deprecated class usage`(@TempDir testDir: File) {
        testWithNonApiClass(testDir, """
            import de.espirit.firstspirit.access.UrlCreator;
            
            public class Broken {
                public UrlCreator urlCreator() { return null; }
             }
        """.trimIndent())
    }

    @Test
    fun `detect deprecated method usage`(@TempDir testDir: File) {
        testWithNonApiClass(testDir, """
            import de.espirit.firstspirit.access.AdminService;
            
            public class Broken {
                public void useMethod(AdminService adminService) { adminService.getServerConfiguration(); }
             }
        """.trimIndent())
    }

    @Test
    fun `use specified FirstSpirit version`(@TempDir testDir: File) {
        prepareSources(testDir, """
            import de.espirit.firstspirit.access.ServiceLocator;
            
            public class Broken {
                public ServiceLocator serviceLocator() { return null; }
            }
        """.trimIndent())

        GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments(FSMPlugin.COMPLIANCE_CHECK_TASK_NAME)
            .withPluginClasspath()
            .build()

        // 2024-10 did not contain ServiceLocator in the access API
        val result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments("-PcomplianceCheckFsVersion=5.2.241009", FSMPlugin.COMPLIANCE_CHECK_TASK_NAME)
            .withPluginClasspath()
            .buildAndFail()
        assertThat(result.task(':' + FSMPlugin.COMPLIANCE_CHECK_TASK_NAME)?.outcome).isEqualTo(TaskOutcome.FAILED)
    }

    private fun testWithNonApiClass(testDir: File, source: String) {
        prepareSources(testDir, source)

        // First try a build without compliance check
        GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments(JavaBasePlugin.BUILD_TASK_NAME)
            .withPluginClasspath()
            .build()

        // Execute the gradle build
        val result = GradleRunner.create()
            .withProjectDir(testDir)
            .withArguments(FSMPlugin.COMPLIANCE_CHECK_TASK_NAME)
            .withPluginClasspath()
            .buildAndFail()
        assertThat(result.task(':' + FSMPlugin.COMPLIANCE_CHECK_TASK_NAME)?.outcome).isEqualTo(TaskOutcome.FAILED)
    }

    private fun prepareSources(testDir: File, source: String) {
        val resourcesUrl = ComplianceCheckTest::class.java.classLoader.getResource("webapp-project")
            ?: error("test project files not found")
        val resourcesPath = Paths.get(resourcesUrl.toURI())
        resourcesPath.toFile().copyRecursively(testDir)

        testDir.resolve("webapp-project/src/main/java/Broken.java").writeText(source)
    }

}