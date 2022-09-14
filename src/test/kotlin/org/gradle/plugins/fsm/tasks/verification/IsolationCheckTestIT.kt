package org.gradle.plugins.fsm.tasks.verification

import de.espirit.mavenplugins.fsmchecker.ComplianceLevel.*
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.gradle.api.Project
import org.gradle.plugins.fsm.FSMPlugin
import org.gradle.plugins.fsm.FSMPluginExtension
import org.gradle.plugins.fsm.descriptor.defineArtifactoryForProject
import org.gradle.plugins.fsm.descriptor.setArtifactoryCredentialsFromLocalProperties
import org.gradle.plugins.fsm.tasks.bundling.FSM
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes.*
import java.io.File
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createDirectories
import kotlin.io.path.outputStream
import kotlin.io.path.readBytes

class IsolationCheckTestIT {

    private lateinit var jarDir: Path
    private lateinit var project: Project
    private lateinit var isolationCheck: IsolationCheck
    private lateinit var fsm: FSM

    @BeforeEach
    fun setup(@TempDir tempDir: File, @TempDir jarDir: Path) {
        this.jarDir = jarDir
        project = ProjectBuilder.builder().withProjectDir(tempDir).build()
        project.setArtifactoryCredentialsFromLocalProperties()
        project.defineArtifactoryForProject()
        project.plugins.apply(FSMPlugin.NAME)
        isolationCheck = project.tasks.getByName(FSMPlugin.ISOLATION_CHECK_TASK_NAME) as IsolationCheck
        isolationCheck.setFirstSpiritVersion("5.2.220409")
        isolationCheck.setDetectorUrl("https://fsdev.e-spirit.de/FsmDependencyDetector/")
        fsm = project.tasks.getByName(FSMPlugin.FSM_TASK_NAME) as FSM
    }


    @Test
    fun `the task name should be composed by a verb and an object`() {
        val verb = "check"
        val obj = "Isolation"
        assertThat(project.tasks.getByName(verb + obj)).isNotNull
    }

    @Test
    fun `default compliance level`() {
        assertThat(isolationCheck.getComplianceLevel()).isSameAs(DEFAULT)
    }

    @Test
    fun `empty FSM with highest compliance`() {
        val pluginExtension = project.extensions.getByType(FSMPluginExtension::class.java)
        pluginExtension.moduleDirName = IsolationCheckTestIT::class.java.getResource("/empty")?.path

        fsm.execute()

        isolationCheck.setComplianceLevel(HIGHEST)
        isolationCheck.check()
    }

    @Test
    fun `empty FSM with default compliance`() {
        val pluginExtension = project.extensions.getByType(FSMPluginExtension::class.java)
        pluginExtension.moduleDirName = IsolationCheckTestIT::class.java.getResource("/empty")?.path

        fsm.execute()

        isolationCheck.setComplianceLevel(DEFAULT)
        isolationCheck.check()
    }

    @Test
    fun `empty FSM with minimal compliance`() {
        val pluginExtension = project.extensions.getByType(FSMPluginExtension::class.java)
        pluginExtension.moduleDirName = IsolationCheckTestIT::class.java.getResource("/empty")?.path

        fsm.execute()

        isolationCheck.setComplianceLevel(MINIMAL)
        isolationCheck.check()
    }

    @Test
    fun `FSM without dependencies with highest compliance`() {
        writeSingleClassToFsmFile(JDK_CLASS)

        isolationCheck.setComplianceLevel(HIGHEST)
        isolationCheck.check()
    }

    @Test
    fun `FSM without dependencies with default compliance`() {
        writeSingleClassToFsmFile(JDK_CLASS)

        isolationCheck.setComplianceLevel(DEFAULT)
        isolationCheck.check()
    }

    @Test
    fun `FSM without dependencies with minimal compliance`() {
        writeSingleClassToFsmFile(JDK_CLASS)

        isolationCheck.setComplianceLevel(MINIMAL)
        isolationCheck.check()
    }

    @Test
    fun `FSM with deprecated API with highest compliance`() {
        writeSingleClassToFsmFile(DEPRECATED_API_CLASS)

        isolationCheck.setComplianceLevel(HIGHEST)

        assertThatThrownBy { isolationCheck.check() }
            .hasMessageContaining(asQualified(DEPRECATED_API_CLASS) + " (1 usages)")
    }

