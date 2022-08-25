package org.gradle.plugins.fsm.descriptor

import de.espirit.firstspirit.server.module.ModuleInfo
import org.assertj.core.api.Assertions.assertThat
import org.gradle.api.Project
import org.gradle.jvm.tasks.Jar
import org.gradle.plugins.fsm.FSMPluginExtension
import org.gradle.plugins.fsm.annotations.FSMAnnotationsPlugin
import org.gradle.plugins.fsm.configurations.FSMConfigurationsPlugin
import org.gradle.plugins.fsm.configurations.FSMConfigurationsPlugin.Companion.FS_MODULE_COMPILE_CONFIGURATION_NAME
import org.gradle.plugins.fsm.configurations.FSMConfigurationsPlugin.Companion.FS_SERVER_COMPILE_CONFIGURATION_NAME
import org.gradle.plugins.fsm.configurations.fsDependency
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.redundent.kotlin.xml.Node
import org.redundent.kotlin.xml.TextElement
import java.nio.file.Files

class ResourcesTest {

    val project: Project = ProjectBuilder.builder().build()

    @BeforeEach
    fun setup() {
        project.plugins.apply("java-library")
        project.plugins.apply(FSMAnnotationsPlugin::class.java)
        project.plugins.apply(FSMConfigurationsPlugin.NAME)
        project.extensions.create("fsmPlugin", FSMPluginExtension::class.java)
        project.setArtifactoryCredentialsFromLocalProperties()
        project.defineArtifactoryForProject()
    }

    @Test
    fun `plain project resource added`() {
        val resources = Resources(project, emptyList()).node

        val jarName = (resources.children[0] as Node).children[0] as TextElement
        assertThat(jarName.text).isEqualTo("lib/test.jar")
    }


    @Test
    fun `file resources from project dependencies`() {
        val subProject = ProjectBuilder.builder().withName("depProject").withParent(project).build()
        subProject.configurations.create("default")
        val fsmResourceFile = subProject.projectDir.resolve("src/main/fsm-resources/image.png")
        Files.createDirectories(fsmResourceFile.toPath().parent)
        fsmResourceFile.createNewFile()

        val projectDependency = project.dependencies.project(mapOf("path" to ":depProject"))
        project.configurations.getByName(FS_MODULE_COMPILE_CONFIGURATION_NAME).dependencies.add(projectDependency)

        val resources = Resources(project, emptyList()).node

        val fsmResource = resources.children[1] as Node
        assertThat(fsmResource.attributes["name"]).isEqualTo("test:depProject-image.png")
        assertThat(fsmResource.textContent()).isEqualTo("image.png")
    }


    @Test
    fun `create resource from project dependency with custom archive name`() {
        val subProjectName = "subProject"
        val subProject = ProjectBuilder.builder().withName(subProjectName).withParent(project).build()
        subProject.plugins.apply("java")

        // add subProject to the main project as a project dependency
        val projectDependency = project.dependencies.project(mapOf("path" to ":${subProjectName}"))
        project.configurations.getByName(FS_MODULE_COMPILE_CONFIGURATION_NAME).dependencies.add(projectDependency)

        // modify archiveBaseName
        val jar = subProject.tasks.getByName("jar") as Jar
        val customJarName = "myCustomJarName"
        jar.archiveBaseName.set(customJarName)
        val resources = Resources(project, ArrayList()).node

        // finally resolve & filter
        val nodes = resources.filter { node: Node ->
            node.nodeName == "resource" && node.attributes["name"] == "${project.name}:${subProjectName}"
        }

        // verify
        assertThat(nodes).isNotEmpty
        val children = nodes[0].children
        assertThat(children).isNotEmpty
        val child = children[0]
        assertThat(child).isInstanceOf(TextElement::class.java)
        assertThat((child as TextElement).text).isEqualTo("lib/${customJarName}.jar")
    }


