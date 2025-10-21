package org.gradle.plugins.fsm.tasks.bundling

import org.assertj.core.api.Assertions.*
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.TaskProvider
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

    private lateinit var fsm: TaskProvider<FSM>

    @BeforeEach
    fun setUp() {
        project = ProjectBuilder.builder().withProjectDir(testDir).build()
        defineArtifactoryForProject(project)

        project.plugins.apply(FSMPlugin.NAME)

        fsm = project.tasks.named(FSMPlugin.FSM_TASK_NAME, FSM::class.java) {
            archiveBaseName.set("testbasename")
            archiveAppendix.set("testappendix")
            archiveVersion.set("1.0")    
        }
    }

    @Test
    fun execute() {
        fsm.get().execute()
        assertThat(fsm.get().destinationDirectory.get().asFile).isDirectory
        assertThat(fsm.get().archiveFile.get().asFile).isFile
    }

    @Test
    fun `FSM extension used`() {
        assertThat(fsm.get().archiveExtension.get()).isEqualTo(FSM.FSM_EXTENSION)
    }


    @Test
    fun `task name should be composed by a verb and an object`() {
        val verb = "assemble"
        val obj = "FSM"
        assertThat(project.tasks.getByName(verb + obj)).isNotNull
    }

    @Test
    fun `jar base name is used`() {
        project.version = "0.0.1-SNAPSHOT"
        with(project.tasks.getByName("jar") as Jar) {
            archiveBaseName.set("xxxxx")
        }
        copyTestJar()
        fsm.get().execute()

        assertThat(moduleXml())
            .describedAs("module-isolated.xml should contain a 'global' resource with the correctly named jar task output in module scope!")
            .contains("""<resource mode="isolated" name="${project.group}:${project.name}" scope="module" version="0.0.1-SNAPSHOT">lib/xxxxx-0.0.1-SNAPSHOT.jar</resource>""")
        assertThat(moduleXml())
            .describedAs("module-isolated.xml should contain a web resource with the correctly named jar task output!")
            .contains("""<resource name="${project.group}:${project.name}" version="0.0.1-SNAPSHOT">lib/xxxxx-0.0.1-SNAPSHOT.jar</resource>""")
    }

    @Test
    fun `default jar output is module-scoped by default`() {
        project.version = "0.0.1"
        copyTestJar()
        fsm.get().execute()

        assertThat(moduleXml())
            .contains("""<resource mode="isolated" name=":test" scope="module" version="0.0.1">lib/test-0.0.1.jar</resource>""")
    }

    @Test
    fun `use server scope for jar output`() {
        project.version = "0.0.1"
        copyTestJar()

        val pluginExtension = project.extensions.getByType(FSMPluginExtension::class.java)
        pluginExtension.projectJarScope = "server"

        fsm.get().execute()

        assertThat(moduleXml())
            .contains("""<resource mode="isolated" name=":test" scope="server" version="0.0.1">lib/test-0.0.1.jar</resource>""")
    }

    @Test
    fun `invalid scope for jar output`() {
        val pluginExtension = project.extensions.getByType(FSMPluginExtension::class.java)

        assertThatExceptionOfType(IllegalArgumentException::class.java)
            .isThrownBy { pluginExtension.projectJarScope = "invalid" }
            .withMessage("Unknown scope value 'invalid'.")
    }

    @Test
    fun archivePathUsed() {
        assertThat(fsm.get().archiveFile.get().asFile).isEqualTo(project.layout.buildDirectory.dir("fsm").get().asFile
            .resolve(fsm.get().archiveFile.get().asFile.name))
    }

    @Test
    fun displayNameUsed() {
        val displayName = "Human-Readable Display Name"
        val pluginExtension = project.extensions.getByType(FSMPluginExtension::class.java)
        pluginExtension.displayName = displayName

        fsm.get().execute()

        assertThat(moduleXml()).contains("""<displayname>${displayName}</displayname>""")
    }

    @Test
    fun `module dir name used`() {
        val pluginExtension = project.extensions.getByType(FSMPluginExtension::class.java)
        val resource = this::class.java.classLoader.getResource("module-isolated.xml")!!
        pluginExtension.moduleDirName = Paths.get(resource.toURI()).parent.toString()

        fsm.get().execute()

        assertThat(moduleXml()).contains("""<custom-tag>custom</custom-tag>""")
    }

    @Test
    fun `variables should be replaced in custom descriptor`() {
        val pluginExtension = project.extensions.getByType(FSMPluginExtension::class.java)
        project.version = "1.4.3"
        project.description = "Module for variable replacement test"
        pluginExtension.vendor = "Crownpeak Technology GmbH"
        pluginExtension.minimalFirstSpiritVersion = "5.2.230909"
        val resource = this::class.java.classLoader.getResource("module-isolated.xml")!!
        pluginExtension.moduleDirName = Paths.get(resource.toURI()).parent.toString()

        fsm.get().execute()

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
    fun `file specified for moduleDir`() {
        val pluginExtension = project.extensions.getByType(FSMPluginExtension::class.java)
        pluginExtension.moduleDirName = this::class.java.classLoader.getResource("module-isolated.xml")?.path

        assertThatExceptionOfType(GradleException::class.java).isThrownBy { fsm.get().execute() }
    }

    @Test
    fun `module dir contains no relevant files`() {
        val pluginExtension = project.extensions.getByType(FSMPluginExtension::class.java)
        pluginExtension.moduleDirName = "some/empty/dir"
        Files.createDirectories(project.file("some/empty/dir").toPath())

        assertThatThrownBy { fsm.get() }
            .isInstanceOf(GradleException::class.java)
            .rootCause()
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("No module.xml or module-isolated.xml found in moduleDir some/empty/dir")
    }

    @Test
    fun `module dir contains only module-xml`() {
        val pluginExtension = project.extensions.getByType(FSMPluginExtension::class.java)
        pluginExtension.moduleDirName = this::class.java.classLoader.getResource("legacyonly")?.path

        fsm.get().execute()

        assertThat(moduleXml()).contains("""<custom-tag>custom-legacy</custom-tag>""")
    }

    @Test
    fun `module dir contains only module-isolated-xml`() {
        val pluginExtension = project.extensions.getByType(FSMPluginExtension::class.java)
        pluginExtension.moduleDirName = this::class.java.classLoader.getResource("isolatedonly")?.path

        fsm.get().execute()

        assertThat(moduleXml()).contains("""<custom-tag>custom</custom-tag>""")
    }

    @Test
    fun `module dir contains both XML`() {
        val pluginExtension = project.extensions.getByType(FSMPluginExtension::class.java)
        pluginExtension.moduleDirName = this::class.java.classLoader.getResource("bothxml")?.path

        assertThatThrownBy { fsm.get().execute() }
            .isInstanceOf(GradleException::class.java)
            .rootCause()
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("legacy modules are no longer supported")
    }

    @Test
    fun vendor() {
        val pluginExtension = project.extensions.getByType(FSMPluginExtension::class.java)
        val vendor = "ACME"
        pluginExtension.vendor = vendor

        fsm.get().execute()

        assertThat(moduleXml()).contains("""<vendor>${vendor}</vendor>""")
    }

    @Test
    fun `resource is configured as isolated by default`() {
        project.repositories.add(project.repositories.mavenCentral())
        project.dependencies.add("fsModuleCompile", "com.google.guava:guava:24.0-jre")

        fsm.get().execute()

        assertThat(moduleXml()).contains("""<resource minVersion="24.0-jre" mode="isolated" name="com.google.guava:guava" scope="module" version="24.0-jre">lib/guava-24.0-jre.jar</resource>""")
    }

    @Test
    fun `resource has min and max version when configured`() {
        project.repositories.add(project.repositories.mavenCentral())
        project.dependencies.add("fsModuleCompile", project.fsDependency("com.google.guava:guava:24.0-jre", "0.0.1", "99.0.0"))

        val dependencyConfigurations = project.plugins.getPlugin(FSMConfigurationsPlugin::class.java).getDependencyConfigurations()
        assertThat(dependencyConfigurations).containsExactly(MinMaxVersion("com.google.guava:guava:24.0-jre", "0.0.1", "99.0.0"))
        fsm.get().execute()

        assertThat(moduleXml()).contains("""<resource maxVersion="99.0.0" minVersion="0.0.1" mode="isolated" name="com.google.guava:guava" scope="module" version="24.0-jre">lib/guava-24.0-jre.jar</resource>""")
    }

    @Test
    fun `licenses CSV is present`() {
        fsm.get().execute()
        assertThat(moduleXml()).contains("<licenses>META-INF/licenses.csv</licenses>")
    }


    @Test
    fun `FSM resource-files are used`() {
        project.version = "1.0.0"
        val fsmResourcesProjectFolder = project.file("src/main/fsm-resources").toPath()
        val fsmResourcesNestedProjectFolder = project.file("src/main/fsm-resources/resourcesFolder").toPath()
        Files.createDirectories(fsmResourcesNestedProjectFolder)
        fsmResourcesProjectFolder.resolve("testResource.txt").writeText("Test")
        fsmResourcesNestedProjectFolder.resolve("testNested0.txt").writeText("Test")
        fsmResourcesNestedProjectFolder.resolve("testNested1.txt").writeText("Test")

        fsm.get().execute()

        assertThat(moduleXml()).contains("""<resource mode="isolated" name="${project.group}:${project.name}-testResource.txt" scope="module" version="1.0.0">testResource.txt</resource>""")
        assertThat(moduleXml()).contains("""<resource mode="isolated" name="${project.group}:${project.name}-resourcesFolder" scope="module" version="1.0.0">resourcesFolder</resource>""")

        withFsmFile { fsm ->
            assertThat(fsm.getEntry("testResource.txt")).isNotNull
            assertThat(fsm.getEntry("resourcesFolder/testNested0.txt")).isNotNull
            assertThat(fsm.getEntry("resourcesFolder/testNested1.txt")).isNotNull
        }
    }



    @Test
    fun `FSM resource-files are used for each Web-App`() {
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

        fsm.get().execute()

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

        fsm.get().execute()

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

        fsm.get().execute()

        assertThat(moduleXml()).contains("""<resource name="test:web" version="unspecified">lib/web.jar</resource>""")

        withFsmFile { fsm ->
            assertThat(fsm.getEntry("lib/web.jar")).isNotNull
        }
    }

    @Test
    fun `do not include FSM resource-files for default configurations like implementation`() {
        val subProjectDir = testDir.resolve("sub")
        val subProject = ProjectBuilder.builder().withName("sub").withProjectDir(subProjectDir).withParent(project).build()
        subProject.plugins.apply("java")
        project.dependencies.add("implementation", subProject)
        val resourcePath = subProjectDir.resolve(FSM.FSM_RESOURCES_PATH).resolve("sub.png")
        resourcePath.parentFile.mkdirs()
        resourcePath.createNewFile()

        fsm.get().execute()

        withFsmFile { fsm ->
            assertThat(fsm.getEntry("sub.png")).isNull()
        }
    }

    @Test
    fun `detect duplicates in FSM resource-files`() {
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

        fsm.get().execute()

        // The following files should be detected as duplicates. They will overwrite each other in the FSM archive
        assertThat(fsm.get().duplicateFsmResourceFiles).containsExactlyInAnyOrder(
            File("1.txt"),
            File("nested/n1.txt")
        )
    }

    @Test
    fun `include transitive fsm-resources`() {
        val implDir = testDir.resolve("impl")
        val apiDir = implDir.resolve("api")
        val impl = ProjectBuilder.builder().withName("impl").withProjectDir(implDir).withParent(project).build()
        val api = ProjectBuilder.builder().withName("api").withProjectDir(apiDir).withParent(project).build()
        impl.plugins.apply("java")
        api.plugins.apply("java")

        project.dependencies.add(FS_MODULE_COMPILE_CONFIGURATION_NAME, impl)
        impl.dependencies.add(JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME, api)

        val fsmResourcesProjectFolder = api.file("src/main/fsm-resources").toPath()
        Files.createDirectories(fsmResourcesProjectFolder)
        Files.createFile(fsmResourcesProjectFolder.resolve("test.txt"))

        fsm.get().execute()

        assertThat(moduleXml()).contains("""<resource mode="isolated" name="test:api-test.txt" scope="module" version="unspecified">test.txt</resource>""")

        withFsmFile { fsm ->
            assertThat(fsm.getEntry("test.txt")).isNotNull
        }
    }

    @Test
    fun `include transitive fsm-resources for web-apps`() {
        copyTestJar()

        val webDir = testDir.resolve("web_a")
        val apiDir = webDir.resolve("api")
        val web = ProjectBuilder.builder().withName("web_a").withProjectDir(webDir).withParent(project).build()
        val api = ProjectBuilder.builder().withName("api").withProjectDir(apiDir).withParent(project).build()
        defineArtifactoryForProject(web)
        web.plugins.apply("java")
        api.plugins.apply("java")

        val fsmPluginExtension = project.extensions.getByType(FSMPluginExtension::class.java)
        fsmPluginExtension.webAppComponent("TestWebAppA", web)
        web.dependencies.add(JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME, api)

        val fsmResourcesProjectFolder = api.file(FSM.FSM_RESOURCES_PATH).toPath()
        Files.createDirectories(fsmResourcesProjectFolder)
        Files.createFile(fsmResourcesProjectFolder.resolve("test.txt"))

        fsm.get().execute()

        assertThat(moduleXml()).contains("""<resource name="test:api-test.txt" version="unspecified">test.txt</resource>""")

        withFsmFile { fsm ->
            assertThat(fsm.getEntry("test.txt")).isNotNull
        }
    }

    @Test
    fun `web-xml resources are excluded from resources`() {
        /**
         * web.xml and web0.xml files are used by our test webcomponent implementations.
         * Those should be excluded from the resources tags in the module-isolated.xml, because
         * they aren't regular fsm resources.
         */
        project.version = "1.0.0"

        copyTestJar()

        val fsmResourcesTestProjectFolder = project.file("src/main/fsm-resources").toPath()
        Files.createDirectories(fsmResourcesTestProjectFolder)
        fsmResourcesTestProjectFolder.resolve("web.xml").writeText("<xml></xml>")
        fsmResourcesTestProjectFolder.resolve("web0.xml").writeText("<xml></xml>")
        fsmResourcesTestProjectFolder.resolve("web1.xml").writeText("<xml></xml>")

        fsm.get().execute()

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

        fsm.get().execute()

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

        fsm.get().execute()

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
        val fsmFile = testDir.resolve("build").resolve("fsm").resolve(fsm.get().archiveFile.get().asFile.name)
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
        val fsmFile = testDir.resolve("build").resolve("fsm").resolve(fsm.get().archiveFile.get().asFile.name)
        assertThat(fsmFile).exists()
        ZipFile(fsmFile).use {
            f.invoke(it)
        }
    }

}