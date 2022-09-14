package org.gradle.plugins.fsm

import org.assertj.core.api.Assertions.assertThat
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.jvm.tasks.Jar
import org.gradle.plugins.fsm.configurations.FSMConfigurationsPlugin
import org.gradle.plugins.fsm.tasks.bundling.FSM
import org.gradle.plugins.fsm.util.TaskAssert.Companion.assertThat
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class FSMPluginTest {

    private lateinit var project: Project

    @BeforeEach
    fun setUp() {
        project = ProjectBuilder.builder().build()
    }

    @Test
    fun `FSM plugin applied`() {
        project.plugins.apply(FSMPlugin.NAME)
        assertThat(project.plugins.hasPlugin(FSMPlugin::class.java)).isTrue
    }

    @Test
    fun `FSM plugin applies configuration-plugin`() {
        project.plugins.apply(FSMPlugin.NAME)
        assertThat(project.plugins.hasPlugin(FSMConfigurationsPlugin::class.java)).isTrue
    }

    @Test
    fun `FSM plugin applies annotations-plugin`() {
        project.plugins.apply(FSMPlugin.NAME)
        assertThat(project.plugins.hasPlugin(FSMConfigurationsPlugin::class.java)).isTrue
    }

    @Test
    fun `applies task`() {
        project.plugins.apply(FSMPlugin.NAME)

        val task = project.tasks.getByName(FSMPlugin.FSM_TASK_NAME)
        assertThat(task).isInstanceOf(FSM::class.java)
    }

    @Test
    fun `FSM-task depends on jar and classes tasks`() {
        project.plugins.apply(FSMPlugin.NAME)

        val fsm = project.tasks.getByName(FSMPlugin.FSM_TASK_NAME)
        assertThat(fsm).dependsOn(JavaPlugin.JAR_TASK_NAME, JavaPlugin.CLASSES_TASK_NAME,
            FSMPlugin.GENERATE_LICENSE_REPORT_TASK_NAME, FSMPlugin.CONFIGURE_FSM_TASK_NAME)
    }

    @Test
    fun `assemble-task depends on FSM-task`() {
        project.plugins.apply(FSMPlugin.NAME)

        val assemble = project.tasks.getByName(BasePlugin.ASSEMBLE_TASK_NAME)
        assertThat(assemble).dependsOn(FSMPlugin.FSM_TASK_NAME)
    }

    @Test
    fun `check-task depends on isolation-check task`() {
        project.plugins.apply(FSMPlugin.NAME)

        val check = project.tasks.getByName(JavaBasePlugin.CHECK_TASK_NAME)
        assertThat(check).dependsOn(JavaPlugin.TEST_TASK_NAME, FSMPlugin.ISOLATION_CHECK_TASK_NAME)
    }

    @Test
    fun `isolation-check-task uses FSM output as input`() {
        project.plugins.apply(FSMPlugin.NAME)

        val fsm = project.tasks.getByName(FSMPlugin.FSM_TASK_NAME)
        val fsmFile = fsm.outputs.files.singleFile
        val isolationCheck = project.tasks.getByName(FSMPlugin.ISOLATION_CHECK_TASK_NAME)

        assertThat(isolationCheck.inputs.files.singleFile).isEqualTo(fsmFile)
    }

    @Test
    fun `isolation-check-task depends on FSM-task`() {
        project.plugins.apply(FSMPlugin.NAME)

        val fsmTask = project.tasks.getByName(FSMPlugin.FSM_TASK_NAME)
        val checkIsolationTask = project.tasks.getByName(FSMPlugin.ISOLATION_CHECK_TASK_NAME)

        assertThat(checkIsolationTask).dependsOn(fsmTask.name)
    }

    @Test
    fun `jar-publication removed`() {
        project.plugins.apply(FSMPlugin.NAME)

        val archiveConfiguration = project.configurations.getByName(Dependency.ARCHIVES_CONFIGURATION)
        assertThat(archiveConfiguration.allArtifacts).isEmpty()
    }


    @Test
    fun `module-XML excluded from jar artifact`() {
        project.plugins.apply(FSMPlugin.NAME)

        val jarTask = project.tasks.named(JavaPlugin.JAR_TASK_NAME, Jar::class.java)
        assertThat(jarTask.get().excludes).contains("module-isolated.xml")
    }
    
}