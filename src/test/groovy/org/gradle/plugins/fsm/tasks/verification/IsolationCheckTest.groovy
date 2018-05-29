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

import static org.assertj.core.api.Assertions.assertThat
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown
import static org.gradle.plugins.fsm.tasks.verification.IsolationLevel.*
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
    void defaultIsolationLevel() {
        assertThat(isolationCheck.getIsolationLevel()).isSameAs(RUNTIME_USAGE)
    }

    @Test
    void emptyFsmDeprecationLevel() {
        Task fsmTask = project.tasks[FSMPlugin.FSM_TASK_NAME]
        fsmTask.execute()

        isolationCheck.setIsolationLevel(DEPRECATED_API_USAGE)
        isolationCheck.execute()
    }

    @Test
    void emptyFsmRuntimeLevel() {
        Task fsmTask = project.tasks[FSMPlugin.FSM_TASK_NAME]
        fsmTask.execute()

        isolationCheck.setIsolationLevel(RUNTIME_USAGE)
        isolationCheck.execute()
    }

    @Test
    void emptyFsmImplLevel() {
        Task fsmTask = project.tasks[FSMPlugin.FSM_TASK_NAME]
        fsmTask.execute()

        isolationCheck.setIsolationLevel(IMPL_USAGE)
        isolationCheck.execute()
    }

    @Test
    void fsmWithoutDependenciesDeprecationLevel() {
        writeSingleClassToFsmFile(JDK_CLASS)

        isolationCheck.setIsolationLevel(DEPRECATED_API_USAGE)
        isolationCheck.execute()
    }

    @Test
    void fsmWithoutDependenciesRuntimeLevel() {
        writeSingleClassToFsmFile(JDK_CLASS)

        isolationCheck.setIsolationLevel(RUNTIME_USAGE)
        isolationCheck.execute()
    }

    @Test
    void fsmWithoutDependenciesImplLevel() {
        writeSingleClassToFsmFile(JDK_CLASS)

        isolationCheck.setIsolationLevel(IMPL_USAGE)
        isolationCheck.execute()
    }

    @Test
    void fsmWithDeprecatedApiUsageDeprecationLevel() {
        writeSingleClassToFsmFile(DEPRECATED_API_CLASS)

        isolationCheck.setIsolationLevel(DEPRECATED_API_USAGE)

        try {
            isolationCheck.execute()
            failBecauseExceptionWasNotThrown(GradleException.class)
        } catch (final GradleException e) {
            assertThat(e).hasCause(new GradleException("Isolation check failed: Usage of deprecated API detected"))
        }
    }

    @Test
    void fsmWithDeprecatedApiUsageRuntimeLevel() {
        writeSingleClassToFsmFile(DEPRECATED_API_CLASS)

        isolationCheck.setIsolationLevel(RUNTIME_USAGE)
        isolationCheck.execute()
    }

    @Test
    void fsmWithDeprecatedApiUsageImplLevel() {
        writeSingleClassToFsmFile(DEPRECATED_API_CLASS)

        isolationCheck.setIsolationLevel(IMPL_USAGE)
        isolationCheck.execute()
    }

    @Test
    void fsmWithApiDependencyDeprecationLevel() {
        writeSingleClassToFsmFile(API_CLASS)

        isolationCheck.setIsolationLevel(DEPRECATED_API_USAGE)
        isolationCheck.execute()
    }

    @Test
    void fsmWithApiDependencyRuntimeLevel() {
        writeSingleClassToFsmFile(API_CLASS)

        isolationCheck.setIsolationLevel(RUNTIME_USAGE)
        isolationCheck.execute()
    }

    @Test
    void fsmWithApiDependencyImplLevel() {
        writeSingleClassToFsmFile(API_CLASS)

        isolationCheck.setIsolationLevel(IMPL_USAGE)
        isolationCheck.execute()
    }

    @Test
    void fsmWithRuntimeDependencyDeprecationLevel() {
        writeSingleClassToFsmFile(RUNTIME_CLASS)

        isolationCheck.setIsolationLevel(DEPRECATED_API_USAGE)

        try {
            isolationCheck.execute()
            failBecauseExceptionWasNotThrown(GradleException.class)
        } catch (GradleException e) {
            assertThat(e).hasCause(new GradleException("Isolation check failed: Usage of classes detected which are not part of the public API"))
        }
    }

    @Test
    void fsmWithRuntimeDependencyRuntimeLevel() {
        writeSingleClassToFsmFile(RUNTIME_CLASS)

        isolationCheck.setIsolationLevel(RUNTIME_USAGE)

        try {
            isolationCheck.execute()
            failBecauseExceptionWasNotThrown(GradleException.class)
        } catch (GradleException e) {
            assertThat(e).hasCause(new GradleException("Isolation check failed: Usage of classes detected which are not part of the public API"))
        }
    }

    @Test
    void fsmWithRuntimeDependencyImplLevel() {
        writeSingleClassToFsmFile(RUNTIME_CLASS)

        isolationCheck.setIsolationLevel(IMPL_USAGE)
        isolationCheck.execute()
    }


    @Test
    void fsmWithImplDependencyDisabled() {
        writeSingleClassToFsmFile(IMPL_CLASS)

        isolationCheck.setDetectorUrl("")
        isolationCheck.execute()
    }


    @Test
    void fsmWithImplDependencyDeprecationLevel() {
        writeSingleClassToFsmFile(IMPL_CLASS)

        isolationCheck.setIsolationLevel(DEPRECATED_API_USAGE)
        try {
            isolationCheck.execute()
            failBecauseExceptionWasNotThrown(GradleException.class)
        } catch (final GradleException e) {
            assertThat(e).hasCause(new GradleException("Isolation check failed: Usage of classes detected which are not part of the isolated runtime"))
        }
    }

    @Test
    void fsmWithImplDependencyRuntimeLevel() {
        writeSingleClassToFsmFile(IMPL_CLASS)

        isolationCheck.setIsolationLevel(RUNTIME_USAGE)
        try {
            isolationCheck.execute()
            failBecauseExceptionWasNotThrown(GradleException.class)
        } catch (final GradleException e) {
            assertThat(e).hasCause(new GradleException("Isolation check failed: Usage of classes detected which are not part of the isolated runtime"))
        }
    }

    @Test
    void fsmWithImplDependencyImplLevel() {
        writeSingleClassToFsmFile(IMPL_CLASS)

        isolationCheck.setIsolationLevel(IMPL_USAGE)
        try {
            isolationCheck.execute()
            failBecauseExceptionWasNotThrown(GradleException.class)
        } catch (final GradleException e) {
            assertThat(e).hasCause(new GradleException("Isolation check failed: Usage of classes detected which are not part of the isolated runtime"))
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
