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
import org.junit.Rule
import org.junit.rules.TemporaryFolder

import java.nio.charset.StandardCharsets
import java.nio.file.Path

import org.gradle.api.Project
import org.gradle.plugins.fsm.FSMPlugin
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Test

import java.util.zip.ZipEntry
import java.util.zip.ZipFile

import static org.assertj.core.api.Assertions.assertThat

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
		
		fsm = project.tasks[FSMPlugin.FSM_TASK_NAME]
		
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
	void fsmExtensionUsed() {
		assertThat(fsm.extension).isEqualTo(FSM.FSM_EXTENSION)
	}
	
	@Test
	void archivePathUsed() {
		assertThat(fsm.archivePath.toPath()).isEqualTo(project.buildDir.toPath().resolve("fsm").resolve(fsm.archiveName))
	}

    @Test
    void useGlobalClassloaderIsolationMode() {
        project.repositories.add(project.getRepositories().mavenCentral())
        project.dependencies.add("fsModuleCompile", "com.google.guava:guava:24.0-jre")
        fsm.resourceMode = 'isolated'

        fsm.execute()

        final Path fsmFile = testDir.toPath().resolve("build").resolve("fsm").resolve(fsm.archiveName)
        assertThat(fsmFile).exists()
        final ZipFile zipFile = new ZipFile(fsmFile.toFile())
        zipFile.withCloseable {
            ZipEntry moduleXmlEntry = zipFile.getEntry("META-INF/module.xml")
            assertThat(moduleXmlEntry).isNotNull()
            zipFile.getInputStream(moduleXmlEntry).withCloseable {
                final String moduleXml = IOUtils.toString(it, StandardCharsets.UTF_8)
                assertThat(moduleXml).contains("""<resource name="com.google.guava.guava" scope="module" mode="isolated" version="24.0-jre">lib/guava-24.0-jre.jar</resource>""")
            }
        }
    }
}
