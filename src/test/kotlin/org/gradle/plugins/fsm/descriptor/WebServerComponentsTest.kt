package org.gradle.plugins.fsm.descriptor

import org.assertj.core.api.Assertions.assertThat
import org.gradle.api.Project
import org.gradle.plugins.fsm.FSMPluginExtension
import org.gradle.plugins.fsm.annotations.FSMAnnotationsPlugin
import org.gradle.plugins.fsm.configurations.FSMConfigurationsPlugin
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class WebServerComponentsTest {

    val project: Project = ProjectBuilder.builder().build()
    private lateinit var moduleDescriptor: ModuleDescriptor

    @BeforeEach
    fun setup() {
        project.plugins.apply("java-library")
        project.plugins.apply(FSMAnnotationsPlugin::class.java)
        val configurationsPlugin = project.plugins.apply(FSMConfigurationsPlugin::class.java)
        val extension = project.extensions.create("fsmPlugin", FSMPluginExtension::class.java)
        project.setArtifactoryCredentialsFromLocalProperties()
        project.defineArtifactoryForProject()
        project.copyTestJar()

        moduleDescriptor = ModuleDescriptor(
            fsmGradlePluginContext = FSMGradlePluginContext(project)
        )
    }

    @Test
    fun `minimal web server`() {
        val components = moduleDescriptor.components.node
        val component = components.filter { it.childText("name") == "TestMinimalWebServerComponent" }.single()
        assertThat(component.childText("displayname")).isEmpty()
        assertThat(component.childText("description")).isEmpty()
        assertThat(component.childText("class")).isEqualTo("org.gradle.plugins.fsm.TestMinimalWebServerComponent")
    }


    @Test
    fun `web server should contain basic information`() {
        val components = moduleDescriptor.components.node
        val component = components.filter { it.childText("name") == "TestWebServerComponentWithParameters" }.single()
        assertThat(component.childText("displayname")).isEqualTo("TestDisplayName")
        assertThat(component.childText("description")).isEqualTo("TestDescription")
        assertThat(component.childText("class")).isEqualTo("org.gradle.plugins.fsm.TestWebServerComponentWithParameters")
    }

    @Test
    fun `web server should have no configurable tag if no config class was set`() {
        val components = moduleDescriptor.components.node
        val component = components.filter { it.childText("name") == "TestMinimalWebServerComponent" }.single()
        assertThat(component.childText("class")).endsWith(".TestMinimalWebServerComponent")
        assertThat(component.filter("configurable")).isEmpty()
    }

    @Test
    fun `web server should not be hidden by default`() {
        val components = moduleDescriptor.components.node
        val component = components.filter { it.childText("name") == "TestWebServerComponentWithParameters" }.single()
        assertThat(component.filter("hidden")).isEmpty()
    }

    @Test
    fun `web server with configurable`() {
        val components = moduleDescriptor.components.node
        val component = components.filter { it.childText("name") == "TestWebServerComponentWithParameters" }.single()
        assertThat(component.childText("class")).isEqualTo("org.gradle.plugins.fsm.TestWebServerComponentWithParameters")
        assertThat(component.childText("configurable"))
                .isEqualTo("org.gradle.plugins.fsm.TestWebServerComponentWithParameters\$TestConfigurable")
    }

    @Test
    fun `hidden component`() {
        val components = moduleDescriptor.components.node
        val component = components.filter { it.childText("name") == "TestHiddenWebServerComponent" }.single()
        val hidden = component.filter("hidden").single()
        assertThat(hidden.textContent().toBoolean()).isTrue()
    }

}