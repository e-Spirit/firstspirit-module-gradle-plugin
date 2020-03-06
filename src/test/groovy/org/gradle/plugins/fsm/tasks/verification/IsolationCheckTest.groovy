package org.gradle.plugins.fsm.tasks.verification

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.plugins.fsm.FSMPlugin
import org.gradle.plugins.fsm.FSMPluginExtension
import org.gradle.plugins.fsm.tasks.bundling.FSM
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.objectweb.asm.ClassWriter

import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

import static de.espirit.mavenplugins.fsmchecker.ComplianceLevel.*
import static org.assertj.core.api.Assertions.assertThat
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown
import static org.gradle.plugins.fsm.ComponentHelper.addTestModulesToBlacklist
import static org.gradle.plugins.fsm.util.TestProjectUtils.defineArtifactoryForProject
import static org.gradle.plugins.fsm.util.TestProjectUtils.setArtifactoryCredentialsFromLocalProperties
import static org.objectweb.asm.Opcodes.ACC_PUBLIC
import static org.objectweb.asm.Opcodes.V1_8

class IsolationCheckTest {

    private Project project
    private File testDir
    private Path jarDir
    private IsolationCheck isolationCheck

    private static final String JDK_CLASS = "java/lang/Object"
    private static final String DEPRECATED_API_CLASS = "de/espirit/firstspirit/access/UrlCreator"
    private static final String API_CLASS = "de/espirit/firstspirit/access/project/Project"
    private static final String RUNTIME_CLASS = "de/espirit/common/StringUtil"
    private static final String IMPL_CLASS = "de/espirit/common/impl/LegacyClassFactoryImpl"

    @BeforeEach
    void setup(@TempDir File tempDir, @TempDir Path jarDir) {
        testDir = tempDir
        this.jarDir = jarDir
        project = ProjectBuilder.builder().withProjectDir(testDir).build()
        setArtifactoryCredentialsFromLocalProperties(project)
        defineArtifactoryForProject(project)
        project.apply plugin: FSMPlugin.NAME
        isolationCheck = project.tasks[FSMPlugin.ISOLATION_CHECK_TASK_NAME] as IsolationCheck
        isolationCheck.setFirstSpiritVersion("5.2.200105")
        isolationCheck.setDetectorUrl("https://fsdev.e-spirit.de/FsmDependencyDetector/")
        FSM fsm = project.tasks[FSMPlugin.FSM_TASK_NAME] as FSM
        addTestModulesToBlacklist(fsm)
    }


    @Test
    void theTaskNameShouldBeComposedByAVerbAndAnObject(){
        String verb = "check"
        String object = "Isolation"
        assertThat(project.tasks[verb + object]).isNotNull();
    }


    @Test
    void defaultComplianceLevel() {
        assertThat(isolationCheck.getComplianceLevel()).isSameAs(DEFAULT)
    }

    @Test
    void emptyFsmWithHighestCompliance() {
        FSMPluginExtension pluginExtension = project.extensions.getByType(FSMPluginExtension.class)
        pluginExtension.moduleDirName = getClass().getClassLoader().getResource("empty").path

        Task fsmTask = project.tasks[FSMPlugin.FSM_TASK_NAME]
        fsmTask.execute()

        isolationCheck.setComplianceLevel(HIGHEST)
        isolationCheck.check()
    }

    @Test
    void emptyFsmWithDefaultCompliance() {
        FSMPluginExtension pluginExtension = project.extensions.getByType(FSMPluginExtension.class)
        pluginExtension.moduleDirName = getClass().getClassLoader().getResource("empty").path

        Task fsmTask = project.tasks[FSMPlugin.FSM_TASK_NAME]
        fsmTask.execute()

        isolationCheck.setComplianceLevel(DEFAULT)
        isolationCheck.check()
    }

    @Test
    void emptyFsmWithMinimalCompliance() {
        FSMPluginExtension pluginExtension = project.extensions.getByType(FSMPluginExtension.class)
        pluginExtension.moduleDirName = getClass().getClassLoader().getResource("empty").path

        FSM fsmTask = project.tasks[FSMPlugin.FSM_TASK_NAME] as FSM
        fsmTask.execute()

        isolationCheck.setComplianceLevel(MINIMAL)
        isolationCheck.check()
    }

