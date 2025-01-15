package org.gradle.plugins.fsm

import org.assertj.core.api.Assertions.assertThat
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class FSMPluginExtensionTest {

    private lateinit var testling: FSMPluginExtension
    private lateinit var project: Project

    @BeforeEach
    fun setUp() {
        project = ProjectBuilder.builder().build()
        project.plugins.apply(FSMPlugin.NAME)
        testling = FSMPluginExtension(project)
    }

    @Test
    fun testWebApps() {
        val webAppSubprojectA = ProjectBuilder.builder().withParent(project).withName("web_a").build()
        val webAppSubprojectB = ProjectBuilder.builder().withParent(project).withName("web_b").build()
        testling.webAppComponent("WebAppA", webAppSubprojectA)
        testling.webAppComponent(webAppSubprojectB)
        assertThat(testling.getWebApps()).containsEntry("WebAppA", webAppSubprojectA)
        assertThat(testling.getWebApps()).containsEntry("web_b", webAppSubprojectB)

        // Ensure the project has a compile dependency on the subprojects
        val dependencyProjects = project.configurations.getByName(FSMPlugin.WEBAPPS_CONFIGURATION_NAME)
            .dependencies.filterIsInstance<ProjectDependency>().map { it.dependencyProject(project) }
        assertThat(dependencyProjects).containsExactly(webAppSubprojectA, webAppSubprojectB)
    }

    @Test
    fun testMinVersionIsDefault() {
        assertThat(testling.appendDefaultMinVersion).isTrue
    }


    @Test
    fun testCanSetFsmDependencies() {
        val fsmModuleName = "fsmModuleName"
        testling.fsmDependencies = listOf(fsmModuleName)
        assertThat(testling.fsmDependencies).contains(fsmModuleName)
    }
}