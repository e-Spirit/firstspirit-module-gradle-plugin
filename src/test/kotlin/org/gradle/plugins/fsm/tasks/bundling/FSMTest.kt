package org.gradle.plugins.fsm.tasks.bundling

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.jvm.tasks.Jar
import org.gradle.plugins.fsm.FSMPlugin
import org.gradle.plugins.fsm.FSMPluginExtension
import org.gradle.plugins.fsm.configurations.FSMConfigurationsPlugin
import org.gradle.plugins.fsm.configurations.FSMConfigurationsPlugin.Companion.FS_MODULE_COMPILE_CONFIGURATION_NAME
import org.gradle.plugins.fsm.configurations.FSMConfigurationsPlugin.Companion.FS_SERVER_COMPILE_CONFIGURATION_NAME
import org.gradle.plugins.fsm.configurations.MinMaxVersion
import org.gradle.plugins.fsm.configurations.fsDependency
import org.gradle.plugins.fsm.util.TestProjectUtils.defineArtifactoryForProject
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.io.path.writeText

class FSMTest {

    @TempDir
    private lateinit var testDir: File

    private lateinit var project: Project

    private lateinit var fsm: FSM

    @BeforeEach
    fun setUp() {
        project = ProjectBuilder.builder().withProjectDir(testDir).build()
        defineArtifactoryForProject(project)

        project.plugins.apply(FSMPlugin.NAME)

        fsm = project.tasks.getByName(FSMPlugin.FSM_TASK_NAME) as FSM

        fsm.archiveBaseName.set("testbasename")
        fsm.archiveAppendix.set("testappendix")
        fsm.archiveVersion.set("1.0")
    }

    @Test
    fun testExecute() {
        fsm.execute()
        assertThat(fsm.destinationDirectory.get().asFile).isDirectory
        assertThat(fsm.archiveFile.get().asFile).isFile
    }

    @Test
    fun fsmExtensionUsed() {
        assertThat(fsm.archiveExtension.get()).isEqualTo(FSM.FSM_EXTENSION)
    }


    @Test
    fun theTaskNameShouldBeComposedByAVerbAndAnObject() {
        val verb = "assemble"
        val obj = "FSM"
        assertThat(project.tasks.getByName(verb + obj)).isNotNull
    }

    @Test
    fun jarBaseNameIsUsed() {
        project.version = "0.0.1-SNAPSHOT"
        with(project.tasks.getByName("jar") as Jar) {
            archiveBaseName.set("xxxxx")
        }
        copyTestJar()
        fsm.execute()

        assertThat(moduleXml())
            .describedAs("module-isolated.xml should contain a 'global' resource with the correctly named jar task output in module scope!")
            .contains("""<resource mode="isolated" name="${project.group}:${project.name}" scope="module" version="0.0.1-SNAPSHOT">lib/xxxxx-0.0.1-SNAPSHOT.jar</resource>""")
        assertThat(moduleXml())
            .describedAs("module-isolated.xml should contain a web resource with the correctly named jar task output!")
            .contains("""<resource name="${project.group}:${project.name}" version="0.0.1-SNAPSHOT">lib/xxxxx-0.0.1-SNAPSHOT.jar</resource>""")
    }

    @Test
    fun archivePathUsed() {
        assertThat(fsm.archiveFile.get().asFile).isEqualTo(project.layout.buildDirectory.dir("fsm").get().asFile
            .resolve(fsm.archiveFile.get().asFile.name))
    }

    @Test
    fun displayNameUsed() {
        val displayName = "Human-Readable Display Name"
        val pluginExtension = project.extensions.getByType(FSMPluginExtension::class.java)
        pluginExtension.displayName = displayName

        fsm.execute()

        assertThat(moduleXml()).contains("""<displayname>${displayName}</displayname>""")
    }

