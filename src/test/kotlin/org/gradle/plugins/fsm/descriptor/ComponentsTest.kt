package org.gradle.plugins.fsm.descriptor

import de.espirit.firstspirit.module.GadgetSpecification
import org.assertj.core.api.Assertions.assertThat
import org.gradle.api.Project
import org.gradle.plugins.fsm.FSMPluginExtension
import org.gradle.plugins.fsm.annotations.FSMAnnotationsPlugin
import org.gradle.plugins.fsm.configurations.FSMConfigurationsPlugin
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.redundent.kotlin.xml.Node

class ComponentsTest {

    val project: Project = ProjectBuilder.builder().build()
    lateinit var components: Node

    @BeforeEach
    fun setup() {
        project.plugins.apply("java-library")
        project.plugins.apply(FSMAnnotationsPlugin::class.java)
        project.plugins.apply(FSMConfigurationsPlugin::class.java)
        project.extensions.create("fsmPlugin", FSMPluginExtension::class.java)
        project.setArtifactoryCredentialsFromLocalProperties()
        project.defineArtifactoryForProject()
        project.copyTestJar()

        val moduleDescriptor = ModuleDescriptor(project)
        components = moduleDescriptor.components.node
    }

    @Test
    fun `minimal public component`() {
        val component = componentWithName("TestMinimalPublicComponentName")
        assertThat(component.childText("displayname")).isEmpty()
        assertThat(component.childText("description")).isEmpty()
        assertThat(component.exists("configurable")).isFalse
        assertThat(component.childText("class")).isEqualTo("org.gradle.plugins.fsm.TestMinimalPublicComponent")
    }

    @Test
    fun `public component should not be hidden by default`() {
        val component = componentWithName("TestMinimalPublicComponentName")
        assertThat(component.filter("hidden")).isEmpty()
    }

    @Test
    fun `hidden public component`() {
        val component = componentWithName("TestHiddenPublicComponentName")
        val hidden = component.filter("hidden").single()
        assertThat(hidden.textContent().toBoolean()).isTrue()
    }

    @Test
    fun `public component`() {
        val component = componentWithName("TestPublicComponentName")

        assertThat(component.childText("description")).isEqualTo("Component Description")
        assertThat(component.exists("configurable")).isFalse
        assertThat(component.childText("class")).isEqualTo("org.gradle.plugins.fsm.TestPublicComponent")
    }

    @Test
    fun `public component with configurable`() {
        val component = componentWithName("TestPublicComponentWithConfigName")
        assertThat(component.childText("description")).isEmpty()
        assertThat(component.childText("configurable")).isEqualTo("org.gradle.plugins.fsm.TestConfigurable")
        assertThat(component.childText("class")).endsWith(".TestPublicComponentWithConfiguration")
    }

    @Test
    fun `minimal schedule task component`() {
        val component = componentWithName("Test task without display name")
        assertThat(component.exists("displayname")).isFalse
        assertThat(component.childText("description")).isEmpty()
        assertThat(component.childText("class")).isEqualTo("de.espirit.firstspirit.module.ScheduleTaskSpecification")
        val configuration = component.filter("configuration").single()
        assertThat(configuration.childText("application")).endsWith(".TestMinimalScheduleTaskComponent")
        assertThat(configuration.filter("form")).isEmpty()
    }

    @Test
    fun `schedule task component without form`() {
        val component = componentWithName("Test task without form")
        assertThat(component.childText("class")).isEqualTo("de.espirit.firstspirit.module.ScheduleTaskSpecification")
        val configuration = component.filter("configuration").single()
        assertThat(configuration.childText("application")).endsWith(".TestScheduleTaskComponentWithoutForm")
        assertThat(configuration.filter("form")).isEmpty()
    }

    @Test
    fun `schedule task component with form`() {
        val component = componentWithName("Test task with form")
        assertThat(component.childText("class")).isEqualTo("de.espirit.firstspirit.module.ScheduleTaskSpecification")
        val configuration = component.filter("configuration").single()
        assertThat(configuration.childText("application")).endsWith(".TestScheduleTaskComponentWithForm")
        assertThat(configuration.childText("form")).isEqualTo("org.gradle.plugins.fsm.TestScheduleTaskFormFactory")
    }

    @Test
    fun `schedule task component with configurable`() {
        val component = componentWithName("Test task with configurable")
        assertThat(component.childText("class")).isEqualTo("de.espirit.firstspirit.module.ScheduleTaskSpecification")
        assertThat(component.childText("configurable")).isEqualTo("org.gradle.plugins.fsm.TestConfigurable")
        val configuration = component.filter("configuration").single()
        assertThat(configuration.childText("application")).endsWith(".TestScheduleTaskComponentWithConfigurable")
        assertThat(configuration.filter("form")).isEmpty()
    }