    @Test
    void fsmWithoutDependenciesWithHighestCompliance() {
        writeSingleClassToFsmFile(JDK_CLASS)

        isolationCheck.setComplianceLevel(HIGHEST)
        isolationCheck.check()
    }

    @Test
    void fsmWithoutDependenciesWithDefaultCompliance() {
        writeSingleClassToFsmFile(JDK_CLASS)

        isolationCheck.setComplianceLevel(DEFAULT)
        isolationCheck.check()
    }

    @Test
    void fsmWithoutDependenciesWithMinimalCompliance() {
        writeSingleClassToFsmFile(JDK_CLASS)

        isolationCheck.setComplianceLevel(MINIMAL)
        isolationCheck.check()
    }

    @Test
    void fsmWithDeprecatedApiWithHighestCompliance() {
        writeSingleClassToFsmFile(DEPRECATED_API_CLASS)

        isolationCheck.setComplianceLevel(HIGHEST)

        try {
            isolationCheck.check()
            failBecauseExceptionWasNotThrown(GradleException.class)
        } catch (final GradleException e) {
            assertThat(e).hasMessageContaining(asQualified(DEPRECATED_API_CLASS) + " (1 usages)")

        }
    }

    @Test
    void fsmWithDeprecatedApiWithDefaultCompliance() {
        writeSingleClassToFsmFile(DEPRECATED_API_CLASS)

        isolationCheck.setComplianceLevel(DEFAULT)
        isolationCheck.check()
    }

    @Test
    void fsmWithDeprecatedApiWithMinimalCompliance() {
        writeSingleClassToFsmFile(DEPRECATED_API_CLASS)

        isolationCheck.setComplianceLevel(MINIMAL)
        isolationCheck.check()
    }

    @Test
    void fsmWithApiDependencyWithHighestCompliance() {
        writeSingleClassToFsmFile(API_CLASS)

        isolationCheck.setComplianceLevel(HIGHEST)
        isolationCheck.check()
    }

    @Test
    void fsmWithApiDependencyWithDefaultCompliance() {
        writeSingleClassToFsmFile(API_CLASS)

        isolationCheck.setComplianceLevel(DEFAULT)
        isolationCheck.check()
    }

    @Test
    void fsmWithApiDependencyWithMinimalCompliance() {
        writeSingleClassToFsmFile(API_CLASS)

        isolationCheck.setComplianceLevel(MINIMAL)
        isolationCheck.check()
    }

    @Test
    void fsmWithRuntimeDependencyWithHighestCompliance() {
        writeSingleClassToFsmFile(RUNTIME_CLASS)

        isolationCheck.setComplianceLevel(HIGHEST)

        try {
            isolationCheck.check()
            failBecauseExceptionWasNotThrown(GradleException.class)
        } catch (GradleException e) {
            assertThat(e).hasMessageContaining(asQualified(RUNTIME_CLASS) + " (1 usages)")
        }
    }

    @Test
    void fsmWithRuntimeDependencyWithDefaultCompliance() {
        writeSingleClassToFsmFile(RUNTIME_CLASS)

        isolationCheck.setComplianceLevel(DEFAULT)

        try {
            isolationCheck.check()
            failBecauseExceptionWasNotThrown(GradleException.class)
        } catch (GradleException e) {
            assertThat(e).hasMessageContaining(asQualified(RUNTIME_CLASS) + " (1 usages)")
        }
    }

    @Test
    void fsmWithRuntimeDependencyWithMinimalCompliance() {
        writeSingleClassToFsmFile(RUNTIME_CLASS)

        isolationCheck.setComplianceLevel(MINIMAL)
        isolationCheck.check()
    }


    @Test
    void fsmWithImplDependencyButNoUrlSet() {
        writeSingleClassToFsmFile(IMPL_CLASS)

        isolationCheck.setDetectorUrl("")
        isolationCheck.check()
    }


    @Test
    void fsmWithImplDependencyWithHighestCompliance() {
        writeSingleClassToFsmFile(IMPL_CLASS)

        isolationCheck.setComplianceLevel(HIGHEST)
        try {
            isolationCheck.check()
            failBecauseExceptionWasNotThrown(GradleException.class)
        } catch (final GradleException e) {
            assertThat(e).hasMessageContaining(asQualified(IMPL_CLASS) + " (1 usages)")
        }
    }

