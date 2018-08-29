package org.gradle.plugins.fsm.tasks.verification


import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.plugins.fsm.FSMPlugin
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
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
import static org.objectweb.asm.Opcodes.ACC_PUBLIC
import static org.objectweb.asm.Opcodes.V1_8

class IsolationCheckTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder()

    private Project project
    private File testDir
    private IsolationCheck isolationCheck

    private static final String JDK_CLASS = "java/lang/Object"
    private static final String DEPRECATED_API_CLASS = "de/espirit/firstspirit/access/UrlCreator"
    private static final String API_CLASS = "de/espirit/firstspirit/access/project/Project"
    private static final String RUNTIME_CLASS = "de/espirit/common/StringUtil"
    private static final String IMPL_CLASS = "de/espirit/common/impl/LegacyClassFactoryImpl"

    @Before
    void setup() {
        testDir = temporaryFolder.newFolder()
        project = ProjectBuilder.builder().withProjectDir(testDir).build()
        project.apply plugin: FSMPlugin.NAME
        isolationCheck = project.tasks[FSMPlugin.ISOLATION_CHECK_TASK_NAME] as IsolationCheck
        isolationCheck.setFirstSpiritVersion("5.2.2109")
        isolationCheck.setDetectorUrl("https://fsdev.e-spirit.de/FsmDependencyDetector/")
    }

    @Test
    void defaultComplianceLevel() {
        assertThat(isolationCheck.getComplianceLevel()).isSameAs(DEFAULT)
    }

    @Test(expected = GradleException.class)
    void emptyFsmWithHighestCompliance() {
        Task fsmTask = project.tasks[FSMPlugin.FSM_TASK_NAME]
        fsmTask.execute()

        isolationCheck.setComplianceLevel(HIGHEST)
        isolationCheck.execute()
    }

    @Test(expected = GradleException.class)
    void emptyFsmWithDefaultCompliance() {
        Task fsmTask = project.tasks[FSMPlugin.FSM_TASK_NAME]
        fsmTask.execute()

        isolationCheck.setComplianceLevel(DEFAULT)
        isolationCheck.execute()
    }

    @Test
    void emptyFsmWithMinimalCompliance() {
        Task fsmTask = project.tasks[FSMPlugin.FSM_TASK_NAME]
        fsmTask.execute()

        try {
            isolationCheck.setComplianceLevel(MINIMAL)
            isolationCheck.execute()
            failBecauseExceptionWasNotThrown(GradleException.class)
        } catch (final GradleException e) {
            assertThat(e).hasCauseInstanceOf(GradleException.class)
        }
    }

    @Test
    void fsmWithoutDependenciesWithHighestCompliance() {
        writeSingleClassToFsmFile(JDK_CLASS)

        isolationCheck.setComplianceLevel(HIGHEST)
        isolationCheck.execute()
    }

    @Test
    void fsmWithoutDependenciesWithDefaultCompliance() {
        writeSingleClassToFsmFile(JDK_CLASS)

        isolationCheck.setComplianceLevel(DEFAULT)
        isolationCheck.execute()
    }

    @Test
    void fsmWithoutDependenciesWithMinimalCompliance() {
        writeSingleClassToFsmFile(JDK_CLASS)

        isolationCheck.setComplianceLevel(MINIMAL)
        isolationCheck.execute()
    }

    @Test
    void fsmWithDeprecatedApiWithHighestCompliance() {
        writeSingleClassToFsmFile(DEPRECATED_API_CLASS)

        isolationCheck.setComplianceLevel(HIGHEST)

        try {
            isolationCheck.execute()
            failBecauseExceptionWasNotThrown(GradleException.class)
        } catch (final GradleException e) {
            assertThat(e).hasCauseInstanceOf(GradleException.class)
            assertThat(e.getCause()).hasMessageContaining(asQualified(DEPRECATED_API_CLASS) + " (1 usages)")

        }
    }

    @Test
    void fsmWithDeprecatedApiWithDefaultCompliance() {
        writeSingleClassToFsmFile(DEPRECATED_API_CLASS)

        isolationCheck.setComplianceLevel(DEFAULT)
        isolationCheck.execute()
    }

    @Test
    void fsmWithDeprecatedApiWithMinimalCompliance() {
        writeSingleClassToFsmFile(DEPRECATED_API_CLASS)

        isolationCheck.setComplianceLevel(MINIMAL)
        isolationCheck.execute()
    }

    @Test
    void fsmWithApiDependencyWithHighestCompliance() {
        writeSingleClassToFsmFile(API_CLASS)

        isolationCheck.setComplianceLevel(HIGHEST)
        isolationCheck.execute()
    }

    @Test
    void fsmWithApiDependencyWithDefaultCompliance() {
        writeSingleClassToFsmFile(API_CLASS)

        isolationCheck.setComplianceLevel(DEFAULT)
        isolationCheck.execute()
    }

    @Test
    void fsmWithApiDependencyWithMinimalCompliance() {
        writeSingleClassToFsmFile(API_CLASS)

        isolationCheck.setComplianceLevel(MINIMAL)
        isolationCheck.execute()
    }

    @Test
    void fsmWithRuntimeDependencyWithHighestCompliance() {
        writeSingleClassToFsmFile(RUNTIME_CLASS)

        isolationCheck.setComplianceLevel(HIGHEST)

        try {
            isolationCheck.execute()
            failBecauseExceptionWasNotThrown(GradleException.class)
        } catch (GradleException e) {
            assertThat(e).hasCauseInstanceOf(GradleException.class)
            assertThat(e.getCause()).hasMessageContaining(asQualified(RUNTIME_CLASS) + " (1 usages)")
        }
    }

    @Test
    void fsmWithRuntimeDependencyWithDefaultCompliance() {
        writeSingleClassToFsmFile(RUNTIME_CLASS)

        isolationCheck.setComplianceLevel(DEFAULT)

        try {
            isolationCheck.execute()
            failBecauseExceptionWasNotThrown(GradleException.class)
        } catch (GradleException e) {
            assertThat(e).hasCauseInstanceOf(GradleException.class)
            assertThat(e.getCause()).hasMessageContaining(asQualified(RUNTIME_CLASS) + " (1 usages)")
        }
    }

    @Test
    void fsmWithRuntimeDependencyWithMinimalCompliance() {
        writeSingleClassToFsmFile(RUNTIME_CLASS)

        isolationCheck.setComplianceLevel(MINIMAL)
        isolationCheck.execute()
    }


    @Test
    void fsmWithImplDependencyButNoUrlSet() {
        writeSingleClassToFsmFile(IMPL_CLASS)

        isolationCheck.setDetectorUrl("")
        isolationCheck.execute()
    }


    @Test
    void fsmWithImplDependencyWithHighestCompliance() {
        writeSingleClassToFsmFile(IMPL_CLASS)

        isolationCheck.setComplianceLevel(HIGHEST)
        try {
            isolationCheck.execute()
            failBecauseExceptionWasNotThrown(GradleException.class)
        } catch (final GradleException e) {
            assertThat(e).hasCauseInstanceOf(GradleException.class)
            assertThat(e.getCause()).hasMessageContaining(asQualified(IMPL_CLASS) + " (1 usages)")
        }
    }

    @Test
    void fsmWithImplDependencyWithDefaultCompliance() {
        writeSingleClassToFsmFile(IMPL_CLASS)

        isolationCheck.setComplianceLevel(DEFAULT)
        try {
            isolationCheck.execute()
            failBecauseExceptionWasNotThrown(GradleException.class)
        } catch (final GradleException e) {
            assertThat(e).hasCauseInstanceOf(GradleException.class)
            assertThat(e.getCause()).hasMessageContaining(asQualified(IMPL_CLASS) + " (1 usages)")
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
            isolationCheck.execute()
            failBecauseExceptionWasNotThrown(GradleException.class)
        } catch (final GradleException e) {
            assertThat(e).hasCauseInstanceOf(GradleException.class)
            assertThat(e.getCause()).hasMessageContaining(asQualified(IMPL_CLASS) + " (1 usages)")

        }
    }


    private void writeSingleClassToFsmFile(final String superClassName) throws IOException {
        final ClassWriter classWriter = new ClassWriter(0)
        classWriter.visit(V1_8, ACC_PUBLIC, "com.example.module/Test", null, superClassName, null)
        final byte[] classContent = classWriter.toByteArray()

        final Path jarFile = temporaryFolder.newFile(IsolationCheckTest.class.getSimpleName() + ".jar").toPath()

        final JarOutputStream jarOutputStream = new JarOutputStream(new FileOutputStream(jarFile.toFile()))
        final JarEntry jarEntry = new JarEntry("Test.class")
        jarOutputStream.putNextEntry(jarEntry)
        jarOutputStream.write(classContent)
        jarOutputStream.close()

        final Task fsmTask = project.tasks[FSMPlugin.FSM_TASK_NAME]
        final File fsmFile = fsmTask.getOutputs().files.singleFile
        Files.createDirectories(fsmFile.toPath().getParent())

        final ZipOutputStream zipOutputStream = new ZipOutputStream(new FileOutputStream(fsmFile))
        zipOutputStream.putNextEntry(new ZipEntry("META-INF/module.xml"))
        zipOutputStream.write("<module><name>TestModule</name><version>0.1</version><components/>".getBytes())
        zipOutputStream.write("<resources>".getBytes())
        zipOutputStream.write("""<resource scope="module">test.jar</resource>""".getBytes())
        zipOutputStream.write("</resources></module>".getBytes())
        zipOutputStream.putNextEntry(new ZipEntry("test.jar"))
        zipOutputStream.write(Files.readAllBytes(jarFile))
        zipOutputStream.close()
    }


}