    @Test
    fun `scheduleTask component tag with form`() {
        val component = componentWithName("Test task with form")
        assertThat(component.childText("description")).isEqualTo("A task for test purposes")
        assertThat(component.childText("class")).isEqualTo("de.espirit.firstspirit.module.ScheduleTaskSpecification")
        val configuration = component.filter("configuration").single()
        assertThat(configuration.childText("application")).endsWith(".TestScheduleTaskComponentWithForm")
        assertThat(configuration.childText("form")).endsWith(".TestScheduleTaskFormFactory")
    }

    @Test
    fun `scheduleTask component tag with configurable`() {
        val component = componentWithName("Test task with configurable")
        assertThat(component.childText("description")).isEqualTo("A task for test purposes")
        assertThat(component.childText("class")).isEqualTo("de.espirit.firstspirit.module.ScheduleTaskSpecification")
        assertThat(component.childText("configurable")).isEqualTo("org.gradle.plugins.fsm.TestConfigurable")
        val configuration = component.filter("configuration").single()
        assertThat(configuration.childText("application")).endsWith(".TestScheduleTaskComponentWithConfigurable")
    }

    @Test
    fun `scheduleTask component tag without form`() {
        val component = componentWithName("Test task without form")
        val configuration = component.filter("configuration").single()
        assertThat(configuration.filter("form")).isEmpty()
    }

    @Test
    fun `gadgetComponent should have GadgetSpecification as class`() {
        val component = componentWithName("Test gadget")
        assertThat(component.childText("class")).isEqualTo(GadgetSpecification::class.java.name)
    }

    @Test
    fun `gadgetComponent should have annotated class as gom`() {
        val component = componentWithName("Test gadget")
        val configuration = component.filter("configuration").single()
        assertThat(configuration.childText("gom")).endsWith("TestMinimalGadgetComponent")
    }

    @Test
    fun `gadgetComponent should have GadgetScope unrestricted as default`() {
        val component = componentWithName("Test gadget")
        val configuration = component.filter("configuration").single()
        val scope = configuration.filter("scope").single()
        assertThat(scope.attributes["unrestricted"]).isEqualTo("yes")
    }

    @Test
    fun `gadgetComponent should have a factory tag if factory is set`() {
        val component = componentWithName("Test gadget with one factory")
        val configuration = component.filter("configuration").single()
        assertThat(configuration.filter("factory")).isNotEmpty
    }

    @Test
    fun `gadgetComponent should have a value tag if value factory is set`() {
        val component = componentWithName("Test gadget with all attributes")
        val configuration = component.filter("configuration").single()
        assertThat(configuration.filter("value")).isNotEmpty
    }

    @Test
    fun `gadgetComponent should have full qualified class as factory`() {
        val component = componentWithName("Test gadget with one factory")
        val configuration = component.filter("configuration").single()
        assertThat(configuration.childText("factory")).isEqualTo("org.gradle.plugins.fsm.TestGadgetFactoryOne")
    }


    @Test
    fun `gadgetComponent should have full qualified class as value`() {
        val component = componentWithName("Test gadget with all attributes")
        val configuration = component.filter("configuration").single()
        assertThat(configuration.childText("value")).isEqualTo("org.gradle.plugins.fsm.TestValueEngineerFactory")
    }


    @Test
    fun `gadgetComponent should have factory tag for each factory`() {
        val component = componentWithName("Test gadget with more than one factory")
        val configuration = component.filter("configuration").single()
        assertThat(configuration.filter("factory")).hasSize(2)
    }


    @Test
    fun `gadgetComponent should not include unimplemented factory class`() {
        val component = componentWithName("Test gadget with unimplemented factory")
        val configuration = component.filter("configuration").single()
        assertThat(configuration.filter("factory")).isEmpty()
    }


    @Test
    fun `gadgetComponent should have gom factory value and scope within configuration tag`() {
        val component = componentWithName("Test gadget with all attributes")
        val configuration = component.filter("configuration").single()
        val scope = configuration.filter("scope").single()
        assertThat(configuration.childText("gom")).isNotBlank
        assertThat(configuration.childText("factory")).isNotBlank
        assertThat(configuration.childText("value")).isNotBlank
        assertThat(scope.attributes["data"]).isEqualTo("yes")
    }

    @Test
    fun `minimal url creator`() {
        val component = componentWithName("TestMinimalUrlFactoryComponentName")
        assertThat(component.childText("class")).isEqualTo("de.espirit.firstspirit.generate.UrlCreatorSpecification")
        assertThat(component.childText("displayname")).isEmpty()
        assertThat(component.childText("description")).isEmpty()
        val configuration = component.filter("configuration").single()
        assertThat(configuration.childText("UrlFactory")).endsWith(".TestMinimalUrlFactoryComponent")
        assertThat(configuration.childText("UseRegistry")).isEqualTo("true")
        assertThat(configuration.filter("FilenameFactory")).isEmpty()
    }

