package org.gradle.plugins.fsm.configurations

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class FSMConfigurationsPluginTest {

    private lateinit var project: Project

    @BeforeEach
    fun setUp() {
        project = ProjectBuilder.builder().build()
    }

    @Test
    fun `applies configuration plugin to project`() {
        project.plugins.apply(FSMConfigurationsPlugin.NAME)
        assertThat(project.plugins.hasPlugin(FSMConfigurationsPlugin::class.java)).isTrue
    }

    @Test
    fun `applies java plugin to project`() {
        project.plugins.apply(FSMConfigurationsPlugin.NAME)
        assertThat(project.plugins.hasPlugin(JavaPlugin::class.java)).isTrue
    }

    @Test
    fun `jar implementation configuration extends FirstSpirit scopes`() {
        project.plugins.apply(FSMConfigurationsPlugin.NAME)
        val implementationConfig = project.configurations.getByName(JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME)

        assertThat(implementationConfig.extendsFrom.map { it.name })
            .containsAll(FSMConfigurationsPlugin.FS_CONFIGURATIONS)
        assertThat(implementationConfig.isVisible).isFalse
        assertThat(implementationConfig.isTransitive).isTrue
    }

    @Test
    fun `fsModuleCompile configuration extends fsServerCompile configuration`() {
        project.plugins.apply(FSMConfigurationsPlugin.NAME)
        val fsModuleCompileConfig = project.configurations.getByName(FSMConfigurationsPlugin.FS_MODULE_COMPILE_CONFIGURATION_NAME)

        assertThat(fsModuleCompileConfig.extendsFrom.map { it.name })
            .containsExactly(FSMConfigurationsPlugin.FS_SERVER_COMPILE_CONFIGURATION_NAME)
        assertThat(fsModuleCompileConfig.isVisible).isFalse
        assertThat(fsModuleCompileConfig.isTransitive).isTrue
    }

    @Test
    fun `project provides and handles fsDependency method`() {
        project.plugins.apply(FSMConfigurationsPlugin.NAME)
        project.repositories.add(project.repositories.mavenCentral())
        val resultingDependency = project.fsDependency("com.google.guava:guava:24.0-jre")

        assertThat(resultingDependency).isEqualTo("com.google.guava:guava:24.0-jre")
    }

    @Test
    fun `fsDependency method fails for duplicated excluded dependency`() {
        project.plugins.apply(FSMConfigurationsPlugin.NAME)
        project.repositories.add(project.repositories.mavenCentral())
        project.fsDependency(mapOf("dependency" to "com.google.guava:guava:24.0-jre", "maxVersion" to "31.0"))

        assertThatThrownBy { project.fsDependency(mapOf("dependency" to "com.google.guava:guava:24.0-jre", "minVersion" to "2.0")) }
            .isInstanceOf(java.lang.IllegalArgumentException::class.java)
    }

    @Test
    fun `fsDependency method fails on non-String type for minVersion argument`() {
        project.plugins.apply(FSMConfigurationsPlugin.NAME)
        project.repositories.add(project.repositories.mavenCentral())

        assertThatThrownBy { project.fsDependency("com.google.guava:guava:24.0-jre", true, true) }
            .isInstanceOf(ClassCastException::class.java)
    }

    @Test
    fun `fsDependency method fails on non-String type for maxVersion argument`() {
        project.plugins.apply(FSMConfigurationsPlugin.NAME)
        project.repositories.add(project.repositories.mavenCentral())

        assertThatThrownBy { project.fsDependency("com.google.guava:guava:24.0-jre", true, "1.0.0", true) }
            .isInstanceOf(ClassCastException::class.java)
    }

}