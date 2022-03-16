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

import org.apache.commons.io.IOUtils
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.jvm.tasks.Jar
import org.gradle.plugins.fsm.FSMPlugin
import org.gradle.plugins.fsm.FSMPluginExtension
import org.gradle.plugins.fsm.configurations.FSMConfigurationsPlugin
import org.gradle.plugins.fsm.configurations.FSMConfigurationsPluginKt
import org.gradle.plugins.fsm.configurations.MinMaxVersion
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

import static org.assertj.core.api.Assertions.*
import static org.gradle.plugins.fsm.util.TestProjectUtils.defineArtifactoryForProject
import static org.gradle.plugins.fsm.util.TestProjectUtils.setArtifactoryCredentialsFromLocalProperties

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
		assertThat(project.tasks[verb + object]).isNotNull()
	}

	@Test
	void jarBaseNameIsUsed() {
		project.version = '0.0.1-SNAPSHOT'
		project.jar {
			baseName = 'xxxxx'
		}
		copyTestJar()
		fsm.execute()

		assertThat(moduleXml())
			.as("module-isolated.xml should contain a 'global' resource with the correctly named jar task output in module scope!")
			.contains("""<resource name="${project.group}:${project.name}" version="0.0.1-SNAPSHOT" scope="module" mode="isolated">lib/xxxxx-0.0.1-SNAPSHOT.jar</resource>""")
		assertThat(moduleXml())
			.as("module-isolated.xml should contain a web resource with the correctly named jar task output!")
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
		pluginExtension.moduleDirName = getClass().getClassLoader().getResource("module-isolated.xml").path

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

        assertThat(moduleXml()).contains("""<custom-tag>custom-legacy</custom-tag>""")
    }

    @Test
    void moduleDirContainsOnlyModuleIsolatedXML() {
        FSMPluginExtension pluginExtension = project.extensions.getByType(FSMPluginExtension.class)
        pluginExtension.moduleDirName = getClass().getClassLoader().getResource("isolatedonly").path

        fsm.execute()

        assertThat(moduleXml()).contains("""<custom-tag>custom</custom-tag>""")
    }

	@Test
	void moduleDirContainsBothXML() {
		FSMPluginExtension pluginExtension = project.extensions.getByType(FSMPluginExtension.class)
		pluginExtension.moduleDirName = getClass().getClassLoader().getResource("bothxml").path

		assertThatThrownBy(() -> fsm.execute())
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("legacy modules are no longer supported")
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
	void resourceHasMinAndMaxVersionWhenConfigured() {
		project.repositories.add(project.getRepositories().mavenCentral())
		use (FSMConfigurationsPluginKt) {
			project.dependencies.add("fsModuleCompile", project.fsDependency("com.google.guava:guava:24.0-jre", "0.0.1", "99.0.0"))
		}

		def dependencyConfigurations = project.plugins.getPlugin(FSMConfigurationsPlugin.class).getDependencyConfigurations()
		assertThat(dependencyConfigurations).contains(new MinMaxVersion("com.google.guava:guava:24.0-jre", "0.0.1", "99.0.0"))
		assertThat(dependencyConfigurations.size()).isEqualTo(1)
		fsm.execute()

		assertThat(moduleXml()).contains("""<resource name="com.google.guava:guava" scope="module" mode="isolated" version="24.0-jre" minVersion="0.0.1" maxVersion="99.0.0">lib/guava-24.0-jre.jar</resource>""")
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
	void testFsmResourceFilesAreUsedForEachWebApp() {
		project.version = "1.0.0"

		copyTestJar()

		// Create dummy test projects
		def webProjectAFile = testDir.toPath().resolve("web_a").toFile()
		webProjectAFile.mkdirs()
		def webProjectA = ProjectBuilder.builder().withName("web_a").withProjectDir(webProjectAFile).withParent(project).build()
		webProjectA.plugins.apply("java")
		setArtifactoryCredentialsFromLocalProperties(webProjectA)
		defineArtifactoryForProject(webProjectA)

		// add resources
		// web_a
		// +-+ fsm-resources
		//   +-- 1.txt
		//   +-- 2.txt
		def webAResources = Files.createDirectories(webProjectAFile.toPath().resolve(FSM.FSM_RESOURCES_PATH))
		webAResources.resolve("1.txt").toFile() << "1.txt"
		webAResources.resolve("2.txt").toFile() << "2.txt"

		def webProjectBFile = testDir.toPath().resolve("web_b").toFile()
		webProjectBFile.mkdirs()
		def webProjectB = ProjectBuilder.builder().withName("web_b").withProjectDir(webProjectBFile).withParent(project).build()
		webProjectB.plugins.apply("java")
		setArtifactoryCredentialsFromLocalProperties(webProjectB)
		defineArtifactoryForProject(webProjectB)

		// add resources
		// web_b
		// +-+ fsm-resources
		//   +-- a.txt
		//   +-+ nested1
		//     +-+ nested2
		//       +-- b.txt
		def webBResources = Files.createDirectories(webProjectBFile.toPath().resolve(FSM.FSM_RESOURCES_PATH))
		webBResources.resolve("a.txt").toFile() << "a.txt"
		def nested = Files.createDirectories(webBResources.resolve("nested1").resolve("nested2"))
		nested.resolve("b.txt") << "b.txt"

		def rootResources = Files.createDirectories(project.projectDir.toPath().resolve(FSM.FSM_RESOURCES_PATH))
		rootResources.resolve("0.txt") << "0.txt"

		// add web projects as web-apps
		def fsmPluginExtension = project.extensions.getByType(FSMPluginExtension)
		fsmPluginExtension.webAppComponent("TestWebAppA", webProjectA)
		fsmPluginExtension.webAppComponent("TestWebAppB", webProjectB)

		fsm.execute()

		// Test resource entries
		withFsmFile { ZipFile fsm ->
			assertThat(fsm.getEntry("0.txt")).isNotNull()
			assertThat(fsm.getEntry("1.txt")).isNotNull()
			assertThat(fsm.getEntry("2.txt")).isNotNull()
			assertThat(fsm.getEntry("a.txt")).isNotNull()
			assertThat(fsm.getEntry("nested1/nested2/b.txt")).isNotNull()
		}
	}

	@Test
	void testFsmResourceFilesDetectDuplicates() {
		project.version = "1.0.0"

		// Create dummy test projects
		def subprojectAFile = testDir.toPath().resolve("a").toFile()
		subprojectAFile.mkdirs()
		def subprojectA = ProjectBuilder.builder().withName("a").withProjectDir(subprojectAFile).withParent(project).build()
		subprojectA.plugins.apply("java")
		setArtifactoryCredentialsFromLocalProperties(subprojectA)
		defineArtifactoryForProject(subprojectA)

		// add resources
		// a
		// +-+ fsm-resources
		//   +-+ nested
		//   | +-- n1.txt    <-- duplicate
		//   | +-- n2.txt
		//   +-- 1.txt       <-- duplicate
		//   +-- 2.txt
		def subprojectAResources = Files.createDirectories(subprojectAFile.toPath().resolve(FSM.FSM_RESOURCES_PATH))
		subprojectAResources.resolve("1.txt").toFile() << "1.txt"
		subprojectAResources.resolve("2.txt").toFile() << "2.txt"
		def nestedA = Files.createDirectories(subprojectAResources.resolve("nested"))
		nestedA.resolve("n1.txt").toFile() << "n1.txt"
		nestedA.resolve("n2.txt").toFile() << "n2.txt"

		def subprojectBFile = testDir.toPath().resolve("b").toFile()
		subprojectBFile.mkdirs()
		def subprojectB = ProjectBuilder.builder().withName("b").withProjectDir(subprojectBFile).withParent(project).build()
		subprojectB.plugins.apply("java")
		setArtifactoryCredentialsFromLocalProperties(subprojectB)
		defineArtifactoryForProject(subprojectB)

		// add resources
		// b
		// +-+ fsm-resources
		//   +-+ nested
		//   | +-- n1.txt    <-- duplicate
		//   | +-- n3.txt
		//   +-- 1.txt       <-- duplicate
		//   +-- 3.txt
		def subprojectBResources = Files.createDirectories(subprojectBFile.toPath().resolve(FSM.FSM_RESOURCES_PATH))
		subprojectBResources.resolve("1.txt").toFile() << "1.txt"
		subprojectBResources.resolve("3.txt").toFile() << "3.txt"
		def nestedB = Files.createDirectories(subprojectBResources.resolve("nested"))
		nestedB.resolve("n1.txt").toFile() << "n1.txt"
		nestedB.resolve("n3.txt").toFile() << "n3.txt"

		fsm.execute()

		// The following files should be detected as duplicates. They will overwrite each other in the FSM archive
		assertThat(fsm.duplicateFsmResourceFiles).containsExactlyInAnyOrder(
				"1.txt",
				"nested/n1.txt"
		)
	}

	@Test
	void testWebXmlResourcesAreExcludedFromResources() {
		/**
		 * web.xml and web0.xml files are used by our test webcomponent implementations.
		 * Those should be excluded from the resources tags in the module-isolated.xml, because
		 * they aren't regular fsm resources.
		 */
		project.version = "1.0.0"

		copyTestJar()

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

	private void copyTestJar() {
		def testJar = Paths.get(System.getProperty("testJar"))
		def jar = project.tasks[JavaPlugin.JAR_TASK_NAME] as Jar
		def archiveFile = jar.archiveFile.get().asFile.toPath()
		Files.createDirectories(archiveFile.parent)
		Files.copy(testJar, archiveFile)
	}


	private String moduleXml() {
        final Path fsmFile = testDir.toPath().resolve("build").resolve("fsm").resolve(fsm.archiveFile.get().asFile.name)
        assertThat(fsmFile).exists()
        final ZipFile zipFile = new ZipFile(fsmFile.toFile())
        zipFile.withCloseable {
            String xmlFileName = "META-INF/module-isolated.xml"
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