    @Test
    fun `directory resources from project dependencies`() {
        val subProject = ProjectBuilder.builder().withName("depProject").withParent(project).build()
        subProject.configurations.create("default")
        val fsmResourceFile = subProject.projectDir.resolve("src/main/fsm-resources/images/image.png")
        Files.createDirectories(fsmResourceFile.toPath().parent)
        fsmResourceFile.createNewFile()

        val projectDependency = project.dependencies.project(mapOf("path" to ":depProject"))
        project.configurations.getByName(FS_MODULE_COMPILE_CONFIGURATION_NAME).dependencies.add(projectDependency)

        val resources = Resources(project, emptyList()).node

        val fsmResource = resources.children[1] as Node
        assertThat(fsmResource.attributes["name"]).isEqualTo("test:depProject-images")
        assertThat(fsmResource.textContent()).isEqualTo("images")
    }


    @Test
    fun `server scope override for project resource`() {
        val subProject = ProjectBuilder.builder().withName("depProject").withParent(project).build()
        subProject.configurations.create("default")
        val fsmResourceFile = subProject.projectDir.resolve("src/main/fsm-resources/image.png")
        Files.createDirectories(fsmResourceFile.toPath().parent)
        fsmResourceFile.createNewFile()

        val projectDependency = project.dependencies.project(mapOf("path" to ":depProject"))
        project.configurations.getByName(FS_MODULE_COMPILE_CONFIGURATION_NAME).dependencies.add(projectDependency)
        project.configurations.getByName(FS_SERVER_COMPILE_CONFIGURATION_NAME).dependencies.add(projectDependency)

        val resources = Resources(project, emptyList()).node

        assertThat(resources.children).hasSize(2)
        val fsmResource = resources.children[1] as Node
        assertThat(fsmResource.attributes["scope"]).isEqualTo("server")
    }


    @Test
    fun `compilation dependency with module scope`() {
        val dependency = project.dependencies.create("org.slf4j:slf4j-api:1.7.32")
        project.configurations.getByName(FS_MODULE_COMPILE_CONFIGURATION_NAME).dependencies.add(dependency)

        val resources = Resources(project, emptyList()).node

        val resource = resources.filter { it.attributes["name"] == "org.slf4j:slf4j-api" }.single()
        assertThat(resource.attributes["version"]).isEqualTo("1.7.32")
        assertThat(resource.attributes["minVersion"]).isEqualTo("1.7.32")
        assertThat(resource.attributes["mode"]).isEqualTo(ModuleInfo.Mode.ISOLATED.name.lowercase())
        assertThat(resource.textContent()).isEqualTo("lib/slf4j-api-1.7.32.jar")
    }

    @Test
    fun `omit minVersion`() {
        val dependency = project.dependencies.create("org.slf4j:slf4j-api:1.7.32")
        project.configurations.getByName(FS_MODULE_COMPILE_CONFIGURATION_NAME).dependencies.add(dependency)
        project.extensions.getByType(FSMPluginExtension::class.java).appendDefaultMinVersion = false

        val resources = Resources(project, emptyList()).node

        val resource = resources.filter { it.attributes["name"] == "org.slf4j:slf4j-api" }.single()
        assertThat(resource.attributes["version"]).isEqualTo("1.7.32")
        assertThat(resource.hasAttribute("minVersion")).isFalse
        assertThat(resource.attributes["mode"]).isEqualTo(ModuleInfo.Mode.ISOLATED.name.lowercase())
        assertThat(resource.textContent()).isEqualTo("lib/slf4j-api-1.7.32.jar")
    }

    @Test
    fun `min and max version with fsDependency extension`() {
        val fsDependency = project.fsDependency("org.slf4j:slf4j-api:1.7.32", "1.7", "1.8")
        val dependency = project.dependencies.create(fsDependency)
        project.configurations.getByName(FS_MODULE_COMPILE_CONFIGURATION_NAME).dependencies.add(dependency)

        val resources = Resources(project, emptyList()).node

        val resource = resources.filter { it.attributes["name"] == "org.slf4j:slf4j-api" }.single()
        assertThat(resource.attributes["version"]).isEqualTo("1.7.32")
        assertThat(resource.attributes["minVersion"]).isEqualTo("1.7")
        assertThat(resource.attributes["maxVersion"]).isEqualTo("1.8")
        assertThat(resource.attributes["mode"]).isEqualTo(ModuleInfo.Mode.ISOLATED.name.lowercase())
        assertThat(resource.textContent()).isEqualTo("lib/slf4j-api-1.7.32.jar")
    }