    @Test
    fun `FSM with deprecated API with default compliance`() {
        writeSingleClassToFsmFile(DEPRECATED_API_CLASS)

        isolationCheck.setComplianceLevel(DEFAULT)
        isolationCheck.check()
    }

    @Test
    fun `FSM with deprecated API with minimal compliance`() {
        writeSingleClassToFsmFile(DEPRECATED_API_CLASS)

        isolationCheck.setComplianceLevel(MINIMAL)
        isolationCheck.check()
    }

    @Test
    fun `FSM with API dependency with highest compliance`() {
        writeSingleClassToFsmFile(API_CLASS)

        isolationCheck.setComplianceLevel(HIGHEST)
        isolationCheck.check()
    }

    @Test
    fun `FSM with API dependency with default compliance`() {
        writeSingleClassToFsmFile(API_CLASS)

        isolationCheck.setComplianceLevel(DEFAULT)
        isolationCheck.check()
    }

    @Test
    fun `FSM with API dependency with minimal compliance`() {
        writeSingleClassToFsmFile(API_CLASS)

        isolationCheck.setComplianceLevel(MINIMAL)
        isolationCheck.check()
    }

    @Test
    fun `FSM with runtime dependency with highest compliance`() {
        writeSingleClassToFsmFile(RUNTIME_CLASS)

        isolationCheck.setComplianceLevel(HIGHEST)

        assertThatThrownBy { isolationCheck.check() }.hasMessageContaining(asQualified(RUNTIME_CLASS) + " (1 usages)")
    }

    @Test
    fun `FSM with runtime dependency with default compliance`() {
        writeSingleClassToFsmFile(RUNTIME_CLASS)

        isolationCheck.setComplianceLevel(DEFAULT)

        assertThatThrownBy { isolationCheck.check() }.hasMessageContaining(asQualified(RUNTIME_CLASS) + " (1 usages)")
    }

    @Test
    fun `FSM with runtime dependency with minimal compliance`() {
        writeSingleClassToFsmFile(RUNTIME_CLASS)

        isolationCheck.setComplianceLevel(MINIMAL)
        isolationCheck.check()
    }


    @Test
    fun `FSM with impl dependency but no URL set`() {
        writeSingleClassToFsmFile(IMPL_CLASS)

        isolationCheck.setDetectorUrl("")
        isolationCheck.check()
    }


    @Test
    fun `FSM with impl dependency with highest compliance`() {
        writeSingleClassToFsmFile(IMPL_CLASS)

        isolationCheck.setComplianceLevel(HIGHEST)

        assertThatThrownBy { isolationCheck.check() }.hasMessageContaining(asQualified(IMPL_CLASS) + " (1 usages)")
    }

    @Test
    fun `FSM with impl dependency with default compliance`() {
        writeSingleClassToFsmFile(IMPL_CLASS)

        isolationCheck.setComplianceLevel(DEFAULT)

        assertThatThrownBy { isolationCheck.check() }.hasMessageContaining(asQualified(IMPL_CLASS) + " (1 usages)")
    }

    @Test
    fun `FSM with impl dependency with minimal compliance`() {
        writeSingleClassToFsmFile(IMPL_CLASS)

        isolationCheck.setComplianceLevel(MINIMAL)

        assertThatThrownBy { isolationCheck.check() }.hasMessageContaining(asQualified(IMPL_CLASS) + " (1 usages)")
    }

    @Test
    fun `ignore resource`() {
        writeSingleClassToFsmFile(IMPL_CLASS)

        isolationCheck.setComplianceLevel(DEFAULT)
        isolationCheck.setWhitelistedResources(listOf("de.espirit:test:1.0"))
        isolationCheck.check()
    }


    @Test
    fun contentCreatorConflict() {
        writeSingleClassToFsmFile(IMPL_CLASS)

        isolationCheck.setComplianceLevel(DEFAULT)
        isolationCheck.setContentCreatorComponents(listOf("contentCreatorComponent"))

        assertThatThrownBy { isolationCheck.check() }
            .hasMessageContaining("com.fasterxml.jackson.annotation.JacksonAnnotation (1 usages)")
    }

    @Test
    fun `has FSM as only task input`() {
        val isolationCheckInputs = isolationCheck.inputs.files.toList()
        assertThat(isolationCheckInputs).hasSize(1)
        assertThat(isolationCheckInputs.first().name).isEqualTo("test.fsm")
    }