    @Test
    void fsmWithImplDependencyWithDefaultCompliance() {
        writeSingleClassToFsmFile(IMPL_CLASS)

        isolationCheck.setComplianceLevel(DEFAULT)
        try {
            isolationCheck.check()
            failBecauseExceptionWasNotThrown(GradleException.class)
        } catch (final GradleException e) {
            assertThat(e).hasMessageContaining(asQualified(IMPL_CLASS) + " (1 usages)")
        }
    }

    private static String asQualified(String classAsFS) {
        classAsFS.replace("/", ".")
    }

    @Test
    void fsmWithImplDependencyWithMinimalCompliance() {
        writeSingleClassToFsmFile(IMPL_CLASS)

        isolationCheck.setComplianceLevel(MINIMAL)
        try {
            isolationCheck.check()
            failBecauseExceptionWasNotThrown(GradleException.class)
        } catch (final GradleException e) {
            assertThat(e).hasMessageContaining(asQualified(IMPL_CLASS) + " (1 usages)")

        }
    }

    @Test
    void ignoreResource() {
        writeSingleClassToFsmFile(IMPL_CLASS)

        isolationCheck.setComplianceLevel(DEFAULT)
        isolationCheck.whitelistedResources = ['de.espirit:test:1.0']
        isolationCheck.check()
    }


    @Test
    void contentCreatorConflict() {
        writeSingleClassToFsmFile(IMPL_CLASS)

        isolationCheck.setComplianceLevel(DEFAULT)
        isolationCheck.contentCreatorComponents = ['contentCreatorComponent']

        try {
            isolationCheck.check()
        } catch (final GradleException e) {
            assertThat(e).hasMessageContaining("com.fasterxml.jackson.annotation.JacksonAnnotation (1 usages)");
        }
    }


    private void writeSingleClassToFsmFile(final String superClassName) throws IOException {
        final ClassWriter classWriter = new ClassWriter(0)
        classWriter.visit(V1_8, ACC_PUBLIC, "com.fasterxml.jackson.annotation/JacksonAnnotation", null, superClassName, null)
        final byte[] classContent = classWriter.toByteArray()

        final Path jarFile = jarDir.resolve(IsolationCheckTest.class.getSimpleName() + ".jar")

        final JarOutputStream jarOutputStream = new JarOutputStream(new FileOutputStream(jarFile.toFile()))
        final JarEntry jarEntry = new JarEntry("JacksonAnnotation.class")
        jarOutputStream.putNextEntry(jarEntry)
        jarOutputStream.write(classContent)
        jarOutputStream.close()

        final Task fsmTask = project.tasks[FSMPlugin.FSM_TASK_NAME]
        final File fsmFile = fsmTask.getOutputs().files.singleFile
        Files.createDirectories(fsmFile.toPath().getParent())

        final ZipOutputStream zipOutputStream = new ZipOutputStream(new FileOutputStream(fsmFile))
        zipOutputStream.putNextEntry(new ZipEntry("META-INF/module.xml"))
        zipOutputStream.write("<module><name>TestModule</name><version>0.1</version>".getBytes())
        zipOutputStream.write("<components>".getBytes());
        zipOutputStream.write("<web-app>".getBytes());
        zipOutputStream.write("<name>contentCreatorComponent</name>".getBytes());
        zipOutputStream.write("<web-xml>web.xml</web-xml>".getBytes());
        zipOutputStream.write("<web-resources>".getBytes());
        zipOutputStream.write("""<resource name="de.espirit:test" version="1.0">test.jar</resource>""".getBytes())
        zipOutputStream.write("</web-resources>".getBytes());
        zipOutputStream.write("</web-app>".getBytes());
        zipOutputStream.write("</components>".getBytes());
        zipOutputStream.write("<resources>".getBytes())
        zipOutputStream.write("""<resource name="de.espirit:test" version="1.0" scope="module">test.jar</resource>""".getBytes())
        zipOutputStream.write("</resources></module>".getBytes())
        zipOutputStream.putNextEntry(new ZipEntry("test.jar"))
        zipOutputStream.write(Files.readAllBytes(jarFile))
        zipOutputStream.putNextEntry(new ZipEntry("web.xml"));
        zipOutputStream.write("<web-app/>".getBytes());
        zipOutputStream.close()
    }


}