    @Test
    fun moduleDirNameUsed() {
        val pluginExtension = project.extensions.getByType(FSMPluginExtension::class.java)
        pluginExtension.moduleDirName = this::class.java.classLoader.getResource("module-isolated.xml")?.path

        fsm.execute()

        assertThat(moduleXml()).contains("""<custom-tag>custom</custom-tag>""")
    }

    @Test
    fun `variables should be replaced in custom descriptor`() {
        val pluginExtension = project.extensions.getByType(FSMPluginExtension::class.java)
        project.version = "1.4.3"
        project.description = "Module for variable replacement test"
        pluginExtension.vendor = "Crownpeak Technology GmbH"
        pluginExtension.minimalFirstSpiritVersion = "5.2.230909"
        pluginExtension.moduleDirName = this::class.java.classLoader.getResource("module-isolated.xml")?.path

        fsm.execute()

        assertThat(moduleXml()).containsIgnoringNewLines("""
            <name>${project.name}</name>
            <displayname>test</displayname>
            <version>${project.version}</version>
            <min-fs-version>${pluginExtension.minimalFirstSpiritVersion}</min-fs-version>
            <description>${project.description}</description>
            <vendor>${pluginExtension.vendor}</vendor>
        """.replaceIndent("\t"))
            .contains("<resource>test-${project.version}.jar</resource>")
    }

    @Test
    fun moduleDirContainsNoRelevantFiles() {
        val pluginExtension = project.extensions.getByType(FSMPluginExtension::class.java)
        pluginExtension.moduleDirName = "some/empty/dir"

        assertThatThrownBy { fsm.execute() }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("No module.xml or module-isolated.xml found in moduleDir some/empty/dir")
    }

    @Test
    fun moduleDirContainsOnlyModuleXML() {
        val pluginExtension = project.extensions.getByType(FSMPluginExtension::class.java)
        pluginExtension.moduleDirName = this::class.java.classLoader.getResource("legacyonly")?.path

        fsm.execute()

        assertThat(moduleXml()).contains("""<custom-tag>custom-legacy</custom-tag>""")
    }

    @Test
    fun moduleDirContainsOnlyModuleIsolatedXML() {
        val pluginExtension = project.extensions.getByType(FSMPluginExtension::class.java)
        pluginExtension.moduleDirName = this::class.java.classLoader.getResource("isolatedonly")?.path

        fsm.execute()

        assertThat(moduleXml()).contains("""<custom-tag>custom</custom-tag>""")
    }

    @Test
    fun moduleDirContainsBothXML() {
        val pluginExtension = project.extensions.getByType(FSMPluginExtension::class.java)
        pluginExtension.moduleDirName = this::class.java.classLoader.getResource("bothxml")?.path

        assertThatThrownBy { fsm.execute() }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("legacy modules are no longer supported")
    }

    @Test
    fun trimRemovesFileNameFromPath() {
        val testPath = "some/directory/containing/a.file"
        val trimmedPath = fsm.trimPathToDirectory(testPath)

        assertThat(trimmedPath).isEqualTo("some/directory/containing")
    }

    @Test
    fun trimIgnoresDotsInDirectoryNames() {
        val testPath = "a/directory/containing/a.dot/in/a/folder/name"
        val trimmedPath = fsm.trimPathToDirectory(testPath)

        assertThat(trimmedPath).isEqualTo("a/directory/containing/a.dot/in/a/folder/name")
    }

    @Test
    fun vendor() {
        val pluginExtension = project.extensions.getByType(FSMPluginExtension::class.java)
        val vendor = "ACME"
        pluginExtension.vendor = vendor

        fsm.execute()

        assertThat(moduleXml()).contains("""<vendor>${vendor}</vendor>""")
    }

    @Test
    fun resourceIsConfiguredAsIsolatedByDefault() {
        project.repositories.add(project.repositories.mavenCentral())
        project.dependencies.add("fsModuleCompile", "com.google.guava:guava:24.0-jre")

        fsm.execute()

        assertThat(moduleXml()).contains("""<resource minVersion="24.0-jre" mode="isolated" name="com.google.guava:guava" scope="module" version="24.0-jre">lib/guava-24.0-jre.jar</resource>""")
    }

