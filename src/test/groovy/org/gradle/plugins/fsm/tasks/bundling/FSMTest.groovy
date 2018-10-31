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
package org.gradle.plugins.fsm.tasks.bundling

import de.espirit.firstspirit.server.module.ModuleInfo
import org.apache.commons.io.IOUtils
import org.gradle.api.Project
import org.gradle.plugins.fsm.FSMPlugin
import org.gradle.plugins.fsm.FSMPluginExtension
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

import static org.assertj.core.api.Assertions.assertThat
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import static org.mockito.Mockito.spy

class FSMTest {

    @Rule
	public TemporaryFolder temporaryFolder = new TemporaryFolder()

	private File testDir
	private Project project

	FSM fsm

	@Before
	void setUp() {
        testDir = temporaryFolder.newFolder()

		project = ProjectBuilder.builder().withProjectDir(testDir).build()
		project.apply plugin: FSMPlugin.NAME

		fsm = project.tasks[FSMPlugin.FSM_TASK_NAME] as FSM
		
		fsm.baseName = 'testbasename'
		fsm.appendix = 'testappendix'
		fsm.version = '1.0'
	}

	@Test
	void testExecute() {
		fsm.execute()
		assertThat(fsm.destinationDir).isDirectory()
		assertThat(fsm.archivePath).isFile()
	}

	@Test
	void pluginExposesMethodForExcludedDependencies() {
		project.repositories.add(project.getRepositories().mavenCentral())
		def resultingDependency = project.fsDependency("com.google.guava:guava:24.0-jre", true)
		fsm.execute()
		assertThat(resultingDependency).is("com.google.guava:guava:24.0-jre")
		def skippedInLegacyDependencies = project.configurations.getByName(FSMPlugin.FS_SKIPPED_IN_LEGACY_CONFIGURATION_NAME).dependencies.collect { it }
		assertThat(skippedInLegacyDependencies.size()).isEqualTo(1)
		assertThat(skippedInLegacyDependencies.get(0).group).isEqualTo("com.google.guava")
		assertThat(skippedInLegacyDependencies.get(0).name).isEqualTo("guava")
		assertThat(skippedInLegacyDependencies.get(0).version).isEqualTo("24.0-jre")
	}

	@Test
	void pluginExposesMethodWithNamedArgumentsForExcludedDependencies() {
		project.repositories.add(project.getRepositories().mavenCentral())
		def resultingDependency = project.fsDependency(dependency: "com.google.guava:guava:24.0-jre", skipInLegacy:  true, maxVersion: "31.0")
		fsm.execute()
		assertThat(resultingDependency).is("com.google.guava:guava:24.0-jre")
		def skippedInLegacyDependencies = project.configurations.getByName(FSMPlugin.FS_SKIPPED_IN_LEGACY_CONFIGURATION_NAME).dependencies.collect { it }
		assertThat(skippedInLegacyDependencies.size()).isEqualTo(1)
		assertThat(skippedInLegacyDependencies.get(0).group).isEqualTo("com.google.guava")
		assertThat(skippedInLegacyDependencies.get(0).name).isEqualTo("guava")
		assertThat(skippedInLegacyDependencies.get(0).version).isEqualTo("24.0-jre")

		def dependencyConfigurations = project.plugins.getPlugin(FSMPlugin.class).getDependencyConfigurations()
		assertThat(dependencyConfigurations).contains(
				new FSMPlugin.MinMaxVersion("com.google.guava:guava:24.0-jre", null, "31.0"))
	}

	@Test(expected = IllegalStateException.class)
	void fsDependencyMethodFailsForDuplicatedExcludedDependencies() {
		project.repositories.add(project.getRepositories().mavenCentral())
		project.fsDependency(dependency: "com.google.guava:guava:24.0-jre", skipInLegacy:  true, maxVersion: "31.0")
		project.fsDependency(dependency: "com.google.guava:guava:24.0-jre", minVersion: "2.0")
	}

	@Test(expected = IllegalArgumentException.class)
	void fsDependencyMethodFailsOnNonBooleanTypeForSkipInLegacyParameter() {
		project.repositories.add(project.getRepositories().mavenCentral())
		project.fsDependency("com.google.guava:guava:24.0-jre", "31.0")
	}

