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
import org.gradle.plugins.fsm.configurations.FSMConfigurationsPlugin
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

import static org.assertj.core.api.Assertions.assertThat
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown
import static org.gradle.plugins.fsm.ComponentHelper.addTestModulesToBlacklist
import static org.gradle.plugins.fsm.util.TestProjectUtils.defineArtifactoryForProject
import static org.gradle.plugins.fsm.util.TestProjectUtils.setArtifactoryCredentialsFromLocalProperties
import static org.mockito.Mockito.spy

class FSMTest {

	private File testDir
	private Project project

	FSM fsm

	@BeforeEach
	void setUp(@TempDir final File tempDir) {
        testDir = tempDir

		project = ProjectBuilder.builder().withProjectDir(testDir).build()
		setArtifactoryCredentialsFromLocalProperties(project)
		defineArtifactoryForProject(project)

		project.apply plugin: FSMPlugin.NAME

		fsm = project.tasks[FSMPlugin.FSM_TASK_NAME] as FSM

		fsm.archiveBaseName.set('testbasename')
		fsm.archiveAppendix.set('testappendix')
		fsm.archiveVersion.set('1.0')

		addTestModulesToBlacklist(fsm)
	}

	@Test
	void testExecute() {
		fsm.execute()
		assertThat(fsm.destinationDirectory.get().asFile).isDirectory()
		assertThat(fsm.archiveFile.get().asFile).isFile()
	}

	@Test
	void fsmExtensionUsed() {
		assertThat(fsm.archiveExtension.get()).isEqualTo(FSM.FSM_EXTENSION)
	}


	@Test
	void theTaskNameShouldBeComposedByAVerbAndAnObject(){
		String verb = "assemble"
		String object = "FSM"
		assertThat(project.tasks[verb + object]).isNotNull();
	}

	@Test
	void jarBaseNameIsUsed() {
		project.version = '0.0.1-SNAPSHOT'
		project.jar {
			baseName = 'xxxxx'
		}
		fsm.execute()

		assertThat(moduleXml())
			.as("module.xml should contain a 'global' resource with the correctly named jar task output in module scope!")
			.contains("""<resource name="${project.group}:${project.name}" version="0.0.1-SNAPSHOT" scope="module" mode="isolated">lib/xxxxx-0.0.1-SNAPSHOT.jar</resource>""")
		assertThat(moduleXml())
			.as("module.xml should contain a web resource with the correctly named jar task output!")
			.contains("""<resource name="${project.group}:${project.name}" version="0.0.1-SNAPSHOT">lib/xxxxx-0.0.1-SNAPSHOT.jar</resource>""")
	}