    @Test
    fun `detect FirstSpirit artifacts`() {
        val idProvider = ClassWriter(0)
        val project = ClassWriter(0)
        val connection = ClassWriter(0)
        idProvider.visit(
            V11, ACC_PUBLIC, "de.espirit.firstspirit.access.store/IDProvider",
            null, "java/lang/Object", null)
        project.visit(
            V11, ACC_PUBLIC, "de.espirit.firstspirit.access.project/Project",
            null, "java/lang/Object", null)
        connection.visit(
            V11, ACC_PUBLIC, "de.espirit.firstspirit.access/Connection",
            null, "java/lang/Object", null)
        val classesToWrite: MutableMap<String, ClassWriter> = HashMap()
        classesToWrite["de/espirit/firstspirit/access/store/IDProvider.class"] = idProvider
        classesToWrite["de/espirit/firstspirit/access/project/Project.class"] = project
        classesToWrite["de/espirit/firstspirit/access/Connection.class"] = connection

        writeClassesToFsmFile(classesToWrite)

        assertThatThrownBy { isolationCheck.check() }.hasMessageContaining("FirstSpirit artifacts detected")
    }

    private fun asQualified(classAsFS: String): String {
        return classAsFS.replace("/", ".")
    }

    private fun writeSingleClassToFsmFile(superClassName: String) {
        val classWriter = ClassWriter(0)
        val className = "com.fasterxml.jackson.annotation/JacksonAnnotation"
        classWriter.visit(V1_8, ACC_PUBLIC, className, null, superClassName, null)
        val classContent = classWriter.toByteArray()

        val jarFile = jarDir.resolve(IsolationCheckTestIT::class.simpleName + ".jar")

        JarOutputStream(jarFile.outputStream()).use {
            val jarEntry = JarEntry("JacksonAnnotation.class")
            it.putNextEntry(jarEntry)
            it.write(classContent)
        }

        writeFSM(jarFile)
    }

    private fun writeClassesToFsmFile(classesToWrite: Map<String, ClassWriter>) {
        val jarFile = jarDir.resolve(IsolationCheckTestIT::class.simpleName + ".jar")

        JarOutputStream(jarFile.outputStream()).use {
            classesToWrite.forEach { (name, bytes) ->
                val jarEntry = JarEntry(name)
                it.putNextEntry(jarEntry)
                it.write(bytes.toByteArray())
            }
        }

        writeFSM(jarFile)
    }


    private fun writeFSM(jarFile: Path) {
        val fsmFile = fsm.outputs.files.singleFile
        fsmFile.toPath().parent.createDirectories()

        ZipOutputStream(fsmFile.outputStream()).use {
            it.putNextEntry(ZipEntry("META-INF/module-isolated.xml"))
            it.write("<module><name>TestModule</name><version>0.1</version>".toByteArray())
            it.write("<components>".toByteArray())
            it.write("<web-app>".toByteArray())
            it.write("<name>contentCreatorComponent</name>".toByteArray())
            it.write("<web-xml>web.xml</web-xml>".toByteArray())
            it.write("<web-resources>".toByteArray())
            it.write("""<resource name="de.espirit:test" version="1.0">test.jar</resource>""".toByteArray())
            it.write("</web-resources>".toByteArray())
            it.write("</web-app>".toByteArray())
            it.write("</components>".toByteArray())
            it.write("<resources>".toByteArray())
            it.write("""<resource name="de.espirit:test" version="1.0" scope="module" mode="isolated">test.jar</resource>""".toByteArray())
            it.write("</resources></module>".toByteArray())
            it.putNextEntry(ZipEntry("test.jar"))
            it.write(jarFile.readBytes())
            it.putNextEntry(ZipEntry("web.xml"))
            it.write("<web-app/>".toByteArray())
        }
    }

    companion object {
        const val JDK_CLASS = "java/lang/Object"
        const val DEPRECATED_API_CLASS = "de/espirit/firstspirit/access/UrlCreator"
        const val API_CLASS = "de/espirit/firstspirit/access/project/Project"
        const val RUNTIME_CLASS = "de/espirit/common/StringUtil"
        const val IMPL_CLASS = "de/espirit/common/impl/LegacyClassFactoryImpl"
    }

}