	@Test(expected = IllegalArgumentException.class)
	void fsDependencyMethodFailsOnNonStringTypeForMinVersionParameter() {
		project.repositories.add(project.getRepositories().mavenCentral())
		project.fsDependency("com.google.guava:guava:24.0-jre", true, true)
	}
	@Test(expected = IllegalArgumentException.class)
	void fsDependencyMethodFailsOnNonStringTypeForMaxVersionParameter() {
		project.repositories.add(project.getRepositories().mavenCentral())
		project.fsDependency("com.google.guava:guava:24.0-jre", true, "1.0.0", true)
	}

	@Test
	void fsmExtensionUsed() {
		assertThat(fsm.extension).isEqualTo(FSM.FSM_EXTENSION)
	}
	
	@Test
	void archivePathUsed() {
		assertThat(fsm.archivePath.toPath()).isEqualTo(project.buildDir.toPath().resolve("fsm").resolve(fsm.archiveName))
	}

	@Test
	void displayNameUsed() {
		String displayName = 'Human-Readable Display Name'
		FSMPluginExtension pluginExtension = project.extensions.getByType(FSMPluginExtension.class)
		pluginExtension.displayName = displayName

		fsm.execute()

		assertThat(moduleXml()).contains("""<displayname>${displayName}</displayname>""")
	}
	@Test
	void moduleDirNameUsed() {
		FSMPluginExtension pluginExtension = project.extensions.getByType(FSMPluginExtension.class)
		pluginExtension.moduleDirName = getClass().getClassLoader().getResource("module.xml").path

		fsm.execute()

		assertThat(moduleXml()).contains("""<custom-tag>custom</custom-tag>""")
	}
	@Test(expected = IllegalArgumentException)
	void moduleDirContainsNoModuleXML() {
		FSMPluginExtension pluginExtension = project.extensions.getByType(FSMPluginExtension.class)
		pluginExtension.moduleDirName = "some/empty/dir"

		fsm.execute()
	}

	@Test
	void module_name_should_be_equal_to_project_name_if_not_set() {
		String projectName = "MyProjectName"
		Project myProject = ProjectBuilder.builder().withProjectDir(testDir).withName(projectName).build()
		myProject.apply plugin: FSMPlugin.NAME
		FSM myFSM = myProject.tasks[FSMPlugin.FSM_TASK_NAME] as FSM
		String xml = "<module><name>\$name</name></module>"
		String expectedXML = "<module><name>${projectName}</name></module>"

		String resultXML = myFSM.filterModuleXml(xml, "", "")

		assertThat(resultXML).isEqualTo(expectedXML)
	}

	@Test
	void module_name_should_be_equal_to_set_module_name() {
		String projectName = "MyProjectName"
		String moduleName = "MyModule"
		Project myProjectSpy = spy(ProjectBuilder.builder().withProjectDir(testDir).withName(projectName).build())
		myProjectSpy.apply plugin: FSMPlugin.NAME
		FSM myFSM = myProjectSpy.tasks[FSMPlugin.FSM_TASK_NAME] as FSM
		FSMPluginExtension pluginExtension = project.extensions.getByType(FSMPluginExtension.class)
		myFSM.pluginExtension = pluginExtension
		pluginExtension.moduleName = moduleName
		String xml = "<module><name>\$name</name></module>"
		String expectedXML = "<module><name>${moduleName}</name></module>"

		String resultXML = myFSM.filterModuleXml(xml, "", "")

		assertThat(resultXML).isEqualTo(expectedXML)
	}

    @Test
    void vendor() {
		FSMPluginExtension pluginExtension = project.extensions.getByType(FSMPluginExtension.class)
        String vendor = "ACME"
        pluginExtension.vendor = vendor

        fsm.execute()

        assertThat(moduleXml()).contains("""<vendor>${vendor}</vendor>""")
    }

	@Test
	void resourceIsConfiguredAsIsolatedByDefault() {
		project.repositories.add(project.getRepositories().mavenCentral())
		project.dependencies.add("fsModuleCompile", "com.google.guava:guava:24.0-jre")

		fsm.execute()

		assertThat(moduleXml()).contains("""<resource name="com.google.guava:guava" scope="module" mode="isolated" version="24.0-jre" minVersion="24.0-jre">lib/guava-24.0-jre.jar</resource>""")
	}

