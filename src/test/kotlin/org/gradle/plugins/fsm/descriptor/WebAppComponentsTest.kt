package org.gradle.plugins.fsm.descriptor

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.plugins.ExtraPropertiesExtension
import org.gradle.plugins.fsm.FSMPluginExtension
import org.gradle.plugins.fsm.annotations.FSMAnnotationsPlugin
import org.gradle.plugins.fsm.configurations.FSMConfigurationsPlugin
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class WebAppComponentsTest {

    val project: Project = ProjectBuilder.builder().withName(NAME).build()

    @BeforeEach
    fun setup() {
        project.group = GROUP
        project.version = VERSION

        project.plugins.apply("java-library")
        project.plugins.apply(FSMAnnotationsPlugin::class.java)
        project.plugins.apply(FSMConfigurationsPlugin::class.java)
        project.extensions.create("fsmPlugin", FSMPluginExtension::class.java)
        project.setArtifactoryCredentialsFromLocalProperties()
        project.defineArtifactoryForProject()

        project.copyTestJar()
    }

    @Test
    fun `minimal web app component`() {
        val moduleDescriptor = ModuleDescriptor(project)
        val components = moduleDescriptor.components.node
        val component = components.filter{ it.childText("name" ) == "TestMinimalWebAppComponentName" }.single()
        assertThat(component.nodeName).isEqualTo("web-app")
        assertThat(component.attributes).doesNotContainKey("xml-schema-version")
        assertThat(component.childText("displayname")).isEmpty()
        assertThat(component.childText("description")).isEmpty()
        assertThat(component.childText("class")).isEqualTo("org.gradle.plugins.fsm.TestMinimalWebAppComponent")
        assertThat(component.childText("web-xml")).isEqualTo("/web.xml")
    }

    @Test
    fun `web app should contain basic information`() {
        val moduleDescriptor = ModuleDescriptor(project)
        val components = moduleDescriptor.components.node
        val component = components.filter{ it.childText("name" ) == "TestWebAppComponentName" }.single()
        assertThat(component.nodeName).isEqualTo("web-app")
        assertThat(component.attributes["scopes"].toString().split(",")).containsExactlyInAnyOrder("PROJECT", "GLOBAL")
        assertThat(component.attributes).containsEntry("xml-schema-version", "5.0")
        assertThat(component.childText("displayname")).isEqualTo("TestDisplayName")
        assertThat(component.childText("description")).isEqualTo("TestDescription")
        assertThat(component.childText("class")).isEqualTo("org.gradle.plugins.fsm.TestWebAppComponent")
        assertThat(component.childText("web-xml")).isEqualTo("/web.xml")
    }

    @Test
    fun `web app with configurable`() {
        val moduleDescriptor = ModuleDescriptor(project)
        val components = moduleDescriptor.components.node
        val component = components.filter{ it.childText("name" ) == "TestWebAppComponentName" }.single()
        assertThat(component.childText("class")).isEqualTo("org.gradle.plugins.fsm.TestWebAppComponent")
        assertThat(component.childText("configurable"))
            .isEqualTo("org.gradle.plugins.fsm.TestWebAppComponent\$TestConfigurable")
    }

    @Test
    fun `web app without configurable`() {
        val moduleDescriptor = ModuleDescriptor(project)
        val components = moduleDescriptor.components.node
        val component = components.filter{ it.childText("name" ) == "TestWebAppComponentWithoutConfigurationName" }.single()
        assertThat(component.childText("class")).endsWith(".TestWebAppComponentWithoutConfiguration")
        assertThat(component.filter("configurable")).isEmpty()
    }

    @Test
    fun `web app resource with target path`() {
        val moduleDescriptor = ModuleDescriptor(project)
        val components = moduleDescriptor.components.node
        val component = components.filter{ it.childText("name" ) == "TestWebAppComponentName" }.single()
        val webResources = component.filter("web-resources").single()
        val commonsLang = webResources.filter{ it.attributes["name"] == "org.apache.commons:commons-lang3" }.single()
        assertThat(commonsLang.attributes["target"]).isEqualTo("targetPath")
    }

    @Test
    fun `web app resource provided by project`() {
        val moduleDescriptor = ModuleDescriptor(project)
        val components = moduleDescriptor.components.node
        val component = components.filter{ it.childText("name" ) == "TestWebAppComponentName" }.single()
        val webResources = component.filter("web-resources").single()
        val webResource = webResources.filter("resource").first()

        assertThat(webResource.attributes["name"]).isEqualTo("${GROUP}:${NAME}")
        assertThat(webResource.attributes["version"]).isEqualTo(VERSION)
        assertThat(webResource.textContent()).isEqualTo("lib/${NAME}-${VERSION}.jar")
    }

    @Test
    fun `web app resources from annotation`() {
        val moduleDescriptor = ModuleDescriptor(project)
        val components = moduleDescriptor.components.node
        val component = components.filter{ it.childText("name" ) == "TestWebAppComponentName" }.single()
        val webResourcesTag = component.filter("web-resources").single()
        val webResources = webResourcesTag.filter("resource")

        val guava = webResources.single { it.attributes["name"] == "com.google.guava:guava" }
        assertThat(guava.attributes["version"]).isEqualTo("24.0")
        assertThat(guava.hasAttribute("minVersion")).isFalse
        assertThat(guava.hasAttribute("maxVersion")).isFalse
        assertThat(guava.textContent()).isEqualTo("lib/guava-24.0.jar")

        val commonsLang = webResources.single { it.attributes["name"] == "org.apache.commons:commons-lang3" }
        assertThat(commonsLang.attributes["version"]).isEqualTo("3.0")
        assertThat(commonsLang.attributes["minVersion"]).isEqualTo("2.9")
        assertThat(commonsLang.attributes["maxVersion"]).isEqualTo("3.1")
        assertThat(commonsLang.attributes["target"]).isEqualTo("targetPath")
        assertThat(commonsLang.textContent()).isEqualTo("lib/commons-lang-3.0.jar")
    }

    @Test
    fun `project property interpolation in webapp resources`() {
        project.addClassToTestJar("org/gradle/plugins/fsm/TestWebAppWithProjectProperties.class")
        project.extensions.getByType(ExtraPropertiesExtension::class.java).set("myCustomVersionPropertyString", 5)

        val moduleDescriptor = ModuleDescriptor(project)
        val components = moduleDescriptor.components.node
        val component = components.filter{ it.childText("name" ) == "WebApp with project properties" }.single()
        val webResourcesTag = component.filter("web-resources").single()
        val webResources = webResourcesTag.filter("resource")

        val res = webResources.single { it.attributes["name"] == "test-webapps-test-project-res" }
        assertThat(res.attributes["version"]).isEqualTo("5")
        assertThat(res.hasAttribute("scope")).isFalse
        assertThat(res.hasAttribute("mode")).isFalse

        val res2 = webResources.single { it.attributes["name"] == "test-webapps-test-project-res2" }
        assertThat(res2.attributes["version"]).isEqualTo(VERSION)
        assertThat(res2.hasAttribute("scope")).isFalse
        assertThat(res2.hasAttribute("mode")).isFalse
    }

    @Test
    fun `web app resources from fsWebCompile configuration`() {
        project.dependencies.add(FSMConfigurationsPlugin.FS_WEB_COMPILE_CONFIGURATION_NAME, "joda-time:joda-time:2.3")
        project.dependencies.add(FSMConfigurationsPlugin.FS_WEB_COMPILE_CONFIGURATION_NAME, "org.joda:joda-convert:2.1.1")

        val moduleDescriptor = ModuleDescriptor(project)
        val components = moduleDescriptor.components.node
        val component = components.filter{ it.childText("name" ) == "TestWebAppComponentName" }.single()
        val webResourcesTag = component.filter("web-resources").single()
        val webResources = webResourcesTag.filter("resource")

        val jodaTime = webResources.single { it.attributes["name"] == "joda-time:joda-time" }
        assertThat(jodaTime.attributes["version"]).isEqualTo("2.3")
        assertThat(jodaTime.attributes["minVersion"]).isEqualTo("2.3")
        assertThat(jodaTime.hasAttribute("maxVersion")).isFalse
        assertThat(jodaTime.hasAttribute("mode")).isFalse
        assertThat(jodaTime.textContent()).isEqualTo("lib/joda-time-2.3.jar")

        val jodaConvert = webResources.single { it.attributes["name"] == "org.joda:joda-convert" }
        assertThat(jodaConvert.attributes["version"]).isEqualTo("2.1.1")
        assertThat(jodaConvert.attributes["minVersion"]).isEqualTo("2.1.1")
        assertThat(jodaConvert.hasAttribute("mode")).isFalse
        assertThat(jodaConvert.textContent()).isEqualTo("lib/joda-convert-2.1.1.jar")
    }

    @Test
    fun `multiple web apps`() {
        val webAppAProject = ProjectBuilder.builder().withParent(project).withName("web_a").build()
        webAppAProject.plugins.apply("java")
        webAppAProject.version = "0.1"
        webAppAProject.repositories.add(webAppAProject.repositories.mavenCentral())
        webAppAProject.dependencies.add("implementation", "org.joda:joda-convert:2.1.2")   //  Version higher than for fsWebCompile, should  use higher version
        webAppAProject.dependencies.add("implementation", "org.slf4j:slf4j-api:1.7.24")
        webAppAProject.dependencies.add("implementation", "commons-logging:commons-logging:1.2")

        val webAppBProject = ProjectBuilder.builder().withParent(project).withName("web_b").build()
        webAppBProject.plugins.apply("java")
        webAppBProject.version = "0.2"
        webAppBProject.repositories.add(webAppBProject.repositories.mavenCentral())
        webAppBProject.dependencies.add("implementation", "org.slf4j:slf4j-api:1.7.25")    // Higher Version

        val fsmPluginExtension = project.extensions.getByType(FSMPluginExtension::class.java)
        fsmPluginExtension.webAppComponent("TestWebAppA", webAppAProject)
        fsmPluginExtension.webAppComponent("TestWebAppB", webAppBProject)

        val moduleDescriptor = ModuleDescriptor(project)
        val components = moduleDescriptor.components.node
        val webAppComponentA = components.filter{ it.childText("name" ) == "TestWebAppA" }.single()
        val webResourcesTagA = webAppComponentA.filter("web-resources").single()
        val webResourcesA = webResourcesTagA.filter("resource")
        val jodaConvertA = webResourcesA.single { it.attributes["name"] == "org.joda:joda-convert" }
        val slf4jA = webResourcesA.single { it.attributes["name"] == "org.slf4j:slf4j-api" }
        val commonsLoggingA = webResourcesA.single { it.attributes["name"] == "commons-logging:commons-logging" }
        assertThat(webAppComponentA.childText("web-xml")).isEqualTo("a/web.xml")
        assertThat(jodaConvertA.attributes["version"]).isEqualTo("2.1.2")
        assertThat(slf4jA.attributes["version"]).isEqualTo("1.7.25")
        assertThat(commonsLoggingA.attributes["version"]).isEqualTo("1.2")

        val webAppComponentB = components.filter{ it.childText("name" ) == "TestWebAppB" }.single()
        val webResourcesTagB = webAppComponentB.filter("web-resources").single()
        val webResourcesB = webResourcesTagB.filter("resource")
        val slf4jB = webResourcesB.single { it.attributes["name"] == "org.slf4j:slf4j-api" }
        assertThat(webAppComponentB.childText("web-xml")).isEqualTo("b/web.xml")
        assertThat(slf4jB.attributes["version"]).isEqualTo("1.7.25")
    }

    @Test
    fun `include jar of dependency web-app project`() {
        val webAppProject = ProjectBuilder.builder().withParent(project).withName("web").build()
        webAppProject.plugins.apply("java")
        webAppProject.version = "0.1"
        webAppProject.repositories.add(webAppProject.repositories.mavenCentral())
        webAppProject.writeJarFileWithEntries("de/espirit/Test.class")

        val fsmPluginExtension = project.extensions.getByType(FSMPluginExtension::class.java)
        fsmPluginExtension.webAppComponent("TestWebAppA", webAppProject)

        val moduleDescriptor = ModuleDescriptor(project)
        val components = moduleDescriptor.components.node
        val webAppComponent = components.filter{ it.childText("name" ) == "TestWebAppA" }.single()
        val webResourcesTag = webAppComponent.filter("web-resources").single()
        val webResources = webResourcesTag.filter("resource")

        val jarTaskOutput = webResources.single { it.attributes["name"] == "webapps-test-project:web" }
        assertThat(jarTaskOutput.attributes["version"]).isEqualTo(webAppProject.version)
    }

    @Test
    fun `do not include empty jar of dependency web-app project`() {
        val webAppProject = ProjectBuilder.builder().withParent(project).withName("web").build()
        webAppProject.plugins.apply("java")
        webAppProject.version = "0.1"
        webAppProject.repositories.add(webAppProject.repositories.mavenCentral())
        webAppProject.writeJarFileWithEntries()

        val fsmPluginExtension = project.extensions.getByType(FSMPluginExtension::class.java)
        fsmPluginExtension.webAppComponent("TestWebAppA", webAppProject)

        val moduleDescriptor = ModuleDescriptor(project)
        val components = moduleDescriptor.components.node
        val webAppComponent = components.filter{ it.childText("name" ) == "TestWebAppA" }.single()
        val webResourcesTag = webAppComponent.filter("web-resources").single()
        val webResources = webResourcesTag.filter("resource")

        val jarTaskOutput = webResources.filter { it.attributes["name"] == "webapps-test-project:web" }
        assertThat(jarTaskOutput).isEmpty()
    }

    @Test
    fun `fail on unknown webapp`()  {
        // We register a WebApp in `firstSpiritModule` using `webAppComponent`, but
        // there's no matching annotation.
        // This should cause WebAppComponents#verify to fail because of an error

        val webAppAProject = ProjectBuilder.builder().withParent(project).withName("web_a").build()
        webAppAProject.plugins.apply("java")
        webAppAProject.version = "0.1"

        val fsmPluginExtension = project.extensions.getByType(FSMPluginExtension::class.java)
        fsmPluginExtension.webAppComponent("not_existing", webAppAProject)

        assertThatExceptionOfType(GradleException::class.java)
            .isThrownBy { ModuleDescriptor(project).node }
            .withMessageContaining("not_existing")
    }

    companion object {
        private const val NAME = "webapps-test-project"
        private const val GROUP = "test"
        private const val VERSION = "1.2"
    }


}