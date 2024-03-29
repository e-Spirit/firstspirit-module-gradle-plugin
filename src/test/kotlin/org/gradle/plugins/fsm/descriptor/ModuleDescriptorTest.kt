package org.gradle.plugins.fsm.descriptor

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.gradle.api.Project
import org.gradle.plugins.fsm.FSMPluginExtension
import org.gradle.plugins.fsm.annotations.FSMAnnotationsPlugin
import org.gradle.plugins.fsm.configurations.FSMConfigurationsPlugin
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ModuleDescriptorTest {

    val project: Project = ProjectBuilder.builder().build()
    private lateinit var moduleDescriptor: ModuleDescriptor

    @BeforeEach
    fun setup() {
        project.configure()
        moduleDescriptor = ModuleDescriptor(project)
    }

    @Test
    fun `single module class written to descriptor`() {
        val classTag = moduleDescriptor.node.filter("class").single()
        assertThat(classTag.textContent()).isEqualTo("org.gradle.plugins.fsm.TestModuleImplWithConfiguration")
    }

    @Test
    fun `module class with configurable`() {
        val configurable = moduleDescriptor.node.filter("configurable").single()
        assertThat(configurable.textContent()).isEqualTo("org.gradle.plugins.fsm.TestConfigurable")
    }

    @Test
    fun `module name should be equal to project name if not set`() {
        val projectName = "MyProjectName"
        val project = ProjectBuilder.builder().withName(projectName).build()
        project.configure()

        moduleDescriptor = ModuleDescriptor(project)

        val nameNode = moduleDescriptor.node.filter("name").single()
        assertThat(nameNode.textContent()).isEqualTo(projectName)
    }

    @Test
    fun `module name should be equal to set module name`() {
        val projectName = "MyProjectName"
        val moduleName = "MyModule"

        val project = ProjectBuilder.builder().withName(projectName).build()
        project.configure()
        val extension = project.extensions.getByType(FSMPluginExtension::class.java)
        extension.moduleName = moduleName

        moduleDescriptor = ModuleDescriptor(project)

        val nameNode = moduleDescriptor.node.filter("name").single()
        assertThat(nameNode.textContent()).isEqualTo(moduleName)
    }

    @Test
    fun `module version should be equal to project version`() {
        val versionTag = moduleDescriptor.node.filter("version").single()
        assertThat(versionTag.textContent()).isEqualTo(project.version)
    }

    @Test
    fun `no min-fs-version should not be added by default`() {
        val minFsVersionTags = moduleDescriptor.node.filter("min-fs-version")
        assertThat(minFsVersionTags).isEmpty()
    }

    @Test
    fun `no min-fs-version should not be added if empty`() {
        project.extensions.getByType(FSMPluginExtension::class.java).minimalFirstSpiritVersion = ""
        moduleDescriptor = ModuleDescriptor(project)

        val minFsVersionTags = moduleDescriptor.node.filter("min-fs-version")
        assertThat(minFsVersionTags).isEmpty()
    }

    @Test
    fun `min-fs-version should be added if defined`() {
        project.extensions.getByType(FSMPluginExtension::class.java).minimalFirstSpiritVersion = "5.2.230909"
        moduleDescriptor = ModuleDescriptor(project)

        val minFsVersionTag = moduleDescriptor.node.filter("min-fs-version").single()
        assertThat(minFsVersionTag.textContent()).isEqualTo("5.2.230909")
    }

    @Test
    fun `module description should be equal to project description`() {
        project.description = "Test project"
        moduleDescriptor = ModuleDescriptor(project)
        val descriptionTag = moduleDescriptor.node.filter("description").single()
        assertThat(descriptionTag.textContent()).isEqualTo(project.description)
    }

    @Test
    fun `project name should be used as fallback for description`() {
        val descriptionTag = moduleDescriptor.node.filter("description").single()
        assertThat(descriptionTag.textContent()).isEqualTo(project.name)
    }

    @Test
    fun `module dependencies`() {
        project.extensions.getByType(FSMPluginExtension::class.java).fsmDependencies = listOf("oneFSM", "anotherFSM")
        moduleDescriptor = ModuleDescriptor(project)
        val dependenciesNode = moduleDescriptor.node.filter("dependencies").single()
        val dependencies = dependenciesNode.filter("depends")
        assertThat(dependencies).hasSize(2)
        assertThat(dependencies.map { it.textContent() }.toList()).containsExactlyInAnyOrder("oneFSM", "anotherFSM")
    }

    @Test
    fun `module tag with two implementation classes`() {
        project.addClassToTestJar("org/gradle/plugins/fsm/TestModuleImpl.class")
        assertThatExceptionOfType(IllegalStateException::class.java)
            .isThrownBy { ModuleDescriptor(project) }
            .withMessageStartingWith("The following classes implementing de.espirit.firstspirit.module.Module were found in your project:")
    }

    @Test
    fun `valid string representation of module dependencies`() {
        project.extensions.getByType(FSMPluginExtension::class.java).fsmDependencies = listOf("oneFSM", "anotherFSM")
        moduleDescriptor = ModuleDescriptor(project)

        assertThat(moduleDescriptor.fsmDependencies()).isEqualTo("""
            |<depends>oneFSM</depends>
            |<depends>anotherFSM</depends>
            """.trimMargin())
    }

    private fun Project.configure() {
        plugins.apply("java-library")
        plugins.apply(FSMAnnotationsPlugin::class.java)
        plugins.apply(FSMConfigurationsPlugin::class.java)
        extensions.create("fsmPlugin", FSMPluginExtension::class.java)
        project.setArtifactoryCredentialsFromLocalProperties()
        project.defineArtifactoryForProject()
        copyTestJar()
    }

}