package org.gradle.plugins.fsm.tasks.bundling

import org.apache.commons.io.IOUtils
import org.gradle.api.Project
import org.gradle.plugins.fsm.FSMPlugin
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

import static org.assertj.core.api.Assertions.assertThat
import static org.gradle.plugins.fsm.util.TestProjectUtils.defineArtifactoryForProject
import static org.gradle.plugins.fsm.util.TestProjectUtils.setArtifactoryCredentialsFromLocalProperties

class FSMTest_Resources {

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
    @Disabled("DEVEX-497 - Race Condition When Downloading Dependency Twice")
    void testResourceWithTransitiveDependency_moduleScoped() {
        // test transitive dependency, ensure it ends up in FSM as well

        // has com.google.j2objc:j2objc-annotations:1.1
        project.dependencies.add("fsModuleCompile", "com.google.guava:guava:24.0-jre")

        fsm.execute()
        def xml = moduleXml()
        assertThat(xml).contains('<resource name="com.google.j2objc:j2objc-annotations" scope="module" mode="isolated" version="1.1" minVersion="1.1">lib/j2objc-annotations-1.1.jar</resource>')
        withFsmFile { ZipFile fsm ->
            assertThat(fsm.getEntry('lib/j2objc-annotations-1.1.jar')).isNotNull()
        }
    }

    @Test
    @Disabled("DEVEX-497 - Race Condition When Downloading Dependency Twice")
    void testResourceWithTransitiveDependency_serverScoped() {
        // has com.google.j2objc:j2objc-annotations:1.1
        project.dependencies.add("fsServerCompile", "com.google.guava:guava:24.0-jre")

        fsm.execute()
        def xml = moduleXml()
        assertThat(xml).contains('<resource name="com.google.j2objc:j2objc-annotations" scope="server" mode="isolated" version="1.1" minVersion="1.1">lib/j2objc-annotations-1.1.jar</resource>')
        withFsmFile { ZipFile fsm ->
            assertThat(fsm.getEntry('lib/j2objc-annotations-1.1.jar')).isNotNull()
        }
    }

    @Test
    void testTransitiveDependencyVersionConflict_moduleLocalLtServer() {
        // If resource A has a transitive resource to resource B and we have a direct dependency to B as well,
        // there may be version conflicts. While the correct jar is packed into the FSM, we need to ensure
        // the entry in the module-isolated.xml is valid as well

        // A. transitive dependency server scoped, direct dependency module scoped, transitive version > direct version
        project.dependencies.add("fsServerCompile", "com.google.guava:guava:24.0-jre")
        project.dependencies.add("fsModuleCompile", "com.google.j2objc:j2objc-annotations:0.9.8")

        fsm.execute()
        def xml = moduleXml()
        assertThat(xml).contains('<resource name="com.google.j2objc:j2objc-annotations" scope="server" mode="isolated" version="1.1" minVersion="1.1">lib/j2objc-annotations-1.1.jar</resource>')
        withFsmFile { ZipFile fsm ->
            assertThat(fsm.getEntry('lib/j2objc-annotations-1.1.jar')).isNotNull()
            assertThat(fsm.getEntry('lib/j2objc-annotations-0.9.8.jar')).isNull()
        }
    }

    @Test
    void testTransitiveDependencyVersionConflict_moduleLocalGtServer() {
        // B transitive dependency server scoped, direct dependency module scoped, direct version > transitive version
        project.dependencies.add("fsServerCompile", "com.google.guava:guava:24.0-jre")
        project.dependencies.add("fsModuleCompile", "com.google.j2objc:j2objc-annotations:1.3")

        fsm.execute()
        def xml = moduleXml()
        assertThat(xml).contains('<resource name="com.google.j2objc:j2objc-annotations" scope="server" mode="isolated" version="1.3" minVersion="1.3">lib/j2objc-annotations-1.3.jar</resource>')
        withFsmFile { ZipFile fsm ->
            assertThat(fsm.getEntry('lib/j2objc-annotations-1.3.jar')).isNotNull()
            assertThat(fsm.getEntry('lib/j2objc-annotations-1.1.jar')).isNull()
        }
    }

    @Test
    void testTransitiveDependencyVersionConflict_serverLocalLtModule() {
        // C. transitive dependency module scoped, direct dependency server scoped, transitive version > direct version
        project.dependencies.add("fsModuleCompile", "com.google.guava:guava:24.0-jre")
        project.dependencies.add("fsServerCompile", "com.google.j2objc:j2objc-annotations:0.9.8")

        fsm.execute()
        def xml = moduleXml()
        assertThat(xml).contains('<resource name="com.google.j2objc:j2objc-annotations" scope="server" mode="isolated" version="1.1" minVersion="1.1">lib/j2objc-annotations-1.1.jar</resource>')
        withFsmFile { ZipFile fsm ->
            assertThat(fsm.getEntry('lib/j2objc-annotations-1.1.jar')).isNotNull()
            assertThat(fsm.getEntry('lib/j2objc-annotations-0.9.8.jar')).isNull()
        }
    }

    @Test
    void testTransitiveDependencyVersionConflict_serverLocalGtModule() {
        // D. transitive dependency module scoped, direct dependency server scoped, transitive version < direct version
        project.dependencies.add("fsModuleCompile", "com.google.guava:guava:24.0-jre")
        project.dependencies.add("fsServerCompile", "com.google.j2objc:j2objc-annotations:1.3")

        fsm.execute()
        def xml = moduleXml()
        assertThat(xml).contains('<resource name="com.google.j2objc:j2objc-annotations" scope="server" mode="isolated" version="1.3" minVersion="1.3">lib/j2objc-annotations-1.3.jar</resource>')
        withFsmFile { ZipFile fsm ->
            assertThat(fsm.getEntry('lib/j2objc-annotations-1.3.jar')).isNotNull()
            assertThat(fsm.getEntry('lib/j2objc-annotations-1.1.jar')).isNull()
        }
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