    @Test
    fun `min and max version with map`() {
        val map = mapOf("dependency" to "org.slf4j:slf4j-api:1.7.32", "skipInLegacy" to true,
            "minVersion" to "1.7", "maxVersion" to "1.8")

        val fsDependency = project.fsDependency(map)
        val dependency = project.dependencies.create(fsDependency)
        project.configurations.getByName(FS_MODULE_COMPILE_CONFIGURATION_NAME).dependencies.add(dependency)

        val resources = Resources(project, emptyList()).node

        val resource = resources.filter { it.attributes["name"] == "org.slf4j:slf4j-api" }.single()
        assertThat(resource.attributes["version"]).isEqualTo("1.7.32")
        assertThat(resource.attributes["minVersion"]).isEqualTo("1.7")
        assertThat(resource.attributes["maxVersion"]).isEqualTo("1.8")
        assertThat(resource.attributes["mode"]).isEqualTo(ModuleInfo.Mode.ISOLATED.name.lowercase())
        assertThat(resource.textContent()).isEqualTo("lib/slf4j-api-1.7.32.jar")
    }

    @Test
    fun `dependency jar with classifier`() {
        val dependency = project.dependencies.create("org.slf4j:slf4j-api:1.7.32:debug")
        project.configurations.getByName(FS_MODULE_COMPILE_CONFIGURATION_NAME).dependencies.add(dependency)

        val resources = Resources(project, emptyList()).node

        val resource = resources.filter { it.attributes["name"] == "org.slf4j:slf4j-api:debug" }.single()
        assertThat(resource.attributes["version"]).isEqualTo("1.7.32")
        assertThat(resource.textContent()).isEqualTo("lib/slf4j-api-1.7.32-debug.jar")
    }

    @Test
    fun `dependency fsm without classifier`() {
        val dependency = project.dependencies.create("org.slf4j:slf4j-api:1.7.32@fsm")
        project.configurations.getByName(FS_MODULE_COMPILE_CONFIGURATION_NAME).dependencies.add(dependency)

        val resources = Resources(project, emptyList()).node

        val resource = resources.filter { it.attributes["name"] == "org.slf4j:slf4j-api@fsm" }.single()
        assertThat(resource.attributes["version"]).isEqualTo("1.7.32")
        assertThat(resource.textContent()).isEqualTo("lib/slf4j-api-1.7.32.fsm")
    }

    @Test
    fun `dependency fsm with classifier`() {
        val dependency = project.dependencies.create("org.slf4j:slf4j-api:1.7.32:debug@fsm")
        project.configurations.getByName(FS_MODULE_COMPILE_CONFIGURATION_NAME).dependencies.add(dependency)

        val resources = Resources(project, emptyList()).node

        val resource = resources.filter { it.attributes["name"] == "org.slf4j:slf4j-api:debug@fsm" }.single()
        assertThat(resource.attributes["version"]).isEqualTo("1.7.32")
        assertThat(resource.textContent()).isEqualTo("lib/slf4j-api-1.7.32-debug.fsm")
    }

    @Disabled("DEVEX-497 - Race Condition When Downloading Dependency Twice")
    @Test
    fun `resolve scope resource conflict`() {
        project.dependencies.add(FS_MODULE_COMPILE_CONFIGURATION_NAME, "org.joda:joda-convert:2.1.1")
        project.dependencies.add(FS_SERVER_COMPILE_CONFIGURATION_NAME, "org.joda:joda-convert:2.1.1")
        project.dependencies.add(FS_MODULE_COMPILE_CONFIGURATION_NAME, "org.slf4j:slf4j-api:1.7.25")
        project.dependencies.add(FS_SERVER_COMPILE_CONFIGURATION_NAME, "org.slf4j:slf4j-api:1.7.25")

        val resources = Resources(project, emptyList()).node

        val jodaConvert = resources.filter { it.attributes["name"] == "org.joda:joda-convert" }.single()
        val slf4j = resources.filter { it.attributes["name"] == "org.slf4j:slf4j-api" }.single()
        assertThat(jodaConvert.attributes["scope"]).isEqualTo("server")
        assertThat(slf4j.attributes["scope"]).isEqualTo("server")
    }

