package org.gradle.plugins.fsm.annotations

import org.assertj.core.api.Assertions.assertThat
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.plugins.fsm.FSMPlugin
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.*

class FSMAnnotationsPluginTest {

    private lateinit var project: Project

    @BeforeEach
    fun setUp() {
        project = ProjectBuilder.builder().build()
    }

    @Test
    fun `applies annotation plugin to project`() {
        project.plugins.apply(FSMAnnotationsPlugin.NAME)
        assertThat(project.plugins.hasPlugin(FSMAnnotationsPlugin::class.java)).isTrue
    }

    @Test
    fun `applies java plugin to project`() {
        project.plugins.apply(FSMAnnotationsPlugin.NAME)

        assertThat(project.plugins.hasPlugin(JavaPlugin::class.java)).isTrue
    }

    @Test
    fun `adds annotation dependency to project`() {
        project.plugins.apply(FSMAnnotationsPlugin.NAME)
        val annotationDependencyCompileOnly = project.configurations.getByName("compileOnly").dependencies.find {
            it.group == "com.espirit.moddev.components" && it.name == "annotations"
        } ?: error("annotations dependency not found for 'compileOnly' configuration")

        val props = Properties()
        FSMPlugin::class.java.getResourceAsStream("/versions.properties").use {
            props.load(it)
        }

        assertThat(props["fsm-annotations-version"]).isEqualTo(annotationDependencyCompileOnly.version)
    }
    
}