package org.gradle.plugins.fsm.descriptor

import org.assertj.core.api.Assertions.assertThat
import org.gradle.api.Project
import org.gradle.api.plugins.ExtraPropertiesExtension
import org.gradle.plugins.fsm.FSMPluginExtension
import org.gradle.plugins.fsm.configurations.FSMConfigurationsPlugin
import org.gradle.plugins.fsm.configurations.FSMConfigurationsPlugin.Companion.FS_MODULE_COMPILE_CONFIGURATION_NAME
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ProjectAppComponentsTest {

    val project: Project = ProjectBuilder.builder().build()

    @BeforeEach
    fun setup() {
        project.plugins.apply("java-library")
        project.plugins.apply(FSMConfigurationsPlugin::class.java)
        project.extensions.create("fsmPlugin", FSMPluginExtension::class.java)
        project.repositories.add(project.repositories.mavenCentral())
        project.copyTestJar()


    }

    @Test
    fun `project app should contain basic information`() {
        val moduleDescriptor = ModuleDescriptor(project)
        val components = moduleDescriptor.components.node
        val component = components.filter{ it.childText("name") == "TestProjectAppComponentName"}.single()
        assertThat(component.childText("displayname")).isEqualTo("TestDisplayName")
        assertThat(component.childText("description")).isEqualTo("TestDescription")
        assertThat(component.childText("class")).isEqualTo("org.gradle.plugins.fsm.TestProjectAppComponent")
    }

    @Test
    fun `project app should have no configurable tag if no config class was set`() {
        val moduleDescriptor = ModuleDescriptor(project)
        val components = moduleDescriptor.components.node
        val component = components.filter{ it.childText("name") == "TestProjectAppComponentWithoutConfigurableName"}.single()
        assertThat(component.childText("class")).endsWith(".TestProjectAppComponentWithoutConfigurable")
        assertThat(component.filter("configurable")).isEmpty()
    }

    @Test
    fun `project app should not contain an empty resources tag when there are no resources`() {
        val moduleDescriptor = ModuleDescriptor(project)
        val components = moduleDescriptor.components.node
        val component = components.filter{ it.childText("name") == "TestProjectAppComponentWithoutConfigurableName"}.single()
        assertThat(component.childText("class")).endsWith(".TestProjectAppComponentWithoutConfigurable")
        assertThat(component.filter("resources")).isEmpty()
    }

    @Test
    fun `project app with configurable`() {
        val moduleDescriptor = ModuleDescriptor(project)
        val components = moduleDescriptor.components.node
        val component = components.filter{ it.childText("name") == "TestProjectAppComponentName"}.single()
        assertThat(component.childText("class")).isEqualTo("org.gradle.plugins.fsm.TestProjectAppComponent")
        assertThat(component.childText("configurable"))
            .isEqualTo("org.gradle.plugins.fsm.TestProjectAppComponent\$TestConfigurable")
    }

    @Test
    fun `project app with resource`() {
        val moduleDescriptor = ModuleDescriptor(project)
        val components = moduleDescriptor.components.node
        val component = components.filter{ it.childText("name") == "TestProjectAppComponentName"}.single()
        assertThat(component.childText("class")).isEqualTo("org.gradle.plugins.fsm.TestProjectAppComponent")
        val resources = component.filter("resources").single()
        val resource = resources.filter("resource").single()
        assertThat(resource.attributes["name"]).isEqualTo("com.google.guava:guava")
        assertThat(resource.attributes["version"]).isEqualTo("24.0")
        assertThat(resource.attributes["scope"]).isEqualTo("module")
        assertThat(resource.attributes["mode"]).isEqualTo("isolated")
        assertThat(resource.textContent()).isEqualTo("lib/guava-24.0.jar")
    }

    @Test
    fun `resource property interpolation in project app resources`() {
        project.addClassToTestJar("org/gradle/plugins/fsm/TestProjectAppComponentWithProperties.class")
        project.extensions.getByType(ExtraPropertiesExtension::class.java).set("jodaConvertDependency", "org.joda:joda-convert")
        project.dependencies.add(FS_MODULE_COMPILE_CONFIGURATION_NAME, "org.joda:joda-convert:2.1.1")

        val moduleDescriptor = ModuleDescriptor(project)
        val components = moduleDescriptor.components.node

        val component = components.filter{ it.childText("name") == "TestProjectAppComponentWithProperties"}.single()
        val resources = component.filter("resources").single()
        val resource = resources.filter("resource").single()
        assertThat(resource.attributes["name"]).isEqualTo("org.joda:joda-convert")
        assertThat(resource.attributes["version"]).isEqualTo("2.1.1")
    }


}