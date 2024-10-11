package org.gradle.plugins.fsm.descriptor

import org.assertj.core.api.Assertions.assertThat
import org.gradle.api.Project
import org.gradle.plugins.fsm.FSMPluginExtension
import org.gradle.plugins.fsm.annotations.FSMAnnotationsPlugin
import org.gradle.plugins.fsm.configurations.FSMConfigurationsPlugin
import org.gradle.plugins.fsm.configurations.FSMConfigurationsPlugin.Companion.FS_MODULE_COMPILE_CONFIGURATION_NAME
import org.gradle.plugins.fsm.configurations.FSMConfigurationsPlugin.Companion.FS_SERVER_COMPILE_CONFIGURATION_NAME
import org.gradle.plugins.fsm.configurations.fsDependency
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.redundent.kotlin.xml.Node

class LibraryComponentsTest {

    val project: Project = ProjectBuilder.builder().build()

    private lateinit var extension: FSMPluginExtension

    @BeforeEach
    fun setup() {
        project.plugins.apply("java-library")
        project.plugins.apply(FSMAnnotationsPlugin::class.java)
        project.plugins.apply(FSMConfigurationsPlugin::class.java)
        extension = project.extensions.create("fsmPlugin", FSMPluginExtension::class.java)
        project.setArtifactoryCredentialsFromLocalProperties()
        project.defineArtifactoryForProject()
        project.copyTestJar()
    }

    @Test
    fun `basic attributes`() {
        val myLib = extension.libraries.create("myLib")
        myLib.displayName = "myDisplayName"
        myLib.description = "myDesc"
        myLib.hidden = true
        myLib.configurable = "com.crownpeak.fsm.demo.Config"

        val moduleDescriptor = ModuleDescriptor(project)
        val components = moduleDescriptor.components.node
        val component = components.filter{ it.childText("name") == "myLib" }.single()
        assertThat(component.childText("displayname")).isEqualTo("myDisplayName")
        assertThat(component.childText("description")).isEqualTo("myDesc")
        assertThat(component.childText("hidden")).isEqualTo("true")
        assertThat(component.childText("configurable")).isEqualTo("com.crownpeak.fsm.demo.Config")
    }

    @Test
    fun `optional values not rendered if unset`() {
        extension.libraries.create("myLib")

        val moduleDescriptor = ModuleDescriptor(project)
        val components = moduleDescriptor.components.node
        val component = components.filter{ it.childText("name") == "myLib" }.single()
        assertThat(component.filter("displayname")).isEmpty()
        assertThat(component.filter("description")).isEmpty()
        assertThat(component.filter("hidden")).isEmpty()
        assertThat(component.filter("configurable")).isEmpty()
    }

    @Test
    fun `configuration resolution`() {
        project.dependencies.add(FS_SERVER_COMPILE_CONFIGURATION_NAME, "org.slf4j:slf4j-api:2.0.6")

        extension.libraries.create("myLib").configuration = project.configurations.getByName(FS_SERVER_COMPILE_CONFIGURATION_NAME)

        val moduleDescriptor = ModuleDescriptor(project)

        val resource = singleResource(moduleDescriptor)
        assertThat(resource.attributes["name"]).isEqualTo("org.slf4j:slf4j-api")
        assertThat(resource.attributes["version"]).isEqualTo("2.0.6")
        assertThat(resource.attributes["scope"]).isEqualTo("server")
        assertThat(resource.attributes["mode"]).isEqualTo("isolated")
        assertThat(resource.textContent()).isEqualTo("lib/slf4j-api-2.0.6.jar")
    }

    @Test
    fun `custom configuration resolution`() {
        val customConfiguration = project.configurations.create("customConfiguration")

        project.dependencies.add("customConfiguration", "org.slf4j:slf4j-api:2.0.6")

        extension.libraries.create("myLib").configuration = customConfiguration

        val moduleDescriptor = ModuleDescriptor(project)

        val resource = singleResource(moduleDescriptor)
        assertThat(resource.attributes["name"]).isEqualTo("org.slf4j:slf4j-api")
        assertThat(resource.attributes["version"]).isEqualTo("2.0.6")
        assertThat(resource.attributes["scope"]).isEqualTo("server")
        assertThat(resource.attributes["mode"]).isEqualTo("isolated")
        assertThat(resource.textContent()).isEqualTo("lib/slf4j-api-2.0.6.jar")
    }

