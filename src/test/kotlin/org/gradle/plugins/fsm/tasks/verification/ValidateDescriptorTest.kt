package org.gradle.plugins.fsm.tasks.verification

import com.github.jk1.license.task.ReportTask
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.gradle.api.Project
import org.gradle.plugins.fsm.FSMPlugin
import org.gradle.plugins.fsm.FSMPluginExtension
import org.gradle.plugins.fsm.tasks.bundling.FSM
import org.gradle.plugins.fsm.util.TestProjectUtils
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files

class ValidateDescriptorTest {
    
    lateinit var project: Project

    private lateinit var testDir: File

    private lateinit var fsmTask: FSM
    private lateinit var validateTask: ValidateDescriptor

    @BeforeEach
    fun setup(@TempDir tempDir: File) {
        testDir = tempDir

        project = ProjectBuilder.builder().withProjectDir(testDir).build()
        TestProjectUtils.defineArtifactoryForProject(project)

        project.plugins.apply(FSMPlugin.NAME)

        fsmTask = project.tasks.getByName(FSMPlugin.FSM_TASK_NAME) as FSM
        validateTask = project.tasks.getByName(FSMPlugin.VALIDATE_DESCRIPTOR_TASK_NAME) as ValidateDescriptor
    }
    
    @Test
    fun `basic configuration`() {
        buildFSM()
        validateTask.validateDescriptor()
    }

    @Test
    fun `empty descriptor`() {
        assertThatThrownBy { validate("") }.hasMessage("Module descriptor is empty.")
    }

    @Test
    fun `minimal descriptor`() {
        val descriptor = """
            <module>
                <name>Test</name>
                <version>1.0</version>
            </module>
        """.trimIndent()

        validate(descriptor)
    }

    @Test
    fun `missing name`() {
        val descriptor = """
            <module>
                <version>1.0</version>
            </module>
        """.trimIndent()

        assertThatThrownBy { validate(descriptor) }.hasMessage("<name> is a required element.")
    }

    @Test
    fun `blank name`() {
        val descriptor = """
            <module>
                <name> </name>
            </module>
        """.trimIndent()

        assertThatThrownBy { validate(descriptor) }.hasMessage("<name> must not be empty!")
    }


    @Test
    fun `line break in name`() {

        val descriptor = """
            <module>
                <name>a
                b</name>
                <version>1.0</version>
            </module>
        """.trimIndent()

        assertThatThrownBy { validate(descriptor) }.hasMessage("<name> must not contain a line break.")
    }


    @Test
    fun `invalid character in name`() {

        val descriptor = """
            <module>
                <name>a&lt;b</name>
                <version>1.0</version>
            </module>
        """.trimIndent()

        assertThatThrownBy { validate(descriptor) }.hasMessage("<name> contains illegal character '<'.")
    }


    @Test
    fun `missing version`() {
        val descriptor = """
            <module>
                <name>Test</name>
            </module>
        """.trimIndent()

        assertThatThrownBy { validate(descriptor) }.hasMessage("<version> is a required element.")
    }

    @Test
    fun `missing licenses file`() {
        val descriptor = """
            <module>
                <name>Test</name>
                <version>1.0</version>
                <licenses>META-INF/licenses-invalid.csv</licenses>
            </module>
        """.trimIndent()

        assertThatThrownBy { validate(descriptor) }.hasMessage("License file 'META-INF/licenses-invalid.csv' not found in FSM archive.")
    }

    @Test
    fun `missing resource`() {
        val descriptor = """
            <module>
                <name>Test</name>
                <version>1.0</version>
                
                <resources>
                    <resource name="my.test:invalid-resource">my-lib.jar</resource>
                </resources>
            </module>
        """.trimIndent()

        assertThatThrownBy { validate(descriptor) }
            .hasMessage("File 'my-lib.jar' specified for resource 'my.test:invalid-resource' in global resources" +
                    " but is not found in the FSM.")
    }


    @Test
    fun `invalid version range`() {
        val descriptor = """
            <module>
                <name>Test</name>
                <version>1.0</version>
                
                <resources>
                    <resource name="my.test:invalid-resource" minVersion="2.0" maxVersion="1.0">my-lib.jar</resource>
                </resources>
            </module>
        """.trimIndent()

        assertThatThrownBy { validate(descriptor) }
            .hasMessage("Invalid version range for resource 'my.test:invalid-resource' in global resources:" +
                    " 2.0 is greater than 1.0")
    }


    @Test
    fun `invalid version range for unnamed resource`() {
        val descriptor = """
            <module>
                <name>Test</name>
                <version>1.0</version>
                
                <resources>
                    <resource minVersion="2.0" maxVersion="1.0">my-lib.jar</resource>
                </resources>
            </module>
        """.trimIndent()

        assertThatThrownBy { validate(descriptor) }
            .hasMessage("Invalid version range for resource '<unnamed>' in global resources: 2.0 is greater than 1.0")
    }


    @Test
    fun `version is smaller than minVersion`() {
        val descriptor = """
            <module>
                <name>Test</name>
                <version>1.0</version>
                
                <resources>
                    <resource name="my.test:invalid-resource" version="1.0" minVersion="2.0" maxVersion="3.0">my-lib.jar</resource>
                </resources>
            </module>
        """.trimIndent()

        assertThatThrownBy { validate(descriptor) }
            .hasMessage("Invalid version for resource 'my.test:invalid-resource' in global resources: 1.0 is" +
                    " smaller than minVersion 2.0")
    }