    @Test
    fun resourceHasMinAndMaxVersionWhenConfigured() {
        project.repositories.add(project.repositories.mavenCentral())
        project.dependencies.add("fsModuleCompile", project.fsDependency("com.google.guava:guava:24.0-jre", "0.0.1", "99.0.0"))

        val dependencyConfigurations = project.plugins.getPlugin(FSMConfigurationsPlugin::class.java).getDependencyConfigurations()
        assertThat(dependencyConfigurations).containsExactly(MinMaxVersion("com.google.guava:guava:24.0-jre", "0.0.1", "99.0.0"))
        fsm.execute()

        assertThat(moduleXml()).contains("""<resource maxVersion="99.0.0" minVersion="0.0.1" mode="isolated" name="com.google.guava:guava" scope="module" version="24.0-jre">lib/guava-24.0-jre.jar</resource>""")
    }

    @Test
    fun testLicensesCsvIsPresent() {
        fsm.execute()
        assertThat(moduleXml()).contains("<licenses>META-INF/licenses.csv</licenses>")
    }


    @Test
    fun testFsmResourceFilesAreUsed() {
        project.version = "1.0.0"
        val fsmResourcesProjectFolder = fsm.project.file("src/main/fsm-resources").toPath()
        val fsmResourcesNestedProjectFolder = fsm.project.file("src/main/fsm-resources/resourcesFolder").toPath()
        Files.createDirectories(fsmResourcesNestedProjectFolder)
        fsmResourcesProjectFolder.resolve("testResource.txt").writeText("Test")
        fsmResourcesNestedProjectFolder.resolve("testNested0.txt").writeText("Test")
        fsmResourcesNestedProjectFolder.resolve("testNested1.txt").writeText("Test")

        fsm.execute()

        assertThat(moduleXml()).contains("""<resource mode="isolated" name="${project.group}:${project.name}-testResource.txt" scope="module" version="1.0.0">testResource.txt</resource>""")
        assertThat(moduleXml()).contains("""<resource mode="isolated" name="${project.group}:${project.name}-resourcesFolder" scope="module" version="1.0.0">resourcesFolder</resource>""")

        withFsmFile { fsm ->
            assertThat(fsm.getEntry("testResource.txt")).isNotNull
            assertThat(fsm.getEntry("resourcesFolder/testNested0.txt")).isNotNull
            assertThat(fsm.getEntry("resourcesFolder/testNested1.txt")).isNotNull
        }
    }