    @Disabled("DEVEX-497 - Race Condition When Downloading Dependency Twice")
    @Test
    fun `module scope server scope resolution works correctly`() {
        // Module and server scope should be resolved together, so the higher version wins, just like normal maven
        // dependency resolution works. In the fsm, the dependency should then only be present on server scope.
        // That's a bit unintuitive, but the right behaviour.

        project.dependencies.add(FS_MODULE_COMPILE_CONFIGURATION_NAME, "org.slf4j:slf4j-api:1.7.1")
        project.dependencies.add(FS_SERVER_COMPILE_CONFIGURATION_NAME, "org.slf4j:slf4j-api:1.7.0")
        project.dependencies.add(FS_MODULE_COMPILE_CONFIGURATION_NAME, "org.joda:joda-convert:2.1.1")

        val resources = Resources(project, emptyList()).node

        val slf4j = resources.filter { it.attributes["name"] == "org.slf4j:slf4j-api" }.single()
        assertThat(slf4j.attributes["scope"]).isEqualTo("server")
        assertThat(slf4j.attributes["version"]).isEqualTo("1.7.1")
        val jodaConvert = resources.filter { it.attributes["name"] == "org.joda:joda-convert" }.single()
        assertThat(jodaConvert.attributes["scope"]).isEqualTo("module")
        assertThat(jodaConvert.attributes["version"]).isEqualTo("2.1.1")
    }

    @Disabled("DEVEX-497 - Race Condition When Downloading Dependency Twice")
    @Test
    fun `module scope server scope resolution works correctly with server version higher`() {
        // Module and server scope should be resolved together, so the higher version wins, just like normal maven
        // dependency resolution works. In the fsm, the dependency should then only be present on server scope.
        // That's a bit unintuitive, but the right behaviour.

        project.dependencies.add(FS_MODULE_COMPILE_CONFIGURATION_NAME, "org.slf4j:slf4j-api:1.7.0")
        project.dependencies.add(FS_SERVER_COMPILE_CONFIGURATION_NAME, "org.slf4j:slf4j-api:1.7.1")
        project.dependencies.add(FS_MODULE_COMPILE_CONFIGURATION_NAME, "org.joda:joda-convert:2.1.1")

        val resources = Resources(project, emptyList()).node

        val slf4j = resources.filter { it.attributes["name"] == "org.slf4j:slf4j-api" }.single()
        assertThat(slf4j.attributes["scope"]).isEqualTo("server")
        assertThat(slf4j.attributes["version"]).isEqualTo("1.7.1")
        val jodaConvert = resources.filter { it.attributes["name"] == "org.joda:joda-convert" }.single()
        assertThat(jodaConvert.attributes["scope"]).isEqualTo("module")
        assertThat(jodaConvert.attributes["version"]).isEqualTo("2.1.1")
    }

    @Test
    fun `valid string representation of resources`() {
        project.version = "1.6"
        val dependency = project.dependencies.create("org.slf4j:slf4j-api:1.7.32")
        project.configurations.getByName(FS_MODULE_COMPILE_CONFIGURATION_NAME).dependencies.add(dependency)

        val resources = Resources(project, emptyList())

        assertThat(resources.innerResourcesToString()).isEqualTo("""
            <resource name=":test" version="1.6" scope="module" mode="isolated">lib/test-1.6.jar</resource>
            <resource name="org.slf4j:slf4j-api" scope="module" mode="isolated" version="1.7.32" minVersion="1.7.32">lib/slf4j-api-1.7.32.jar</resource>
        """.trimIndent())
    }

}