package org.gradle.plugins.fsm.isolationcheck

import de.espirit.mavenplugins.fsmchecker.ComplianceLevel
import org.assertj.core.api.Assertions.assertThat
import org.gradle.plugins.fsm.isolationcheck.VerificationResult.Status.*
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import java.io.FileOutputStream
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class WebServiceConnectorIT {

    @TempDir
    private lateinit var tempDir: Path

    private lateinit var webserviceUri: URI
    private var webserviceUsername: String? = null
    private var webservicePassword: String? = null


    @BeforeEach
    fun setup() {
        val propFile = WebServiceConnectorIT::class.java.classLoader.getResourceAsStream("test.properties")

        assumeTrue(propFile != null, "Please copy and edit test.properties.example" +
                " to test.properties in order to enable this test")

        val testProperties = Properties()
        propFile.use { testProperties.load(it) }

        webserviceUri = URI(testProperties.getProperty("url"))
        webserviceUsername = testProperties.getProperty("username")
        webservicePassword = testProperties.getProperty("password")
    }


    @Test
    fun `connection-error`() {
        val uri = URI("http://unknown.invalid")
        val webserviceConnector =
            WebServiceConnector(uri, null, MAX_BYTECODE_VERSION, webserviceUsername, webservicePassword)
        val useCase = ComplianceCheck(ComplianceLevel.HIGHEST, tempDir, webserviceConnector)
        val classWriter = ClassWriter(0)
        classWriter.visit(
            Opcodes.V11,
            Opcodes.ACC_PUBLIC,
            "com.example.module/TestWithoutDependencies",
            null,
            JDK_CLASS,
            null
        )
        val file = writeSingleClassToFsmFile(classWriter, "TestWithoutDependencies.class")
        val result: VerificationResult = useCase.check(listOf(file))
        assertThat(result.status).isSameAs(CONNECTION_FAILED)
        assertThat(result.message).isEqualTo("Upload failed because of unknown host: 'unknown.invalid: Name or service not known'")
    }

    @Test
    fun `single file without dependencies`() {
        val webserviceConnector =
            WebServiceConnector(webserviceUri, null, MAX_BYTECODE_VERSION, webserviceUsername, webservicePassword)
        val useCase = ComplianceCheck(
            ComplianceLevel.HIGHEST,
            tempDir, webserviceConnector
        )
        val classWriter = ClassWriter(0)
        classWriter.visit(
            Opcodes.V1_8,
            Opcodes.ACC_PUBLIC,
            "com.example.module/TestWithoutDependencies",
            null,
            JDK_CLASS,
            null
        )
        val file = writeSingleClassToFsmFile(classWriter, "TestWithoutDependencies.class")
        val result: VerificationResult = useCase.check(listOf(file))
        assertThat(result.status).isSameAs(VALID)
        assertThat(result.message).startsWith("Isolation check passed! ComplianceLevel: 'HIGHEST'")
    }

    @Test
    fun `single file with specific version`() {
        val webserviceConnector = WebServiceConnector(
            webserviceUri,
            "5.2.220309",
            MAX_BYTECODE_VERSION,
            webserviceUsername,
            webservicePassword
        )
        val useCase = ComplianceCheck(
            ComplianceLevel.HIGHEST,
            tempDir, webserviceConnector
        )
        val classWriter = ClassWriter(0)
        classWriter.visit(
            Opcodes.V1_8,
            Opcodes.ACC_PUBLIC,
            "com.example.module/TestWithoutDependencies",
            null,
            JDK_CLASS,
            null
        )
        val file = writeSingleClassToFsmFile(classWriter, "TestWithoutDependencies.class")
        val result: VerificationResult = useCase.check(listOf(file))
        assertThat(result.status).isSameAs(VALID)
        assertThat(result.message).startsWith("Isolation check passed! ComplianceLevel: 'HIGHEST'")
    }

    @Test
    fun `single file with invalid version`() {
        val webserviceConnector = WebServiceConnector(
            webserviceUri,
            "invalid",
            MAX_BYTECODE_VERSION,
            webserviceUsername,
            webservicePassword
        )
        val useCase = ComplianceCheck(
            ComplianceLevel.HIGHEST,
            tempDir, webserviceConnector
        )
        val classWriter = ClassWriter(0)
        classWriter.visit(
            Opcodes.V1_8,
            Opcodes.ACC_PUBLIC,
            "com.example.module/TestWithoutDependencies",
            null,
            JDK_CLASS,
            null
        )
        val file = writeSingleClassToFsmFile(classWriter, "TestWithoutDependencies.class")
        val result: VerificationResult = useCase.check(listOf(file))
        assertThat(result.status).isSameAs(CONNECTION_FAILED)
        assertThat(result.message).isEqualTo("Analyze failed with status '404'")
    }

    @Test
    fun `single file with dependencies`() {
        val webserviceConnector =
            WebServiceConnector(webserviceUri, null, MAX_BYTECODE_VERSION, webserviceUsername, webservicePassword)
        val useCase = ComplianceCheck(
            ComplianceLevel.HIGHEST,
            tempDir, webserviceConnector
        )
        val classWriter = ClassWriter(0)
        classWriter.visit(
            Opcodes.V1_8,
            Opcodes.ACC_PUBLIC,
            "com.example.module/TestWithDependencies",
            null,
            JDK_CLASS,
            null
        )
        classWriter.visitField(Opcodes.ACC_PRIVATE, "Logger", "Lde/espirit/common/Logging;", null, null)
        val file = writeSingleClassToFsmFile(classWriter, "TestWithDependencies.class")
        val result: VerificationResult = useCase.check(listOf(file))
        assertThat(result.status).isSameAs(INVALID)
        assertThat(result.message).contains("IMPL_USAGE")
    }

    @Test
    fun deprecation() {
        val webserviceConnector =
            WebServiceConnector(webserviceUri, null, MAX_BYTECODE_VERSION, webserviceUsername, webservicePassword)
        val useCase = ComplianceCheck(
            ComplianceLevel.HIGHEST,
            tempDir, webserviceConnector
        )
        val classWriter = ClassWriter(0)
        classWriter.visit(
            Opcodes.V1_8,
            Opcodes.ACC_PUBLIC,
            "com.example.module/TestWithDependencies",
            null,
            JDK_CLASS,
            null
        )
        classWriter.visitField(
            Opcodes.ACC_PRIVATE,
            "EditorValue",
            "Lde/espirit/firstspirit/access/editor/EditorValue;",
            null,
            null
        )
        val file = writeSingleClassToFsmFile(classWriter, "TestWithDependencies.class")
        val result: VerificationResult = useCase.check(listOf(file))
        assertThat(result.status).isSameAs(INVALID)
        assertThat(result.message).contains("DEPRECATED_API_USAGE")
    }

    @Test
    fun `ignore deprecation when checking runtime-level`() {
        val webserviceConnector =
            WebServiceConnector(webserviceUri, null, MAX_BYTECODE_VERSION, webserviceUsername, webservicePassword)
        val useCase = ComplianceCheck(
            ComplianceLevel.DEFAULT,
            tempDir, webserviceConnector
        )
        val classWriter = ClassWriter(0)
        classWriter.visit(
            Opcodes.V1_8,
            Opcodes.ACC_PUBLIC,
            "com.example.module/TestIgnoreDeprecations",
            null,
            DEPRECATED_API_CLASS,
            null
        )
        val file = writeSingleClassToFsmFile(classWriter, "TestWithDependencies.class")
        val result: VerificationResult = useCase.check(listOf(file))
        assertThat(result.status).isSameAs(VALID)
        assertThat(result.message).isEqualTo("Isolation check passed! ComplianceLevel: 'DEFAULT'")
    }

    @Test
    fun `ignore runtime-usage when checking impl-level`() {
        val webserviceConnector =
            WebServiceConnector(webserviceUri, null, MAX_BYTECODE_VERSION, webserviceUsername, webservicePassword)
        val minimalComplianceUseCase = ComplianceCheck(
            ComplianceLevel.MINIMAL,
            tempDir, webserviceConnector
        )
        val fileList = codeWithRuntimeDependencies()
        val result: VerificationResult = minimalComplianceUseCase.check(fileList)
        assertThat(result.status).isSameAs(VALID)
        assertThat(result.message).isEqualTo("Isolation check passed! ComplianceLevel: 'MINIMAL'")
    }

    @Test
    fun `ignore whitelisted resources`() {
        val webserviceConnector =
            WebServiceConnector(webserviceUri, null, MAX_BYTECODE_VERSION, webserviceUsername, webservicePassword)
        val useCase = ComplianceCheck(
            ComplianceLevel.HIGHEST,
            tempDir, webserviceConnector
        )
        useCase.addWhitelistedResource("de.espirit.modules:test:1.2")
        val fileList = codeWithRuntimeDependencies()
        val result: VerificationResult = useCase.check(fileList)
        assertThat(result.status).isEqualTo(VALID)
        assertThat(result.message).isEqualTo("Isolation check passed! ComplianceLevel: 'HIGHEST'")
    }


    @Test
    fun `check ContentCreator component`() {
        val webserviceConnector =
            WebServiceConnector(webserviceUri, null, MAX_BYTECODE_VERSION, webserviceUsername, webservicePassword)
        val useCase = ComplianceCheck(
            ComplianceLevel.DEFAULT,
            tempDir, webserviceConnector
        )
        useCase.addContentCreatorComponent("contentCreatorComponent")
        val fileList = codeWithRuntimeDependencies()
        val result: VerificationResult = useCase.check(fileList)
        assertThat(result.status).isEqualTo(INVALID)
        assertThat(result.message).contains("com.fasterxml.jackson.annotation.JacksonAnnotation (1 usages)")
    }


    @Test
    fun `impl-class`() {
        val webserviceConnector =
            WebServiceConnector(webserviceUri, null, MAX_BYTECODE_VERSION, webserviceUsername, webservicePassword)
        val minimalComplianceUseCase = ComplianceCheck(
            ComplianceLevel.MINIMAL,
            tempDir, webserviceConnector
        )
        val classWriter = ClassWriter(0)
        classWriter.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "com.example.module/TestImpl", null, IMPL_CLASS, null)
        val file = writeSingleClassToFsmFile(classWriter, "TestWithDependencies.class")
        val result: VerificationResult = minimalComplianceUseCase.check(listOf(file))
        assertThat(result.status).isSameAs(INVALID)
        assertThat(result.message).contains("IMPL_USAGE")
    }

    @Test
    fun `invalid module`() {
        val webserviceConnector =
            WebServiceConnector(webserviceUri, null, MAX_BYTECODE_VERSION, webserviceUsername, webservicePassword)
        val minimalComplianceUseCase = ComplianceCheck(
            ComplianceLevel.MINIMAL,
            tempDir, webserviceConnector
        )
        val fsm = Files.createTempFile(WebServiceConnectorIT::class.java.simpleName, ".fsm")
        val result: VerificationResult = minimalComplianceUseCase.check(listOf(fsm))
        assertThat(result.status).isSameAs(INVALID)
        assertThat(result.message).startsWith("Unable to process module")
    }

    @Test
    fun `no module file to check`() {
        val webserviceConnector =
            WebServiceConnector(webserviceUri, null, MAX_BYTECODE_VERSION, webserviceUsername, webservicePassword)
        val minimalComplianceUseCase = ComplianceCheck(
            ComplianceLevel.MINIMAL,
            tempDir, webserviceConnector
        )
        val result: VerificationResult = minimalComplianceUseCase.check(emptyList())
        assertThat(result.status).isSameAs(VALID)
        assertThat(result.message).isEqualTo("No FirstSpirit module files (.fsm) were configured. -> skipping check.")
    }


    /**
     * Although ComplianceLevel is HIGHEST, and the build fails due to a violation for IsolationLevel.RUNTIME_USAGE, there should be a message saying
     * that IsolationLevel.IMPL_USAGE was fine (output should contain 'IMPL_USAGE: 0 violations').
     */
    @Test
    fun `write zero violations on lower isolation-level when failing on higher isolationLevel`() {
        val webserviceConnector =
            WebServiceConnector(webserviceUri, null, MAX_BYTECODE_VERSION, webserviceUsername, webservicePassword)
        val highestComplianceUseCase = ComplianceCheck(
            ComplianceLevel.HIGHEST,
            tempDir, webserviceConnector
        )
        val code = codeWithRuntimeDependencies()
        val result: VerificationResult = highestComplianceUseCase.check(code)
        assertThat(result.status).isSameAs(INVALID)
        assertThat(result.message)
            .contains("IMPL_USAGE (Dependency on class not available in isolated mode):" + System.lineSeparator() + "  0 violations!")
    }


    /**
     * Although ComplianceLevel is [ComplianceLevel.DEFAULT], and the build fails due to a violation for IsolationLevel.RUNTIME_USAGE, the
     * plugin should write that compliance on higher Level was fine (output should contain 'DEPRECATED_API_USAGE: 0 violations').
     */
    @Test
    fun `write zero violations on higher isolation-level when failing on lower isolation-level`() {
        val webserviceConnector =
            WebServiceConnector(webserviceUri, null, MAX_BYTECODE_VERSION, webserviceUsername, webservicePassword)
        val defaultComplianceUseCase = ComplianceCheck(
            ComplianceLevel.DEFAULT,
            tempDir, webserviceConnector
        )
        val code = codeWithRuntimeDependencies()
        val result: VerificationResult = defaultComplianceUseCase.check(code)
        assertThat(result.status).isSameAs(INVALID)
        assertThat(result.message)
            .contains("DEPRECATED_API_USAGE (Usage of API marked with @Deprecated):" + System.lineSeparator() + "  0 violations!")
    }


    @Test
    fun `valid bytecode level`() {
        val webserviceConnector =
            WebServiceConnector(webserviceUri, null, MAX_BYTECODE_VERSION, webserviceUsername, webservicePassword)
        val useCase = ComplianceCheck(
            ComplianceLevel.HIGHEST,
            tempDir, webserviceConnector
        )
        val classWriter = ClassWriter(0)
        classWriter.visit(
            Opcodes.V1_8,
            Opcodes.ACC_PUBLIC,
            "com.example.module/TestBytecodeLevel",
            null,
            JDK_CLASS,
            null
        )
        val file = writeSingleClassToFsmFile(classWriter, "TestBytecodeLevel.class")
        val result: VerificationResult = useCase.check(listOf(file))
        assertThat(result.status).isSameAs(VALID)
    }


    @Test
    fun `small bytecode level`() {
        val webserviceConnector =
            WebServiceConnector(webserviceUri, null, MAX_BYTECODE_VERSION, webserviceUsername, webservicePassword)
        val useCase = ComplianceCheck(
            ComplianceLevel.HIGHEST,
            tempDir, webserviceConnector
        )
        val classWriter = ClassWriter(0)
        classWriter.visit(
            Opcodes.V1_1,
            Opcodes.ACC_PUBLIC,
            "com.example.module/TestBytecodeLevel",
            null,
            JDK_CLASS,
            null
        )
        val file = writeSingleClassToFsmFile(classWriter, "TestBytecodeLevel.class")
        val result: VerificationResult = useCase.check(listOf(file))
        assertThat(result.status).isSameAs(VALID)
    }


    @Test
    fun `invalid bytecode level Java 8`() {
        val webserviceConnector =
            WebServiceConnector(webserviceUri, null, 52, webserviceUsername, webservicePassword)
        val useCase = ComplianceCheck(
            ComplianceLevel.HIGHEST,
            tempDir, webserviceConnector
        )
        val classWriter = ClassWriter(0)
        classWriter.visit(
            Opcodes.V11,
            Opcodes.ACC_PUBLIC,
            "com.example.module/TestBytecodeLevel",
            null,
            JDK_CLASS,
            null
        )
        val file = writeSingleClassToFsmFile(classWriter, "TestBytecodeLevel.class")
        val result: VerificationResult = useCase.check(listOf(file))
        assertThat(result.status).isSameAs(INVALID)
        assertThat(result.message).startsWith("Jars with invalid bytecode level detected:")
    }


    @Test
    fun `invalid bytecode level Java 11`() {
        val webserviceConnector =
            WebServiceConnector(webserviceUri, null, MAX_BYTECODE_VERSION, webserviceUsername, webservicePassword)
        val useCase = ComplianceCheck(
            ComplianceLevel.HIGHEST,
            tempDir, webserviceConnector
        )
        val classWriter = ClassWriter(0)
        classWriter.visit(
            Opcodes.V17,
            Opcodes.ACC_PUBLIC,
            "com.example.module/TestBytecodeLevel",
            null,
            JDK_CLASS,
            null
        )
        val file = writeSingleClassToFsmFile(classWriter, "TestBytecodeLevel.class")
        val result: VerificationResult = useCase.check(listOf(file))
        assertThat(result.status).isSameAs(INVALID)
        assertThat(result.message).startsWith("Jars with invalid bytecode level detected:")
    }


    @Test
    fun `module-info with Java 8`() {
        val webserviceConnector =
            WebServiceConnector(webserviceUri, null, 52, webserviceUsername, webservicePassword)
        val useCase = ComplianceCheck(
            ComplianceLevel.HIGHEST,
            tempDir, webserviceConnector
        )
        val classWriter = ClassWriter(0)
        classWriter.visit(Opcodes.V9, Opcodes.ACC_PUBLIC, "module-info", null, null, null)
        val file = writeSingleClassToFsmFile(classWriter, "META-INF/module-info.class")
        val result: VerificationResult = useCase.check(listOf(file))
        assertThat(result.status).isSameAs(VALID)
    }


    @Test
    fun `multi-release Jar`() {
        val webserviceConnector =
            WebServiceConnector(webserviceUri, null, 52, webserviceUsername, webservicePassword)
        val useCase = ComplianceCheck(
            ComplianceLevel.HIGHEST,
            tempDir, webserviceConnector
        )
        val classWriter = ClassWriter(0)
        classWriter.visit(
            Opcodes.V11,
            Opcodes.ACC_PUBLIC,
            "META-INF.versions.11/MultiReleaseTest",
            null,
            JDK_CLASS,
            null
        )
        val file = writeSingleClassToFsmFile(classWriter, "META-INF/versions/11/MultiReleaseTest.class")
        val result: VerificationResult = useCase.check(listOf(file))
        assertThat(result.status).isSameAs(VALID)
    }


    @Test
    fun `detect FirstSpirit artifacts`() {
        val idProvider = ClassWriter(0)
        val project = ClassWriter(0)
        val connection = ClassWriter(0)
        idProvider.visit(
            Opcodes.V11,
            Opcodes.ACC_PUBLIC,
            "de.espirit.firstspirit.access.store/IDProvider",
            null,
            "java/lang/Object",
            null
        )
        project.visit(
            Opcodes.V11,
            Opcodes.ACC_PUBLIC,
            "de.espirit.firstspirit.access.project/Project",
            null,
            "java/lang/Object",
            null
        )
        connection.visit(
            Opcodes.V11,
            Opcodes.ACC_PUBLIC,
            "de.espirit.firstspirit.access/Connection",
            null,
            "java/lang/Object",
            null
        )
        val classesToWrite = mapOf(
            "de/espirit/firstspirit/access/store/IDProvider.class" to idProvider,
            "de/espirit/firstspirit/access/project/Project.class" to project,
            "de/espirit/firstspirit/access/Connection.class" to connection
        )
        val fsm = writeClassesToFsmFile(classesToWrite)
        val webserviceConnector =
            WebServiceConnector(webserviceUri, null, 55, webserviceUsername, webservicePassword)
        val useCase = ComplianceCheck(
            ComplianceLevel.HIGHEST,
            tempDir, webserviceConnector
        )
        val result: VerificationResult = useCase.check(listOf(fsm))
        assertThat(result.status).isSameAs(INVALID)
        assertThat(result.message).startsWith("FirstSpirit artifacts detected:")
    }


    private fun codeWithRuntimeDependencies(): List<Path> {
        val classWriter = ClassWriter(0)
        classWriter.visit(
            Opcodes.V1_8,
            Opcodes.ACC_PUBLIC,
            "com.fasterxml.jackson.annotation/JacksonAnnotation",
            null,
            RUNTIME_CLASS,
            null
        )
        val file = writeSingleClassToFsmFile(classWriter, "JacksonAnnotation.class")
        return listOf(file)
    }

    private fun writeSingleClassToFsmFile(classToWrite: ClassWriter, className: String): Path {
        return writeClassesToFsmFile(mapOf(className to classToWrite))
    }

    private fun writeClassesToFsmFile(classesToWrite: Map<String, ClassWriter>): Path {
        val jarFile = tempDir.resolve(WebServiceConnectorIT::class.java.simpleName + ".jar")
        JarOutputStream(FileOutputStream(jarFile.toFile())).use { jarOutputStream ->
            for ((path, classToWrite) in classesToWrite) {
                val jarEntry = JarEntry(path)
                jarOutputStream.putNextEntry(jarEntry)
                jarOutputStream.write(classToWrite.toByteArray())
            }
        }
        val fsmFile = tempDir.resolve(WebServiceConnectorIT::class.java.simpleName + ".fsm")
        ZipOutputStream(FileOutputStream(fsmFile.toFile())).use { zipOutputStream ->
            zipOutputStream.putNextEntry(ZipEntry("META-INF/module.xml"))
            zipOutputStream.write("""
            <module><name>TestModule</name><version>0.1</version>
                <components>
                    <web-app>
                        <name>contentCreatorComponent</name>
                        <web-xml>web.xml</web-xml>
                        <web-resources>
                            <resource name="de.espirit.modules:test" version="1.2" scope="module">test.jar</resource>
                        </web-resources>
                    </web-app>
                </components>
                <resources>
                    <resource name="de.espirit.modules:test" version="1.2" scope="module">test.jar</resource>
                </resources>
            </module>
            """.trimIndent().toByteArray())

            zipOutputStream.putNextEntry(ZipEntry("test.jar"))
            zipOutputStream.write(Files.readAllBytes(jarFile))
            zipOutputStream.putNextEntry(ZipEntry("web.xml"))
            zipOutputStream.write("<web-app/>".toByteArray())
        }
        return fsmFile
    }

    companion object {
        private const val JDK_CLASS = "java/lang/Object"
        private const val DEPRECATED_API_CLASS = "de/espirit/firstspirit/access/UrlCreator"
        private const val RUNTIME_CLASS = "de/espirit/common/StringUtil"
        private const val IMPL_CLASS = "de/espirit/common/impl/LegacyClassFactoryImpl"
        private const val MAX_BYTECODE_VERSION = 55 // Java 11
    }

}