    @Test
    fun `empty resources block when configuration is undefined`() {
        extension.libraries.create("myLib")

        val moduleDescriptor = ModuleDescriptor(project)
        val components = moduleDescriptor.components.node
        val component = components.filter { it.childText("name") == "myLib" }.single()
        val resources = component.filter("resources").single()

        assertThat(resources.children).isEmpty()
    }

    @Test
    fun `empty resources block when no dependencies are defined`() {
        extension.libraries.create("myLib").configuration = project.configurations.create("emptyConfiguration")

        val moduleDescriptor = ModuleDescriptor(project)
        val components = moduleDescriptor.components.node
        val component = components.filter { it.childText("name") == "myLib" }.single()
        val resources = component.filter("resources").single()

        assertThat(resources.children).isEmpty()
    }

    @Test
    fun `specify min- and max-version`() {
        val customConfiguration = project.configurations.create("customConfiguration")

        project.dependencies.add("customConfiguration",
            project.fsDependency("org.slf4j:slf4j-api:2.0.6", "2.0.0", "2.0.99"))

        extension.libraries.create("myLib").configuration = customConfiguration

        val moduleDescriptor = ModuleDescriptor(project)

        val resource = singleResource(moduleDescriptor)
        assertThat(resource.attributes["name"]).isEqualTo("org.slf4j:slf4j-api")
        assertThat(resource.attributes["version"]).isEqualTo("2.0.6")
        assertThat(resource.attributes["minVersion"]).isEqualTo("2.0.0")
        assertThat(resource.attributes["maxVersion"]).isEqualTo("2.0.99")
        assertThat(resource.textContent()).isEqualTo("lib/slf4j-api-2.0.6.jar")
    }

    @Test
    fun `dependency with classifier`() {
        val customConfiguration = project.configurations.create("customConfiguration")
        val dependency = project.dependencies.create("de.espirit.firstspirit:fs-api:5.2.221111:javadoc")
        customConfiguration.dependencies.add(dependency)

        extension.libraries.create("myLib").configuration = customConfiguration

        val moduleDescriptor = ModuleDescriptor(project)

        val resource = singleResource(moduleDescriptor)
        assertThat(resource.attributes["name"]).isEqualTo("de.espirit.firstspirit:fs-api:javadoc")
        assertThat(resource.attributes["version"]).isEqualTo("5.2.221111")
        assertThat(resource.textContent()).isEqualTo("lib/fs-api-5.2.221111-javadoc.jar")
    }

    @Test
    fun `use same version as existing dependency on runtime classpath`() {
        val customConfiguration = project.configurations.create("customConfiguration")
        val dependency = project.dependencies.create("org.slf4j:slf4j-api:2.0.6")
        customConfiguration.dependencies.add(dependency)

        val fsModuleConfigration = project.configurations.getByName(FS_MODULE_COMPILE_CONFIGURATION_NAME)
        val runtimeDependency = project.dependencies.create("org.slf4j:slf4j-api:2.0.0")
        fsModuleConfigration.dependencies.add(runtimeDependency)

        extension.libraries.create("myLib").configuration = customConfiguration

        val moduleDescriptor = ModuleDescriptor(project)

        val resource = singleResource(moduleDescriptor)
        assertThat(resource.attributes["version"]).isEqualTo("2.0.0")
    }

    private fun singleResource(moduleDescriptor: ModuleDescriptor): Node {
        val components = moduleDescriptor.components.node
        val component = components.filter { it.childText("name") == "myLib" }.single()
        val resources = component.filter("resources").single()
        return resources.filter("resource").single()
    }

}