    @Test
    fun `version is greater than maxVersion`() {
        val descriptor = """
            <module>
                <name>Test</name>
                <version>1.0</version>
                
                <resources>
                    <resource name="my.test:invalid-resource" version="4.0" minVersion="2.0" maxVersion="3.0">my-lib.jar</resource>
                </resources>
            </module>
        """.trimIndent()

        assertThatThrownBy { validate(descriptor) }
            .hasMessage("Invalid version for resource 'my.test:invalid-resource' in global resources: 4.0 is" +
                    " greater than maxVersion 3.0")
    }


    @Test
    fun `missing component name`() {
        val descriptor = """
            <module>
                <name>Test</name>
                <version>1.0</version>
                
                <components>
                    <project-app>
                        <name></name>
                    </project-app>
                </components>
            </module>
        """.trimIndent()

        assertThatThrownBy { validate(descriptor) }
            .hasMessage("Component of type 'project-app' with unset or empty name detected.")
    }



    @Test
    fun `missing component resource`() {
        val descriptor = """
            <module>
                <name>Test</name>
                <version>1.0</version>
                
                <components>
                    <project-app>
                        <name>TestProjectApp</name>
                        <resources>
                            <resource>my-lib.jar</resource>
                        </resources>                        
                    </project-app>
                </components>
            </module>
        """.trimIndent()

        assertThatThrownBy { validate(descriptor) }
            .hasMessage("File 'my-lib.jar' specified for resource '<unnamed>' in component of type 'project-app' with" +
                    " name 'TestProjectApp' but is not found in the FSM.")
    }


    @Test
    fun `resource without file`() {
        val descriptor = """
            <module>
                <name>Test</name>
                <version>1.0</version>
                
                <components>
                    <project-app>
                        <name>TestProjectApp</name>
                        <resources>
                            <resource></resource>
                        </resources>                        
                    </project-app>
                </components>
            </module>
        """.trimIndent()

        assertThatThrownBy { validate(descriptor) }
            .hasMessage("No file specified for resource '<unnamed>' in component of type 'project-app' with" +
                    " name 'TestProjectApp'.")
    }


    @Test
    fun `missing web-app resource`() {
        val descriptor = """
            <module>
                <name>Test</name>
                <version>1.0</version>
                
                <components>
                    <web-app>
                        <name>TestWebApp</name>
                        <web-resources>
                            <resource name="my.test:invalid-resource">my-lib.jar</resource>
                        </web-resources>                        
                    </web-app>
                </components>
            </module>
        """.trimIndent()

        assertThatThrownBy { validate(descriptor) }
            .hasMessage("File 'my-lib.jar' specified for resource 'my.test:invalid-resource' in component of type" +
                    " 'web-app' with name 'TestWebApp' but is not found in the FSM.")
    }


    @Test
    fun `web-app resource without file`() {
        val descriptor = """
            <module>
                <name>Test</name>
                <version>1.0</version>
                
                <components>
                    <web-app>
                        <name>TestWebApp</name>
                        <web-resources>
                            <resource name="my.test:invalid-resource"></resource>
                        </web-resources>                        
                    </web-app>
                </components>
            </module>
        """.trimIndent()

        assertThatThrownBy { validate(descriptor) }
            .hasMessage("No file specified for resource 'my.test:invalid-resource' in component of type 'web-app' " +
                    "with name 'TestWebApp'.")
    }


    @Test
    fun `missing web-xml file`() {
        val descriptor = """
            <module>
                <name>Test</name>
                <version>1.0</version>
                
                <components>
                    <web-app>
                        <name>TestWebApp</name>
                        <web-xml>web/web.xml</web-xml>
                    </web-app>
                </components>
            </module>
        """.trimIndent()

        assertThatThrownBy { validate(descriptor) }
            .hasMessage("web.xml file 'web/web.xml' not found for component of type 'web-app' with name" +
                    " 'TestWebApp' in the FSM.")
    }


    @Test
    fun `web-xml file is directory`() {
        val descriptor = """
            <module>
                <name>Test</name>
                <version>1.0</version>
                
                <components>
                    <web-app>
                        <name>TestWebApp</name>
                        <web-xml>web/</web-xml>
                    </web-app>
                </components>
            </module>
        """.trimIndent()

        assertThatThrownBy { validate(descriptor) }
            .hasMessage("web.xml file 'web/' must not be a directory for component of type 'web-app' " +
                    "with name 'TestWebApp'.")
    }


    @Test
    fun `empty web-xml file name`() {
        val descriptor = """
            <module>
                <name>Test</name>
                <version>1.0</version>
                
                <components>
                    <web-app>
                        <name>TestWebApp</name>
                        <web-xml></web-xml>
                    </web-app>
                </components>
            </module>
        """.trimIndent()

        assertThatThrownBy { validate(descriptor) }
            .hasMessage("web.xml path must not be empty in component of type 'web-app' with name 'TestWebApp'.")
    }


    private fun validate(descriptor: String) {
        val moduleDirName = "src/main/resources"
        val moduleDir = testDir.resolve(moduleDirName)

        val extension = project.extensions.getByType(FSMPluginExtension::class.java)
        extension.moduleDirName = moduleDirName
        Files.createDirectories(moduleDir.toPath())
        val moduleXml = moduleDir.resolve("module-isolated.xml")
        moduleXml.writeText(descriptor)

        buildFSM()
        validateTask.validateDescriptor()
    }

    private fun buildFSM() {
        (project.tasks.getByName(FSMPlugin.GENERATE_LICENSE_REPORT_TASK_NAME) as ReportTask).generateReport()
        fsmTask.execute()
    }
    
}