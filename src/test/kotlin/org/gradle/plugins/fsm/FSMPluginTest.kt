package org.gradle.plugins.fsm

import org.assertj.core.api.Assertions.assertThat
import org.gradle.api.Project
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.jvm.tasks.Jar
import org.gradle.plugins.fsm.configurations.FSMConfigurationsPlugin
import org.gradle.plugins.fsm.descriptor.defineArtifactoryForProject
import org.gradle.plugins.fsm.descriptor.setArtifactoryCredentialsFromLocalProperties
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
        project.setArtifactoryCredentialsFromLocalProperties()
        project.defineArtifactoryForProject()
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
    fun `FSM-task depends on jar task`() {
        project.plugins.apply(FSMPlugin.NAME)

        val fsm = project.tasks.getByName(FSMPlugin.FSM_TASK_NAME)
        assertThat(fsm).dependsOn(JavaPlugin.JAR_TASK_NAME, FSMPlugin.GENERATE_LICENSE_REPORT_TASK_NAME)
    }

    @Test
    fun `assemble-task depends on FSM-task`() {
        project.plugins.apply(FSMPlugin.NAME)

        val assemble = project.tasks.getByName(BasePlugin.ASSEMBLE_TASK_NAME)
        assertThat(assemble).dependsOn(FSMPlugin.FSM_TASK_NAME)
    }

    @Test
    fun `check-task depends on validate-descriptor task`() {
        project.plugins.apply(FSMPlugin.NAME)

        val check = project.tasks.getByName(JavaBasePlugin.CHECK_TASK_NAME)
        assertThat(check).dependsOn(JavaPlugin.TEST_TASK_NAME, FSMPlugin.VALIDATE_DESCRIPTOR_TASK_NAME)
    }

    @Test
    fun `validate-task uses FSM output as input`() {
        project.plugins.apply(FSMPlugin.NAME)

        val fsm = project.tasks.getByName(FSMPlugin.FSM_TASK_NAME)
        val fsmFile = fsm.outputs.files.singleFile
        val validateDescriptor = project.tasks.getByName(FSMPlugin.VALIDATE_DESCRIPTOR_TASK_NAME)

        assertThat(validateDescriptor.inputs.files.singleFile).isEqualTo(fsmFile)
    }

    @Test
    fun `validate-task depends on FSM-task`() {
        project.plugins.apply(FSMPlugin.NAME)

        val fsmTask = project.tasks.getByName(FSMPlugin.FSM_TASK_NAME)
        val validateDescriptor = project.tasks.getByName(FSMPlugin.VALIDATE_DESCRIPTOR_TASK_NAME)

        assertThat(validateDescriptor).dependsOn(fsmTask.name)
    }

    @Test
    fun `validate-task called automatically for assembleFSM`() {
        project.plugins.apply(FSMPlugin.NAME)

        val fsmTask = project.tasks.getByName(FSMPlugin.FSM_TASK_NAME)
        val finalizers = fsmTask.finalizedBy.getDependencies(fsmTask)
        assertThat(finalizers.stream().anyMatch { it.name == FSMPlugin.VALIDATE_DESCRIPTOR_TASK_NAME }).isTrue
    }

    @Test
    fun `module-XML excluded from jar artifact`() {
        project.plugins.apply(FSMPlugin.NAME)

        val jarTask = project.tasks.named(JavaPlugin.JAR_TASK_NAME, Jar::class.java)
        assertThat(jarTask.get().excludes).contains("module-isolated.xml")
    }
    
}