	@Test
	void archivePathUsed() {
		assertThat(fsm.archiveFile.get().asFile.toPath()).isEqualTo(project.buildDir.toPath().resolve("fsm").resolve(fsm.archiveFile.get().asFile.name))
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

	@Test
	void moduleDirContainsNoRelevantFiles() {
		FSMPluginExtension pluginExtension = project.extensions.getByType(FSMPluginExtension.class)
		pluginExtension.moduleDirName = "some/empty/dir"

		try {
			fsm.execute()
			failBecauseExceptionWasNotThrown(IllegalArgumentException.class)
		} catch(final IllegalArgumentException e) {
			assertThat(e).hasMessage("No module.xml or module-isolated.xml found in moduleDir some/empty/dir")
		}
	}

    @Test
    void moduleDirContainsOnlyModuleXML() {
        FSMPluginExtension pluginExtension = project.extensions.getByType(FSMPluginExtension.class)
        pluginExtension.moduleDirName = getClass().getClassLoader().getResource("legacyonly").path

        fsm.execute()

        assertThat(moduleXml()).contains("""<custom-tag>custom</custom-tag>""")
        assertThat(moduleXml(true)).doesNotContain("""<custom-tag>custom</custom-tag>""")
    }

    @Test
    void moduleDirContainsOnlyModuleIsolatedXML() {
        FSMPluginExtension pluginExtension = project.extensions.getByType(FSMPluginExtension.class)
        pluginExtension.moduleDirName = getClass().getClassLoader().getResource("isolatedonly").path

        fsm.execute()

        assertThat(moduleXml(true)).contains("""<custom-tag>custom</custom-tag>""")
        assertThat(moduleXml()).doesNotContain("""<custom-tag>custom</custom-tag>""")
    }

	@Test
	void moduleDirContainsBothXML() {
		FSMPluginExtension pluginExtension = project.extensions.getByType(FSMPluginExtension.class)
		pluginExtension.moduleDirName = getClass().getClassLoader().getResource("bothxml").path

		fsm.execute()

		assertThat(moduleXml(true)).contains("""<custom-tag>custom-isolated</custom-tag>""")
		assertThat(moduleXml()).contains("""<custom-tag>custom-legacy</custom-tag>""")
	}

	@Test
	void trimRemovesFileNameFromPath() {
		String testPath = "some/directory/containing/a.file"
		String trimmedPath = fsm.trimPathToDirectory(testPath)

		assertThat(trimmedPath).is("some/directory/containing")
	}

	@Test
	void trimIgnoresDotsInDirectoryNames() {
		String testPath = "a/directory/containing/a.dot/in/a/folder/name"
		String trimmedPath = fsm.trimPathToDirectory(testPath)

		assertThat(trimmedPath).is("a/directory/containing/a.dot/in/a/folder/name")
	}

	@Test
	void module_name_should_be_equal_to_project_name_if_not_set() {
		String projectName = "MyProjectName"
		Project myProject = ProjectBuilder.builder().withProjectDir(testDir).withName(projectName).build()
		myProject.apply plugin: FSMPlugin.NAME
		FSM myFSM = myProject.tasks[FSMPlugin.FSM_TASK_NAME] as FSM
		String xml = "<module><name>\$name</name></module>"
		String expectedXML = "<module><name>${projectName}</name></module>"

		String resultXML = myFSM.filterModuleXml(xml, new FSM.XMLData())

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

		String resultXML = myFSM.filterModuleXml(xml, new FSM.XMLData())

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

		def dependencyConfigurations = project.plugins.getPlugin(FSMConfigurationsPlugin.class).getDependencyConfigurations()
		assertThat(dependencyConfigurations).contains(new FSMConfigurationsPlugin.MinMaxVersion("com.google.guava:guava:24.0-jre", "0.0.1", "99.0.0"))
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
	void webResourceIsSkippedInLegacyWhenConfigured() {
		project.repositories.add(project.getRepositories().mavenCentral())
		project.dependencies.add("fsWebCompile", project.fsDependency(dependency: 'junit:junit:4.12', skipInLegacy: true))
		project.dependencies.add("fsWebCompile", project.fsDependency(dependency: 'com.google.guava:guava:24.0-jre', skipInLegacy: false))
		FSMPluginExtension pluginExtension = project.extensions.getByType(FSMPluginExtension.class)
		pluginExtension.resourceMode = ModuleInfo.Mode.LEGACY

		fsm.execute()

		String legacy = moduleXml(false)
		String isolated = moduleXml(true)
		assertThat(legacy).contains("<resource name=\"com.google.guava:guava\" version=\"24.0-jre\" minVersion=\"24.0-jre\">lib/guava-24.0-jre.jar</resource>")
		assertThat(isolated).contains("<resource name=\"com.google.guava:guava\" version=\"24.0-jre\" minVersion=\"24.0-jre\">lib/guava-24.0-jre.jar</resource>")
		assertThat(legacy).doesNotContain("<resource name=\"junit:junit\" version=\"4.12\" minVersion=\"4.12\">lib/junit-4.12.jar</resource>")
	}

	@Test
	void testLicensesCsvIsPresent() {
		fsm.execute()
		assertThat(moduleXml()).contains("<licenses>META-INF/licenses.csv</licenses>")
	}

	@Test
	void testFsmResourceFilesAreUsed() {
		project.version = "1.0.0"
		Path fsmResourcesProjectFolder = fsm.project.file('src/main/fsm-resources').toPath()
		Path fsmResourcesNestedProjectFolder = fsm.project.file('src/main/fsm-resources/resourcesFolder').toPath()
		Files.createDirectories(fsmResourcesNestedProjectFolder)
		Files.write(fsmResourcesProjectFolder.resolve("testResource.txt"), "Test".getBytes(StandardCharsets.UTF_8))
		Files.write(fsmResourcesNestedProjectFolder.resolve("testNested0.txt"), "Test".getBytes(StandardCharsets.UTF_8))
		Files.write(fsmResourcesNestedProjectFolder.resolve("testNested1.txt"), "Test".getBytes(StandardCharsets.UTF_8))

		fsm.execute()

		assertThat(moduleXml()).contains("<resource name=\"${project.group}:${project.name}-testResource.txt\" version=\"1.0.0\" scope=\"module\" mode=\"isolated\">testResource.txt</resource>")
		assertThat(moduleXml()).contains("<resource name=\"${project.group}:${project.name}-resourcesFolder\" version=\"1.0.0\" scope=\"module\" mode=\"isolated\">resourcesFolder</resource>")

		withFsmFile { ZipFile fsm ->
			assertThat(fsm.getEntry("testResource.txt")).isNotNull()
			assertThat(fsm.getEntry("resourcesFolder/testNested0.txt")).isNotNull()
			assertThat(fsm.getEntry("resourcesFolder/testNested1.txt")).isNotNull()
		}
	}


	@Test
	void testWebXmlResourcesAreExcludedFromResources() {
		/**
		 * web.xml and web0.xml files are used by our test webcomponent implementations.
		 * Those should be excluded from the resources tags in the module.xml, because
		 * they aren't regular fsm resources.
		 */
		project.version = "1.0.0"
		Path fsmResourcesTestProjectFolder = fsm.project.file('src/main/fsm-resources').toPath()
		Files.createDirectories(fsmResourcesTestProjectFolder)
		Files.write(fsmResourcesTestProjectFolder.resolve("web.xml"), "<xml></xml>".getBytes(StandardCharsets.UTF_8))
		Files.write(fsmResourcesTestProjectFolder.resolve("web0.xml"), "<xml></xml>".getBytes(StandardCharsets.UTF_8))
		Files.write(fsmResourcesTestProjectFolder.resolve("web1.xml"), "<xml></xml>".getBytes(StandardCharsets.UTF_8))

		fsm.execute()

		def moduleXml = moduleXml()
		assertThat(moduleXml).doesNotContain("<resource name=\"${project.group}:${project.name}-web.xml\" version=\"1.0.0\" scope=\"module\" mode=\"isolated\">web.xml</resource>")
		assertThat(moduleXml).doesNotContain("<resource name=\"${project.group}:${project.name}-web0.xml\" version=\"1.0.0\" scope=\"module\" mode=\"isolated\">web0.xml</resource>")
		assertThat(moduleXml).contains("<resource name=\"${project.group}:${project.name}-web1.xml\" version=\"1.0.0\" scope=\"module\" mode=\"isolated\">web1.xml</resource>")

		withFsmFile { ZipFile fsm ->
			assertThat(fsm.getEntry("web.xml")).isNotNull()
			assertThat(fsm.getEntry("web0.xml")).isNotNull()
			assertThat(fsm.getEntry("web1.xml")).isNotNull()
		}

	}

    private String moduleXml(boolean isolated = false) {
		String isolationMode = isolated ? "-isolated" : ""
        final Path fsmFile = testDir.toPath().resolve("build").resolve("fsm").resolve(fsm.archiveFile.get().asFile.name)
        assertThat(fsmFile).exists()
        final ZipFile zipFile = new ZipFile(fsmFile.toFile())
        zipFile.withCloseable {
            String xmlFileName = "META-INF/module" + isolationMode + ".xml"
            ZipEntry moduleXmlEntry = zipFile.getEntry(xmlFileName)
            assertThat(moduleXmlEntry).isNotNull()
            zipFile.getInputStream(moduleXmlEntry).withCloseable {
                return IOUtils.toString(it, StandardCharsets.UTF_8)
            }
        }
    }

	private void withFsmFile(Closure closure) {
		final Path fsmFile = testDir.toPath().resolve("build").resolve("fsm").resolve(fsm.archiveFile.get().asFile.name)
		assertThat(fsmFile).exists()
		final ZipFile zipFile = new ZipFile(fsmFile.toFile())
		zipFile.withCloseable {
			closure(zipFile)
		}
	}
}