    @Test
    fun testFsmResourceFilesAreUsedForEachWebApp() {
        project.version = "1.0.0"

        copyTestJar()

        // Create dummy test projects
        val webProjectAFile = testDir.resolve("web_a")
        webProjectAFile.mkdirs()
        val webProjectA = ProjectBuilder.builder().withName("web_a").withProjectDir(webProjectAFile).withParent(project).build()
        webProjectA.plugins.apply("java")
        defineArtifactoryForProject(webProjectA)

        // add resources
        // web_a
        // +-+ fsm-resources
        //   +-- 1.txt
        //   +-- 2.txt
        val webAResources = Files.createDirectories(webProjectAFile.toPath().resolve(FSM.FSM_RESOURCES_PATH))
        webAResources.resolve("1.txt").writeText("1.txt")
        webAResources.resolve("2.txt").writeText("2.txt")

        val webProjectBFile = testDir.resolve("web_b")
        webProjectBFile.mkdirs()
        val webProjectB = ProjectBuilder.builder().withName("web_b").withProjectDir(webProjectBFile).withParent(project).build()
        webProjectB.plugins.apply("java")
        defineArtifactoryForProject(webProjectB)

        // add resources
        // web_b
        // +-+ fsm-resources
        //   +-- a.txt
        //   +-+ nested1
        //     +-+ nested2
        //       +-- b.txt
        val webBResources = Files.createDirectories(webProjectBFile.toPath().resolve(FSM.FSM_RESOURCES_PATH))
        webBResources.resolve("a.txt").writeText("a.txt")
        val nested = Files.createDirectories(webBResources.resolve("nested1").resolve("nested2"))
        nested.resolve("b.txt").writeText("b.txt")

        val rootResources = Files.createDirectories(project.projectDir.toPath().resolve(FSM.FSM_RESOURCES_PATH))
        rootResources.resolve("0.txt").writeText("0.txt")

        // add web projects as web-apps
        val fsmPluginExtension = project.extensions.getByType(FSMPluginExtension::class.java)
        fsmPluginExtension.webAppComponent("TestWebAppA", webProjectA)
        fsmPluginExtension.webAppComponent("TestWebAppB", webProjectB)

        fsm.execute()

        // Test resource entries
        withFsmFile { fsm ->
            assertThat(fsm.getEntry("0.txt")).isNotNull
            assertThat(fsm.getEntry("1.txt")).isNotNull
            assertThat(fsm.getEntry("2.txt")).isNotNull
            assertThat(fsm.getEntry("a.txt")).isNotNull
            assertThat(fsm.getEntry("nested1/nested2/b.txt")).isNotNull
        }
    }

    @Test
    fun `web-app component libraries should be included`() {
        project.version = "1.0.0"

        copyTestJar()

        val libProjectDir = testDir.resolve("lib")
        libProjectDir.mkdirs()
        val libProject = ProjectBuilder.builder().withName("lib").withProjectDir(libProjectDir).withParent(project).build()
        libProject.plugins.apply("java")
        val libBuildDir = libProjectDir.resolve("build/libs/")
        libBuildDir.mkdirs()
        libBuildDir.resolve("lib.jar").createNewFile()

        val webProjectDir = testDir.resolve("web")
        webProjectDir.mkdirs()
        val webProject = ProjectBuilder.builder().withName("web").withProjectDir(webProjectDir).withParent(project).build()
        webProject.plugins.apply("java")
        webProject.dependencies.add("implementation", project.dependencies.project(mapOf("path" to ":lib")))

        val fsmPluginExtension = project.extensions.getByType(FSMPluginExtension::class.java)
        fsmPluginExtension.webAppComponent("TestWebAppA", webProject)

        fsm.execute()

        assertThat(moduleXml()).contains("""<resource minVersion="unspecified" name="test:lib" version="unspecified">lib/lib.jar</resource>""")

        withFsmFile { fsm ->
            assertThat(fsm.getEntry("lib/lib.jar")).isNotNull
        }
    }

    @Test
    fun `web-app component jar output should be included`() {
        project.version = "1.0.0"

        copyTestJar()

        val webProjectDir = testDir.resolve("web")
        webProjectDir.mkdirs()
        val webProject = ProjectBuilder.builder().withName("web").withProjectDir(webProjectDir).withParent(project).build()
        webProject.plugins.apply("java")
        val jarTask = webProject.tasks.getByName(JavaPlugin.JAR_TASK_NAME)
        val jarFile = jarTask.outputs.files.toList().single()
        Files.createDirectories(jarFile.toPath().parent)
        ZipOutputStream(jarFile.outputStream()).use {
            it.putNextEntry(ZipEntry("test.txt"))
            it.closeEntry()
        }

        val fsmPluginExtension = project.extensions.getByType(FSMPluginExtension::class.java)
        fsmPluginExtension.webAppComponent("TestWebAppA", webProject)

        fsm.execute()

        assertThat(moduleXml()).contains("""<resource name="test:web" version="unspecified">lib/web.jar</resource>""")

        withFsmFile { fsm ->
            assertThat(fsm.getEntry("lib/web.jar")).isNotNull
        }
    }

