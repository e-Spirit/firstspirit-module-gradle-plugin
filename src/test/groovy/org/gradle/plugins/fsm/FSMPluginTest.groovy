/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.plugins.fsm

import org.assertj.core.api.HamcrestCondition
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.plugins.fsm.annotations.FSMAnnotationsPlugin
import org.gradle.plugins.fsm.configurations.FSMConfigurationsPlugin
import org.gradle.plugins.fsm.tasks.bundling.FSM
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

import static org.assertj.core.api.Assertions.assertThat
import static org.gradle.plugins.fsm.util.Matchers.dependsOn

class FSMPluginTest {

	Project project

	@BeforeEach
	void setUp() {
		project = ProjectBuilder.builder().build()
	}

	@Test
	void fsmPluginApplied() {
		project.apply plugin: FSMPlugin.NAME
		assertThat(project.plugins.hasPlugin(FSMPlugin)).isTrue()
	}

	@Test
	void fsmPluginAppliesConfigurationPlugin() {
		project.apply plugin: FSMPlugin.NAME
		assertThat(project.plugins.hasPlugin(FSMConfigurationsPlugin)).isTrue()
	}
	@Test
	void fsmPluginAppliesAnnotationsPlugin() {
		project.apply plugin: FSMPlugin.NAME
		assertThat(project.plugins.hasPlugin(FSMAnnotationsPlugin)).isTrue()
	}

	@Test
	void appliesTask() {
		project.apply plugin: FSMPlugin.NAME

		def task = project.tasks[FSMPlugin.FSM_TASK_NAME]
		assertThat(task).isInstanceOf(FSM)
	}

	@Test
	void fsmTaskDependsOnJarAndClassesTask() {
		project.apply plugin: FSMPlugin.NAME

		Task fsm = project.tasks[FSMPlugin.FSM_TASK_NAME]
		assertThat(fsm).is(new HamcrestCondition<Task>(dependsOn(JavaPlugin.JAR_TASK_NAME, JavaPlugin.CLASSES_TASK_NAME, FSMPlugin.GENERATE_LICENSE_REPORT_TASK_NAME)))
	}
	
	@Test
	void assembleTaskDependsOnFSMTask() {
		project.apply plugin: FSMPlugin.NAME

		Task assemble = project.tasks[BasePlugin.ASSEMBLE_TASK_NAME]
		assertThat(assemble).is(new HamcrestCondition<Task>(dependsOn(FSMPlugin.FSM_TASK_NAME)))
	}

	@Test
	void checkTaskDependsOnIsolationCheckTask() {
		project.apply plugin: FSMPlugin.NAME

		Task check = project.tasks[JavaBasePlugin.CHECK_TASK_NAME]
		assertThat(check).is(new HamcrestCondition<Task>(dependsOn(JavaPlugin.TEST_TASK_NAME, FSMPlugin.ISOLATION_CHECK_TASK_NAME)))
	}

    @Test
    void isolationCheckTaskUsesFsmOutputAsInput() {
        project.apply plugin: FSMPlugin.NAME

        Task fsm = project.tasks[FSMPlugin.FSM_TASK_NAME]
        File jarFile = fsm.outputs.files.singleFile
        Task isolationCheck = project.tasks[FSMPlugin.ISOLATION_CHECK_TASK_NAME]

        assertThat(isolationCheck.inputs.files.singleFile).isEqualTo(jarFile)
    }

	@Test
	void isolationCheckTaskDependsOnFsmTask() {
		project.apply plugin: FSMPlugin.NAME

		Task fsmTask = project.tasks[FSMPlugin.FSM_TASK_NAME]
		Task checkIsolationTask = project.tasks[FSMPlugin.ISOLATION_CHECK_TASK_NAME]

		assertThat(checkIsolationTask).is(new HamcrestCondition<Task>(dependsOn(fsmTask.name)))
	}

	@Test
	void jarPublicationRemoved() {
		project.apply plugin: FSMPlugin.NAME

		Configuration archiveConfiguration = project.getConfigurations().getByName(Dependency.ARCHIVES_CONFIGURATION)
		assertThat(archiveConfiguration.getAllArtifacts()).isEmpty()
	}

	
	@Test
	void moduleXMLExcludedFromJarArtifact() {
		new FSMPlugin().apply(project)
		
		Task jarTask = project.tasks[JavaPlugin.JAR_TASK_NAME]
		assertThat(jarTask.excludes).contains("module-isolated.xml")
	}
}