	@Test
	void resourceIsConfiguredAsLegacyWhenConfigured() {
		project.repositories.add(project.getRepositories().mavenCentral())
		project.dependencies.add("fsModuleCompile", "com.google.guava:guava:24.0-jre")
		FSMPluginExtension pluginExtension = project.extensions.getByType(FSMPluginExtension.class)
		pluginExtension.resourceMode = ModuleInfo.Mode.LEGACY

		fsm.execute()

		assertThat(moduleXml()).contains("""<resource name="com.google.guava:guava" scope="module" mode="legacy" version="24.0-jre" minVersion="24.0-jre">lib/guava-24.0-jre.jar</resource>""")
	}

	@Test
	void resourceHasMinAndMaxVersionWhenConfigured() {
		project.repositories.add(project.getRepositories().mavenCentral())
		project.dependencies.add("fsModuleCompile", project.fsDependency("com.google.guava:guava:24.0-jre", false, "0.0.1", "99.0.0"))
		FSMPluginExtension pluginExtension = project.extensions.getByType(FSMPluginExtension.class)
		pluginExtension.resourceMode = ModuleInfo.Mode.LEGACY

		def dependencyConfigurations = project.plugins.getPlugin(FSMPlugin.class).getDependencyConfigurations()
		assertThat(dependencyConfigurations).contains(new FSMPlugin.MinMaxVersion("com.google.guava:guava:24.0-jre", "0.0.1", "99.0.0"))
		assertThat(dependencyConfigurations.size()).isEqualTo(1)
		fsm.execute()

		assertThat(moduleXml()).contains("""<resource name="com.google.guava:guava" scope="module" mode="legacy" version="24.0-jre" minVersion="0.0.1" maxVersion="99.0.0">lib/guava-24.0-jre.jar</resource>""")
	}

	@Test
	void resourceIsSkippedInLegacyWhenConfigured() {
		project.repositories.add(project.getRepositories().mavenCentral())
		project.dependencies.add("fsModuleCompile", 'junit:junit:4.12')
		project.dependencies.add("fsModuleCompile", project.fsDependency(dependency: 'com.google.guava:guava:24.0-jre', skipInLegacy: true))
		FSMPluginExtension pluginExtension = project.extensions.getByType(FSMPluginExtension.class)
		pluginExtension.resourceMode = ModuleInfo.Mode.LEGACY

		fsm.execute()

		assertThat(moduleXml()).contains("""<resource name="junit:junit" scope="module" mode="legacy" version="4.12" minVersion="4.12">lib/junit-4.12.jar</resource>""")
		assertThat(moduleXml()).doesNotContain("""<resource name="com.google.guava:guava" scope="module" mode="legacy" version="24.0-jre" minVersion="24.0-jre">lib/guava-24.0-jre.jar</resource>""")
	}

    @Test
    void staticFilesResource() {
        Path filesDir = fsm.project.file('src/main/files').toPath()
        Files.createDirectories(filesDir)
        Files.write(filesDir.resolve("testFile"), "Test".getBytes(StandardCharsets.UTF_8))

        fsm.execute()

        assertThat(moduleXml()).contains("""<resource name=":test-files" version="unspecified" scope="module" mode="isolated">files/</resource>""")
    }

	@Test
	void staticIsolatedFilesResource() {
		Path filesDir = fsm.project.file('src/main/files').toPath()
		Files.createDirectories(filesDir)
		Files.write(filesDir.resolve("testFile"), "Test".getBytes(StandardCharsets.UTF_8))
		FSMPluginExtension pluginExtension = project.extensions.getByType(FSMPluginExtension.class)
		pluginExtension.resourceMode = ModuleInfo.Mode.ISOLATED

		fsm.execute()

		assertThat(moduleXml()).contains("""<resource name=":test-files" version="unspecified" scope="module" mode="isolated">files/</resource>""")
	}

    @Test
    void noStaticFilesResource() {
        fsm.execute()

        assertThat(moduleXml()).doesNotContain(":test-files")
    }

    private String moduleXml() {
        final Path fsmFile = testDir.toPath().resolve("build").resolve("fsm").resolve(fsm.archiveName)
        assertThat(fsmFile).exists()
        final ZipFile zipFile = new ZipFile(fsmFile.toFile())
        zipFile.withCloseable {
            ZipEntry moduleXmlEntry = zipFile.getEntry("META-INF/module.xml")
            assertThat(moduleXmlEntry).isNotNull()
            zipFile.getInputStream(moduleXmlEntry).withCloseable {
                return IOUtils.toString(it, StandardCharsets.UTF_8)
            }
        }
    }
}