    @Test
    fun testFsmResourceFilesDetectDuplicates() {
        project.version = "1.0.0"

        // Create dummy test projects
        val subprojectAFile = testDir.resolve("a")
        subprojectAFile.mkdirs()
        val subprojectA = ProjectBuilder.builder().withName("a").withProjectDir(subprojectAFile).withParent(project).build()
        subprojectA.plugins.apply("java")
        project.dependencies.add(FS_MODULE_COMPILE_CONFIGURATION_NAME, subprojectA)
        defineArtifactoryForProject(subprojectA)

        // add resources
        // a
        // +-+ fsm-resources
        //   +-+ nested
        //   | +-- n1.txt    <-- duplicate
        //   | +-- n2.txt
        //   +-- 1.txt       <-- duplicate
        //   +-- 2.txt
        val subprojectAResources = Files.createDirectories(subprojectAFile.toPath().resolve(FSM.FSM_RESOURCES_PATH))
        subprojectAResources.resolve("1.txt").writeText("1.txt")
        subprojectAResources.resolve("2.txt").writeText("2.txt")
        val nestedA = Files.createDirectories(subprojectAResources.resolve("nested"))
        nestedA.resolve("n1.txt").writeText("n1.txt")
        nestedA.resolve("n2.txt").writeText("n2.txt")

        val subprojectBFile = testDir.resolve("b")
        subprojectBFile.mkdirs()
        val subprojectB = ProjectBuilder.builder().withName("b").withProjectDir(subprojectBFile).withParent(project).build()
        subprojectB.plugins.apply("java")
        project.dependencies.add(FS_MODULE_COMPILE_CONFIGURATION_NAME, subprojectB)
        defineArtifactoryForProject(subprojectB)

        // add resources
        // b
        // +-+ fsm-resources
        //   +-+ nested
        //   | +-- n1.txt    <-- duplicate
        //   | +-- n3.txt
        //   +-- 1.txt       <-- duplicate
        //   +-- 3.txt
        val subprojectBResources = Files.createDirectories(subprojectBFile.toPath().resolve(FSM.FSM_RESOURCES_PATH))
        subprojectBResources.resolve("1.txt").writeText("1.txt")
        subprojectBResources.resolve("3.txt").writeText("3.txt")
        val nestedB = Files.createDirectories(subprojectBResources.resolve("nested"))
        nestedB.resolve("n1.txt").writeText("n1.txt")
        nestedB.resolve("n3.txt").writeText("n3.txt")

        fsm.execute()

        // The following files should be detected as duplicates. They will overwrite each other in the FSM archive
        assertThat(fsm.duplicateFsmResourceFiles).containsExactlyInAnyOrder(
            File("1.txt"),
            File("nested/n1.txt")
        )
    }

    @Test
    fun testWebXmlResourcesAreExcludedFromResources() {
        /**
         * web.xml and web0.xml files are used by our test webcomponent implementations.
         * Those should be excluded from the resources tags in the module-isolated.xml, because
         * they aren't regular fsm resources.
         */
        project.version = "1.0.0"

        copyTestJar()

        val fsmResourcesTestProjectFolder = fsm.project.file("src/main/fsm-resources").toPath()
        Files.createDirectories(fsmResourcesTestProjectFolder)
        fsmResourcesTestProjectFolder.resolve("web.xml").writeText("<xml></xml>")
        fsmResourcesTestProjectFolder.resolve("web0.xml").writeText("<xml></xml>")
        fsmResourcesTestProjectFolder.resolve("web1.xml").writeText("<xml></xml>")

        fsm.execute()

        val moduleXml = moduleXml()
        assertThat(moduleXml).doesNotContain("""<resource mode="isolated" name="${project.group}:${project.name}-web.xml" scope="module" version="1.0.0">web.xml</resource>""")
        assertThat(moduleXml).doesNotContain("""<resource mode="isolated" name="${project.group}:${project.name}-web0.xml" scope="module" version="1.0.0">web0.xml</resource>""")
        assertThat(moduleXml).contains("""<resource mode="isolated" name="${project.group}:${project.name}-web1.xml" scope="module" version="1.0.0">web1.xml</resource>""")

        withFsmFile { fsm ->
            assertThat(fsm.getEntry("web.xml")).isNotNull
            assertThat(fsm.getEntry("web0.xml")).isNotNull
            assertThat(fsm.getEntry("web1.xml")).isNotNull
        }

    }

