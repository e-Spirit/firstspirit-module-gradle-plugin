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

import org.gradle.api.plugins.JavaBasePlugin

import static org.gradle.plugins.fsm.util.Matchers.*
import static org.gradle.plugins.fsm.util.TestUtil.getNames
import static org.hamcrest.Matchers.*
import static org.junit.Assert.*

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.plugins.fsm.tasks.bundling.FSM
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.util.WrapUtil
import org.junit.Before
import org.junit.Test

class FSMPluginTest {

	Project project
	FSMPlugin fsmPlugin

	@Before
	void setUp() {
		project = ProjectBuilder.builder().build()
		fsmPlugin = new FSMPlugin()
	}

	@Test
	void fsmPluginApplied() {
		project.apply plugin: FSMPlugin.NAME
		assertTrue(project.plugins.hasPlugin(FSMPlugin))
	}

	@Test
	void javaPluginApplied() {
		project.apply plugin: FSMPlugin.NAME
		assertTrue(project.plugins.hasPlugin(JavaPlugin))
	}

	@Test
	void appliesTask() {
		project.apply plugin: FSMPlugin.NAME

		def task = project.tasks[FSMPlugin.FSM_TASK_NAME]
		assertThat(task, instanceOf(FSM))
	}
	
	@Test
	void fsmTaskDependsOnJarAndClassesTask() {
		project.apply plugin: FSMPlugin.NAME

		Task fsm = project.tasks[FSMPlugin.FSM_TASK_NAME]
		assertThat(fsm, dependsOn(JavaPlugin.JAR_TASK_NAME, JavaPlugin.CLASSES_TASK_NAME))
	}
	
	@Test
	void assembleTaskDependsOnFSMTask() {
		project.apply plugin: FSMPlugin.NAME

		Task assemble = project.tasks[BasePlugin.ASSEMBLE_TASK_NAME]
		assertThat(assemble, dependsOn(FSMPlugin.FSM_TASK_NAME))
	}

	@Test
	void checkTaskDependsOnIsolationCheckTask() {
		project.apply plugin: FSMPlugin.NAME

		Task check = project.tasks[JavaBasePlugin.CHECK_TASK_NAME]
		assertThat(check, dependsOn(JavaPlugin.TEST_TASK_NAME, FSMPlugin.ISOLATION_CHECK_TASK_NAME))
	}

    @Test
    void isolationCheckTaskUsesFsmOutputAsInput() {
        project.apply plugin: FSMPlugin.NAME

        Task fsm = project.tasks[FSMPlugin.FSM_TASK_NAME]
        File jarFile = fsm.outputs.files.singleFile
        Task isolationCheck = project.tasks[FSMPlugin.ISOLATION_CHECK_TASK_NAME]

        assertThat(isolationCheck.inputs.files.singleFile, equalTo(jarFile))
    }

	@Test
	void replacesJarAsPublication() {
		project.apply plugin: FSMPlugin.NAME

		Configuration archiveConfiguration = project.getConfigurations().getByName(Dependency.ARCHIVES_CONFIGURATION)
		assertThat(archiveConfiguration.getAllArtifacts().size(), equalTo(1))
		assertThat(archiveConfiguration.getAllArtifacts().iterator().next().getType(), equalTo("fsm"))
	}

	@Test
	void jarCompileConfigurationExtendsFsScopes() {
		fsmPlugin.apply(project)

		def compileConfig = project.configurations.getByName(JavaPlugin.COMPILE_CONFIGURATION_NAME)
		def fsCompileScopes = WrapUtil.toSet(FSMPlugin.FS_MODULE_COMPILE_CONFIGURATION_NAME, FSMPlugin.FS_SERVER_COMPILE_CONFIGURATION_NAME, FSMPlugin.FS_WEB_COMPILE_CONFIGURATION_NAME)
		assertThat(getNames(compileConfig.extendsFrom), equalTo(fsCompileScopes))
		assertFalse(compileConfig.visible)
		assertTrue(compileConfig.transitive)
	}

	@Test
	void jarRuntimeConfigurationExtendsFsScopes() {
		fsmPlugin.apply(project)

		def runtimeConfig = project.configurations.getByName(JavaPlugin.RUNTIME_CONFIGURATION_NAME)
		assertThat(getNames(runtimeConfig.extendsFrom), equalTo(WrapUtil.toSet(JavaPlugin.COMPILE_CONFIGURATION_NAME, FSMPlugin.PROVIDED_RUNTIME_CONFIGURATION_NAME)))
		assertFalse(runtimeConfig.visible)
		assertTrue(runtimeConfig.transitive)
	}

	@Test
	void jarCompileOnlyConfigurationExtendsFsScopes() {
		fsmPlugin.apply(project)

		def compileOnlyConfig = project.configurations.getByName(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME)
		assertThat(getNames(compileOnlyConfig.extendsFrom), equalTo(WrapUtil.toSet(FSMPlugin.PROVIDED_COMPILE_CONFIGURATION_NAME)))
		assertFalse(compileOnlyConfig.visible)
		assertTrue(compileOnlyConfig.transitive)
	}

	@Test
	void createsConfigurations() {
		fsmPlugin.apply(project)

		def configuration = project.configurations.getByName(FSMPlugin.PROVIDED_RUNTIME_CONFIGURATION_NAME)
		assertThat(getNames(configuration.extendsFrom), equalTo(WrapUtil.toSet(FSMPlugin.PROVIDED_COMPILE_CONFIGURATION_NAME)))
		assertFalse(configuration.visible)
		assertTrue(configuration.transitive)
	}
	
	@Test
	void moduleXMLExcludedFromJarArtifact() {
		fsmPlugin.apply(project)
		
		Task jarTask = project.tasks[JavaPlugin.JAR_TASK_NAME]
		assertThat(jarTask.excludes, contains("module.xml"))
	}
}