    @Test
    fun `url creator without filename factory`() {
        val component = componentWithName("TestUrlFactoryComponentName")
        assertThat(component.childText("class")).isEqualTo("de.espirit.firstspirit.generate.UrlCreatorSpecification")
        val configuration = component.filter("configuration").single()
        assertThat(configuration.childText("UrlFactory")).endsWith(".TestUrlFactoryComponent")
        assertThat(configuration.childText("UseRegistry")).isEqualTo("true")
        assertThat(configuration.filter("FilenameFactory")).isEmpty()
    }

    @Test
    fun `url creator with filename factory`() {
        val component = componentWithName("TestUrlFactoryWithFilenameFactoryComponentName")
        assertThat(component.childText("class")).isEqualTo("de.espirit.firstspirit.generate.UrlCreatorSpecification")
        val configuration = component.filter("configuration").single()
        assertThat(configuration.childText("UrlFactory")).endsWith(".TestUrlFactoryWithFilenameFactory")
        assertThat(configuration.childText("UseRegistry")).isEqualTo("true")
        assertThat(configuration.childText("FilenameFactory")).endsWith("TestFilenameFactory")
    }

    @Test
    fun `url creator with custom parameters`() {
        val component = componentWithName("TestUrlFactoryWithParameters")
        assertThat(component.childText("class")).isEqualTo("de.espirit.firstspirit.generate.UrlCreatorSpecification")
        val configuration = component.filter("configuration").single()
        assertThat(configuration.childText("UrlFactory")).endsWith(".TestUrlFactoryWithParameters")
        assertThat(configuration.childText("myCustomParameterA")).isEqualTo("1")
        assertThat(configuration.childText("myCustomParameterB")).isEqualTo("2")
    }

    @Test
    fun `minimal service component`() {
        val component = componentWithName("TestMinimalServiceComponentName")
        assertThat(component.childText("displayname")).isEmpty()
        assertThat(component.childText("description")).isEmpty()
        assertThat(component.childText("class")).isEqualTo("org.gradle.plugins.fsm.TestMinimalServiceComponent")
    }

    @Test
    fun `service component should not be hidden by default`() {
        val component = componentWithName("TestMinimalServiceComponentName")
        assertThat(component.filter("hidden")).isEmpty()
    }

    @Test
    fun `hidden service component`() {
        val component = componentWithName("TestHiddenServiceComponentName")
        val hidden = component.filter("hidden").single()
        assertThat(hidden.textContent().toBoolean()).isTrue()
    }

    @Test
    fun `service tag with configuration`() {
        val component = componentWithName("TestServiceComponentName")
        assertThat(component.childText("displayname")).isEqualTo("TestDisplayName")
        assertThat(component.childText("description")).isEqualTo("TestDescription")
        assertThat(component.childText("class")).isEqualTo("org.gradle.plugins.fsm.TestServiceComponent")
        assertThat(component.childText("configurable"))
            .isEqualTo("org.gradle.plugins.fsm.TestServiceComponent\$TestConfigurable")
    }

    @Test
    fun `service tag without configuration`() {
        val component = componentWithName("TestServiceComponentWithoutConfigurableName")
        assertThat(component.childText("displayname")).isEqualTo("TestDisplayName")
        assertThat(component.childText("description")).isEqualTo("TestDescription")
        assertThat(component.childText("class")).endsWith(".TestServiceComponentWithoutConfigurable")
        assertThat(component.filter("configurable")).isEmpty()
    }

    @Test
    fun `valid string representation of components`() {
        val moduleDescriptor = ModuleDescriptor(project)
        val inner = moduleDescriptor.components.innerComponentsToString()
        assertThat(inner).containsIgnoringWhitespaces("""
            <public>
            	<name>TestMinimalPublicComponentName</name> 
            	<displayname/>
            	<description/>
            	<class>org.gradle.plugins.fsm.TestMinimalPublicComponent</class>
            </public>
        """.trimIndent())

        assertThat(inner).containsIgnoringWhitespaces("""
            <public>
            	<name>TestPublicComponentWithConfigName</name>
            	<displayname>TestDisplayName</displayname>
            	<description/>
            	<class>org.gradle.plugins.fsm.TestPublicComponentWithConfiguration</class>
            	<configurable>org.gradle.plugins.fsm.TestConfigurable</configurable>
            </public>
        """.trimIndent())

        assertThat(inner).containsIgnoringWhitespaces("""
            <public>
            	<name>Test task with configurable</name>
            	<description>A task for test purposes</description>
            	<class>de.espirit.firstspirit.module.ScheduleTaskSpecification</class>
            	<configuration>
            		<application>org.gradle.plugins.fsm.TestScheduleTaskComponentWithConfigurable</application>
            	</configuration>
            	<configurable>org.gradle.plugins.fsm.TestConfigurable</configurable>
            </public>
        """.trimIndent())
    }

    private fun componentWithName(name: String): Node {
        return components.filter { it.childText("name") == name }.single()
    }
    
}