    @Test
    fun `library component`() {
        val pluginExtension = project.extensions.getByType(FSMPluginExtension::class.java)
        val libWithCustomConfiguration = pluginExtension.libraries.create("libWithCustomConfiguration")

        val configuration = project.configurations.create("customLib")
        configuration.dependencies.add(project.dependencies.create("org.slf4j:slf4j-api:2.0.6"))
        libWithCustomConfiguration.configuration = configuration

        fsm.execute()

        val moduleXml = moduleXml()
        assertThat(moduleXml).contains("<library>")
        assertThat(moduleXml).contains("<name>libWithCustomConfiguration</name>")
        assertThat(moduleXml).contains("""<resource minVersion="2.0.6" mode="isolated" name="org.slf4j:slf4j-api" scope="server" version="2.0.6">lib/slf4j-api-2.0.6.jar</resource>""")

        withFsmFile { fsm ->
            assertThat(fsm.getEntry("lib/slf4j-api-2.0.6.jar")).isNotNull
        }
    }


    @Test
    fun `do not include local jar dependencies`(@TempDir tempDir: Path) {
        val localServerJar = Files.createFile(tempDir.resolve("server-lib.jar"))
        val localModuleJar = Files.createFile(tempDir.resolve("module-lib-1.0.jar"))
        val localImplJar = Files.createFile(tempDir.resolve("lib-1.0.jar"))
        project.dependencies.add(FS_SERVER_COMPILE_CONFIGURATION_NAME, project.files(localServerJar))
        project.dependencies.add(FS_MODULE_COMPILE_CONFIGURATION_NAME, project.files(localModuleJar))
        project.dependencies.add(JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME, project.files(localImplJar))

        fsm.execute()

        assertThat(moduleXml()).contains("<resources/>")

        withFsmFile { fsm ->
            assertThat(fsm.getEntry("lib/server-lib.jar")).isNull()
            assertThat(fsm.getEntry("lib/module-lib-1.0.jar")).isNull()
            assertThat(fsm.getEntry("lib/lib-1.0.jar")).isNull()
        }
    }


    private fun copyTestJar() {
        val testJar = Paths.get(System.getProperty("testJar"))
        val jar = project.tasks.getByName(JavaPlugin.JAR_TASK_NAME) as Jar
        val archiveFile = jar.archiveFile.get().asFile.toPath()
        Files.createDirectories(archiveFile.parent)
        Files.copy(testJar, archiveFile)
    }


    private fun moduleXml(): String {
        val fsmFile = testDir.resolve("build").resolve("fsm").resolve(fsm.archiveFile.get().asFile.name)
        assertThat(fsmFile).exists()
        ZipFile(fsmFile).use { zipFile ->
            val xmlFileName = "META-INF/module-isolated.xml"
            val moduleXmlEntry = zipFile.getEntry(xmlFileName)
            assertThat(moduleXmlEntry).isNotNull
            zipFile.getInputStream(moduleXmlEntry).use {
                return it.reader().readText()
            }
        }
    }

    private fun withFsmFile(f: (ZipFile) -> Unit) {
        val fsmFile = testDir.resolve("build").resolve("fsm").resolve(fsm.archiveFile.get().asFile.name)
        assertThat(fsmFile).exists()
        ZipFile(fsmFile).use {
            f.invoke(it)
        }
    }

}