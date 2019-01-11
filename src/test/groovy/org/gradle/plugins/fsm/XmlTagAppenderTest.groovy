package org.gradle.plugins.fsm

import com.espirit.moddev.components.annotations.*
import de.espirit.firstspirit.server.module.ModuleInfo
import org.apache.commons.lang3.StringUtils
import org.assertj.core.api.SoftAssertions
import org.gradle.api.Project
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.plugins.fsm.configurations.FSMConfigurationsPlugin
import org.gradle.plugins.fsm.tasks.bundling.FSM
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert
import org.junit.Before
import org.junit.Test

import static org.assertj.core.api.Assertions.assertThat
import static org.gradle.plugins.fsm.ComponentHelper.createResource
import static org.gradle.plugins.fsm.ComponentHelper.createWebResource
import static org.gradle.plugins.fsm.configurations.FSMConfigurationsPlugin.FS_WEB_COMPILE_CONFIGURATION_NAME
import static org.mockito.Mockito.mock
import static org.mockito.Mockito.when

class XmlTagAppenderTest {

    static final String INDENT_WS_4 = XmlTagAppender.INDENT_WS_4
    static final String INDENT_WS_8 = XmlTagAppender.INDENT_WS_8
    static final String INDENT_WS_12___ = XmlTagAppender.INDENT_WS_12
    static final String INDENT_WS_16_______ = XmlTagAppender.INDENT_WS_16

    //blacklist contains test classes because of the restriction of allowing only 1 class to implement module
    static final List<String> MODULE_BLACKLIST = [TestModuleImplWithConfiguration.class.name,
                                                  TestModuleImpl.class.name]

    final List<String> componentImplementingClasses = [TestPublicComponent.getName(), TestScheduleTaskComponentWithConfigurable.getName(),
                                                       TestPublicComponentWithConfiguration.getName(), TestWebAppComponent.getName(),
                                                       TestProjectAppComponent.getName(), TestProjectAppComponentWithoutConfigurable.getName(),
                                                       TestScheduleTaskComponentWithForm.getName(), TestScheduleTaskComponentWithoutForm.getName(),
                                                       TestServiceComponent.getName(), TestServiceComponentWithoutConfigurable.getName(),
                                                       TestMinimalGadgetComponent.getName(), TestGadgetComponentWithOneFactory.getName(),
                                                       TestGadgetComponentWithMoreThanOneFactory.getName(), TestGadgetComponentWithAllAttributes.getName(),
                                                       TestGadgetComponentWithUnimplementedFactory.getName()]
    final List<String> validAndInvalidProjectAppClasses = [XmlTagAppender.PROJECT_APP_BLACKLIST, componentImplementingClasses].flatten()

    Project project

    private static final String NAME = "webapps-test-project"
    private static final String GROUP = "test"
    private static final String VERSION = "1.2"

    @Before
    void setUp() {

        project = ProjectBuilder.builder().withName(NAME).build()
        project.setGroup(GROUP)
        project.setVersion(VERSION)
        project.repositories.add(project.repositories.mavenCentral())
        project.pluginManager.apply(FSMPlugin)
        project.dependencies.add(FS_WEB_COMPILE_CONFIGURATION_NAME, "joda-time:joda-time:2.3")
        project.dependencies.add(FS_WEB_COMPILE_CONFIGURATION_NAME, "org.joda:joda-convert:2.1.1")
        project.getExtensions().getByType(FSMPluginExtension).setFsmDependencies(Arrays.asList('oneFSM', 'anotherFSM'))
    }

    /**
     * Explicitly tests pretty printing of components tag creation
     */
    @Test
    void testComponentsAppending_pretty_printing() throws Exception {
        StringBuilder result = new StringBuilder()
        def scannerResultProvider = new FSM.ClassScannerResultProvider() {
            @Override
            List<String> getNamesOfClassesImplementing(final Class<?> implementedInterface) {
                println implementedInterface
                return componentImplementingClasses
            }

            @Override
            List<String> getNamesOfClassesWithAnnotation(final Class<?> annotation) {
                if (PublicComponent.isAssignableFrom(annotation)) {
                    return [TestPublicComponent.getName(), TestPublicComponentWithConfiguration.getName()]
                }
                if (UrlFactoryComponent.isAssignableFrom(annotation)) {
                    return [TestUrlFactoryComponent.getName()] as List<String>
                }
                if (ScheduleTaskComponent.isAssignableFrom(annotation)) {
                    return [TestScheduleTaskComponentWithConfigurable.getName()]
                }
                if (GadgetComponent.isAssignableFrom(annotation)) {
                    return [TestGadgetComponentWithAllAttributes.getName()]
                }
                println annotation
                return []
            }
        }
        XmlTagAppender.appendComponentsTag(project, new URLClassLoader(new URL[0]), scannerResultProvider, false, result, true)
        /* explicitly test indent and pretty printing of components tag
        * within module.xml
        * <module>
        *     <components>
        *         <public/>
        *         <project-app/>
        */
        Assert.assertEquals("""
${INDENT_WS_8}<public>
${INDENT_WS_12___}<name>TestPublicComponentName</name>
${INDENT_WS_12___}<displayname>TestDisplayName</displayname>
${INDENT_WS_12___}<class>org.gradle.plugins.fsm.TestPublicComponent</class>
${INDENT_WS_8}</public>
${INDENT_WS_8}<public>
${INDENT_WS_12___}<name>TestPublicComponentWithConfigName</name>
${INDENT_WS_12___}<displayname>TestDisplayName</displayname>
${INDENT_WS_12___}<class>org.gradle.plugins.fsm.TestPublicComponentWithConfiguration</class>
${INDENT_WS_12___}<configurable>org.gradle.plugins.fsm.TestConfigurable</configurable>
${INDENT_WS_8}</public>
${INDENT_WS_8}<public>
${INDENT_WS_12___}<name>Test task</name>
${INDENT_WS_12___}<description>A task for test purpose</description>
${INDENT_WS_12___}<class>de.espirit.firstspirit.module.ScheduleTaskSpecification</class>
${INDENT_WS_12___}<configuration>
${INDENT_WS_16_______}<application>org.gradle.plugins.fsm.TestScheduleTaskComponentWithConfigurable</application>
${INDENT_WS_12___}</configuration>
${INDENT_WS_12___}<configurable>org.gradle.plugins.fsm.TestConfigurable</configurable>
${INDENT_WS_8}</public>
${INDENT_WS_8}<public>
${INDENT_WS_12___}<name>Test gadget</name>
${INDENT_WS_12___}<description>The description</description>
${INDENT_WS_12___}<class>de.espirit.firstspirit.module.GadgetSpecification</class>
${INDENT_WS_12___}<configuration>
${INDENT_WS_16_______}<gom>org.gradle.plugins.fsm.TestGadgetComponentWithAllAttributes</gom>
${INDENT_WS_16_______}<factory>org.gradle.plugins.fsm.TestGadgetFactoryOne</factory>
${INDENT_WS_16_______}<value>org.gradle.plugins.fsm.TestValueEngineerFactory</value>
${INDENT_WS_16_______}<scope data="yes" />
${INDENT_WS_12___}</configuration>
${INDENT_WS_8}</public>
${INDENT_WS_8}<public>
${INDENT_WS_12___}<name>TestUrlFactoryComponentName</name>
${INDENT_WS_12___}<displayname>TestDisplayName</displayname>
${INDENT_WS_12___}<description>TestDescription</description>
${INDENT_WS_12___}<class>de.espirit.firstspirit.generate.UrlCreatorSpecification</class>
${INDENT_WS_12___}<configuration>
${INDENT_WS_16_______}<UrlFactory>org.gradle.plugins.fsm.TestUrlFactoryComponent</UrlFactory>
${INDENT_WS_16_______}<UseRegistry>true</UseRegistry>
${INDENT_WS_12___}</configuration>
${INDENT_WS_8}</public>

${INDENT_WS_8}<service>
${INDENT_WS_12___}<name>TestServiceComponentName</name>
${INDENT_WS_12___}<displayname>TestDisplayName</displayname>
${INDENT_WS_12___}<description>TestDescription</description>
${INDENT_WS_12___}<class>org.gradle.plugins.fsm.TestServiceComponent</class>
${INDENT_WS_12___}<configurable>org.gradle.plugins.fsm.TestServiceComponent\$TestConfigurable</configurable>
${INDENT_WS_8}</service>

${INDENT_WS_8}<service>
${INDENT_WS_12___}<name>TestServiceComponentWithoutConfigurableName</name>
${INDENT_WS_12___}<displayname>TestDisplayName</displayname>
${INDENT_WS_12___}<description>TestDescription</description>
${INDENT_WS_12___}<class>org.gradle.plugins.fsm.TestServiceComponentWithoutConfigurable</class>
${INDENT_WS_8}</service>

${INDENT_WS_8}<project-app>
${INDENT_WS_12___}<name>TestProjectAppComponentName</name>
${INDENT_WS_12___}<displayname>TestDisplayName</displayname>
${INDENT_WS_12___}<description>TestDescription</description>
${INDENT_WS_12___}<class>org.gradle.plugins.fsm.TestProjectAppComponent</class>
${INDENT_WS_12___}<configurable>org.gradle.plugins.fsm.TestProjectAppComponent\$TestConfigurable</configurable>
${INDENT_WS_12___}<resources>
${INDENT_WS_16_______}<resource name="com.google.guava:guava" version="24.0" scope="module" mode="legacy">lib/guava-24.0.jar</resource>
${INDENT_WS_12___}</resources>
${INDENT_WS_8}</project-app>

${INDENT_WS_8}<project-app>
${INDENT_WS_12___}<name>TestProjectAppComponentWithoutConfigurableName</name>
${INDENT_WS_12___}<displayname>TestDisplayName</displayname>
${INDENT_WS_12___}<description>TestDescription</description>
${INDENT_WS_12___}<class>org.gradle.plugins.fsm.TestProjectAppComponentWithoutConfigurable</class>
${INDENT_WS_8}</project-app>

${INDENT_WS_8}<web-app scopes="project,global">
${INDENT_WS_12___}<name>TestWebAppComponentName</name>
${INDENT_WS_12___}<displayname>TestDisplayName</displayname>
${INDENT_WS_12___}<description>TestDescription</description>
${INDENT_WS_12___}<class>org.gradle.plugins.fsm.TestWebAppComponent</class>
${INDENT_WS_12___}<configurable>org.gradle.plugins.fsm.TestWebAppComponent\$TestConfigurable</configurable>
${INDENT_WS_12___}<web-xml>/web.xml</web-xml>
${INDENT_WS_12___}<web-resources>
${INDENT_WS_16_______}<resource name="webapps-test-project-1.2.jar" version="1.2">lib/webapps-test-project-1.2.jar</resource>

${INDENT_WS_16_______}<resource name="com.google.guava:guava" version="24.0">lib/guava-24.0.jar</resource>
${INDENT_WS_16_______}<resource name="org.apache.commons:commons-lang3" version="3.0" minVersion="2.9" maxVersion="3.1" target="targetPath">lib/commons-lang-3.0.jar</resource>

${INDENT_WS_16_______}<resource name="joda-time:joda-time" version="2.3">lib/joda-time-2.3.jar</resource>
${INDENT_WS_16_______}<resource name="org.joda:joda-convert" version="2.1.1">lib/joda-convert-2.1.1.jar</resource>
${INDENT_WS_12___}</web-resources>
${INDENT_WS_8}</web-app>
""".toString(), result.toString())
    }
    @Test(expected = IllegalStateException)
    void testModuleTagWithTwoClassesImpl() {
        StringBuilder result = new StringBuilder()
        def scannerResultProvider = new FSM.ClassScannerResultProvider() {
            @Override
            List<String> getNamesOfClassesImplementing(final Class<?> implementedInterface) {
                return ["org.some.class.implementing.module", "org.some.other.class.definitely.not.the.same.as.the.one.on.the.left"]
            }

            @Override
            List<String> getNamesOfClassesWithAnnotation(final Class<?> annotation) {
                return []
            }
        }
        XmlTagAppender.appendModuleAnnotationTags(new URLClassLoader(new URL[0], getClass().getClassLoader()), scannerResultProvider, result, XmlTagAppenderTest.MODULE_BLACKLIST)
    }

    @Test(expected = IllegalStateException)
    void testModuleTagWithTwoClassesAnnotated() {
        StringBuilder result = new StringBuilder()
        def scannerResultProvider = new FSM.ClassScannerResultProvider() {
            @Override
            List<String> getNamesOfClassesImplementing(final Class<?> implementedInterface) {
                return []
            }

            @Override
            List<String> getNamesOfClassesWithAnnotation(final Class<?> annotation) {
                return ["org.some.class.implementing.module", "org.some.other.class.definitely.not.the.same.as.the.one.on.the.left"]
            }
        }
        XmlTagAppender.appendModuleAnnotationTags(new URLClassLoader(new URL[0], getClass().getClassLoader()), scannerResultProvider, result, XmlTagAppenderTest.MODULE_BLACKLIST)
    }

    @Test
    void testModuleTagAppending() throws Exception {
        StringBuilder result = new StringBuilder()

        XmlTagAppender.appendModuleClassAndConfigTags(TestModuleImpl, result)

        Assert.assertEquals("""${INDENT_WS_4}<class>org.gradle.plugins.fsm.TestModuleImpl</class>""".toString(), result.toString())
    }


    @Test
    void testModuleAnnotationWithConfigurable(){
        StringBuilder result = new StringBuilder()

        XmlTagAppender.appendModuleClassAndConfigTags(TestModuleImplWithConfiguration, result)
        Assert.assertEquals("""${INDENT_WS_4}<class>org.gradle.plugins.fsm.TestModuleImplWithConfiguration</class>
${INDENT_WS_4}<configurable>org.gradle.plugins.fsm.TestConfigurable</configurable>""".toString(), result.toString())
    }

    @Test
    void testPublicComponentTagAppending() throws Exception {
        StringBuilder result = new StringBuilder()
        XmlTagAppender.appendPublicComponentTags(new URLClassLoader(new URL[0], getClass().getClassLoader()), [TestPublicComponent.getName()], result)

        Assert.assertEquals("""
${INDENT_WS_8}<public>
${INDENT_WS_12___}<name>TestPublicComponentName</name>
${INDENT_WS_12___}<displayname>TestDisplayName</displayname>
${INDENT_WS_12___}<class>org.gradle.plugins.fsm.TestPublicComponent</class>
${INDENT_WS_8}</public>""".toString(), result.toString())
    }


     @Test
    void testPublicComponentTagAppending_configurable() throws Exception {
        StringBuilder result = new StringBuilder()
        XmlTagAppender.appendPublicComponentTags(new URLClassLoader(new URL[0], getClass().getClassLoader()), [TestPublicComponentWithConfiguration.getName()], result)

        Assert.assertEquals("""
${INDENT_WS_8}<public>
${INDENT_WS_12___}<name>TestPublicComponentWithConfigName</name>
${INDENT_WS_12___}<displayname>TestDisplayName</displayname>
${INDENT_WS_12___}<class>org.gradle.plugins.fsm.TestPublicComponentWithConfiguration</class>
${INDENT_WS_12___}<configurable>org.gradle.plugins.fsm.TestConfigurable</configurable>
${INDENT_WS_8}</public>""".toString(), result.toString())
    }

    @Test
    void testScheduleTaskComponentTagAppendingWithForm() {
        StringBuilder result = new StringBuilder()
        XmlTagAppender.appendComponentTags(new URLClassLoader(new URL[0], getClass().getClassLoader()), [TestScheduleTaskComponentWithForm.getName()], result, ScheduleTaskComponent)

        Assert.assertEquals("""
${INDENT_WS_8}<public>
${INDENT_WS_12___}<name>Test task</name>
${INDENT_WS_12___}<description>A task for test purpose</description>
${INDENT_WS_12___}<class>de.espirit.firstspirit.module.ScheduleTaskSpecification</class>
${INDENT_WS_12___}<configuration>
${INDENT_WS_16_______}<application>org.gradle.plugins.fsm.TestScheduleTaskComponentWithForm</application>
${INDENT_WS_16_______}<form>org.gradle.plugins.fsm.TestScheduleTaskFormFactory</form>
${INDENT_WS_12___}</configuration>
${INDENT_WS_8}</public>""".toString(), result.toString())
    }



    @Test
    void testScheduleTaskComponentTagAppending_configurable() {
        StringBuilder result = new StringBuilder()
        XmlTagAppender.appendComponentTags(new URLClassLoader(new URL[0], getClass().getClassLoader()), [TestScheduleTaskComponentWithConfigurable.getName()], result, ScheduleTaskComponent)

        Assert.assertEquals("""
${INDENT_WS_8}<public>
${INDENT_WS_12___}<name>Test task</name>
${INDENT_WS_12___}<description>A task for test purpose</description>
${INDENT_WS_12___}<class>de.espirit.firstspirit.module.ScheduleTaskSpecification</class>
${INDENT_WS_12___}<configuration>
${INDENT_WS_16_______}<application>org.gradle.plugins.fsm.TestScheduleTaskComponentWithConfigurable</application>
${INDENT_WS_12___}</configuration>
${INDENT_WS_12___}<configurable>org.gradle.plugins.fsm.TestConfigurable</configurable>
${INDENT_WS_8}</public>""".toString(), result.toString())
    }

    @Test
    void testScheduleTaskComponentTagAppendingWithoutForm() {
        StringBuilder result = new StringBuilder()
        XmlTagAppender.appendComponentTags(new URLClassLoader(new URL[0], getClass().getClassLoader()),[TestScheduleTaskComponentWithoutForm.getName()], result, ScheduleTaskComponent)

        Assert.assertEquals("""
${INDENT_WS_8}<public>
${INDENT_WS_12___}<name>Test task</name>
${INDENT_WS_12___}<description>A task for test purpose</description>
${INDENT_WS_12___}<class>de.espirit.firstspirit.module.ScheduleTaskSpecification</class>
${INDENT_WS_12___}<configuration>
${INDENT_WS_16_______}<application>org.gradle.plugins.fsm.TestScheduleTaskComponentWithoutForm</application>
${INDENT_WS_12___}</configuration>
${INDENT_WS_8}</public>""".toString(), result.toString())
    }


    @Test
    void gadgetComponent_should_have_GadgetSpecification_as_class() {
        StringBuilder result = new StringBuilder()
        XmlTagAppender.appendComponentTags(new URLClassLoader(new URL[0], getClass().getClassLoader()), [TestMinimalGadgetComponent.getName()], result, GadgetComponent)
        assertThat(result.toString()).contains('<class>de.espirit.firstspirit.module.GadgetSpecification</class>')
    }

    @Test
    void gadgetComponent_should_have_annotated_class_as_gom() {
        StringBuilder result = new StringBuilder()
        XmlTagAppender.appendComponentTags(new URLClassLoader(new URL[0], getClass().getClassLoader()), [TestMinimalGadgetComponent.getName()], result, GadgetComponent)
        assertThat(result.toString()).contains("<gom>${TestMinimalGadgetComponent.class.getName()}</gom>")
    }


    @Test
    void gadgetComponent_should_have_GadgetScope_unrestricted_as_default() {
        StringBuilder result = new StringBuilder()
        XmlTagAppender.appendComponentTags(new URLClassLoader(new URL[0], getClass().getClassLoader()), [TestMinimalGadgetComponent.getName()], result, GadgetComponent)
        assertThat(result.toString()).contains("<scope unrestricted=\"yes\" />")
    }


    @Test
    void gadgetComponent_should_have_a_factory_tag_if_factory_is_set() {
        StringBuilder result = new StringBuilder()
        XmlTagAppender.appendComponentTags(new URLClassLoader(new URL[0], getClass().getClassLoader()), [TestGadgetComponentWithOneFactory.getName()], result, GadgetComponent)
        SoftAssertions softly = new SoftAssertions()
        softly.assertThat(result.toString()).contains('<factory>')
        softly.assertThat(result.toString()).contains('</factory>')
        softly.assertAll()
    }


    @Test
    void gadgetComponent_should_have_a_value_tag_if_value_factory_is_set() {
        StringBuilder result = new StringBuilder()
        XmlTagAppender.appendComponentTags(new URLClassLoader(new URL[0], getClass().getClassLoader()), [TestGadgetComponentWithAllAttributes.getName()], result, GadgetComponent)
        SoftAssertions softly = new SoftAssertions()
        softly.assertThat(result.toString()).contains('<value>')
        softly.assertThat(result.toString()).contains('</value>')
        softly.assertAll()
    }


    @Test
    void gadgetComponent_should_have_full_qualified_class_as_factory() {
        StringBuilder result = new StringBuilder()
        XmlTagAppender.appendComponentTags(new URLClassLoader(new URL[0], getClass().getClassLoader()), [TestGadgetComponentWithOneFactory.getName()], result, GadgetComponent)
        assertThat(result.toString()).contains("<factory>${TestGadgetFactoryOne.class.getName()}</factory>")
    }


    @Test
    void gadgetComponent_should_have_full_qualified_class_as_value() {
        StringBuilder result = new StringBuilder()
        XmlTagAppender.appendComponentTags(new URLClassLoader(new URL[0], getClass().getClassLoader()), [TestGadgetComponentWithAllAttributes.getName()], result, GadgetComponent)
        assertThat(result.toString()).contains("<value>${TestValueEngineerFactory.class.getName()}</value>")
    }


    @Test
    void gadgetComponent_should_have_factory_tag_for_each_factory() {
        StringBuilder result = new StringBuilder()
        XmlTagAppender.appendComponentTags(new URLClassLoader(new URL[0], getClass().getClassLoader()), [TestGadgetComponentWithMoreThanOneFactory.getName()], result, GadgetComponent)
        SoftAssertions softly = new SoftAssertions()
        int factoryOpenTags = StringUtils.countMatches(result.toString(), '<factory>')
        int factoryCloseTags = StringUtils.countMatches(result.toString(), '</factory>')
        softly.assertThat(factoryOpenTags).isEqualTo(2)
        softly.assertThat(factoryCloseTags).isEqualTo(2)
        softly.assertAll()
    }


    @Test
    void gadgetComponent_should_not_include_unimplemented_factory_class() {
        StringBuilder result = new StringBuilder()
        XmlTagAppender.appendComponentTags(new URLClassLoader(new URL[0], getClass().getClassLoader()), [TestGadgetComponentWithUnimplementedFactory.getName()], result, GadgetComponent)
        assertThat(result.toString()).doesNotContain('<factory>', '/factory>')
    }


    @Test
    void gadgetComponent_should_have_gom_factory_value_and_scope_within_configuration_tag() {
        StringBuilder result = new StringBuilder()
        XmlTagAppender.appendComponentTags(new URLClassLoader(new URL[0], getClass().getClassLoader()), [TestGadgetComponentWithAllAttributes.getName()], result, GadgetComponent)
        String resultString = result.toString()
        String configString = resultString.substring(resultString.indexOf('<configuration>'), resultString.indexOf('</configuration>')+'</configuration>'.length())
        StringBuilder regexBuilder = new StringBuilder();
        regexBuilder.append("<configuration>\\s+")
                .append("<gom>[\\w\\.]+</gom>\\s+").append("<factory>[\\w\\.]+</factory>\\s+")
                .append("<value>[\\w\\.]+</value>\\s+").append("<scope data=\"yes\" />\\s+</configuration>")
        assertThat(configString.matches(regexBuilder.toString())).isTrue()
    }


    @Test
    void appendWebAppTags_with_target_path() throws Exception {
        StringBuilder result = new StringBuilder()
        XmlTagAppender.appendWebAppTags(project, new URLClassLoader(new URL[0], getClass().getClassLoader()), componentImplementingClasses, result, true, true)

        // targetPath defined in WebResource annotation at {@link TestWebAppComponent}
        def targetPathValue = 'targetPath'

        Assert.assertEquals("""
${INDENT_WS_8}<web-app scopes="project,global">
${INDENT_WS_12___}<name>TestWebAppComponentName</name>
${INDENT_WS_12___}<displayname>TestDisplayName</displayname>
${INDENT_WS_12___}<description>TestDescription</description>
${INDENT_WS_12___}<class>org.gradle.plugins.fsm.TestWebAppComponent</class>
${INDENT_WS_12___}<configurable>org.gradle.plugins.fsm.TestWebAppComponent\$TestConfigurable</configurable>
${INDENT_WS_12___}<web-xml>/web.xml</web-xml>
${INDENT_WS_12___}<web-resources>
${INDENT_WS_16_______}<resource name="${XmlTagAppender.getJarFilename(project)}" version="${VERSION}">lib/$NAME-${VERSION}.jar</resource>

${INDENT_WS_16_______}<resource name="com.google.guava:guava" version="24.0">lib/guava-24.0.jar</resource>
${INDENT_WS_16_______}<resource name="org.apache.commons:commons-lang3" version="3.0" minVersion="2.9" maxVersion="3.1" target="${targetPathValue}">lib/commons-lang-3.0.jar</resource>

${INDENT_WS_16_______}<resource name="joda-time:joda-time" version="2.3" minVersion="2.3">lib/joda-time-2.3.jar</resource>
${INDENT_WS_16_______}<resource name="org.joda:joda-convert" version="2.1.1" minVersion="2.1.1">lib/joda-convert-2.1.1.jar</resource>
${INDENT_WS_12___}</web-resources>
${INDENT_WS_8}</web-app>
""".toString(), result.toString())
    }

    @Test
    void appendWebAppTagsWithoutConfig() throws Exception {
        StringBuilder result = new StringBuilder()
        XmlTagAppender.appendWebAppTags(project, new URLClassLoader(new URL[0], getClass().getClassLoader()), [TestWebAppComponentWithoutConfiguration.getName()], result, true, true)
        Assert.assertEquals("""
${INDENT_WS_8}<web-app scopes="project,global">
${INDENT_WS_12___}<name>TestWebAppComponentName</name>
${INDENT_WS_12___}<displayname>TestDisplayName</displayname>
${INDENT_WS_12___}<description>TestDescription</description>
${INDENT_WS_12___}<class>org.gradle.plugins.fsm.TestWebAppComponentWithoutConfiguration</class>
${INDENT_WS_12___}<web-xml>web0.xml</web-xml>
${INDENT_WS_12___}<web-resources>
${INDENT_WS_16_______}<resource name="${XmlTagAppender.getJarFilename(project)}" version="${VERSION}">lib/$NAME-${VERSION}.jar</resource>

${INDENT_WS_16_______}<resource name="com.google.guava:guava" version="24.0">lib/guava-24.0.jar</resource>
${INDENT_WS_16_______}<resource name="org.apache.commons:commons-lang3" version="3.0" minVersion="2.9" maxVersion="3.1">lib/commons-lang-3.0.jar</resource>

${INDENT_WS_16_______}<resource name="joda-time:joda-time" version="2.3" minVersion="2.3">lib/joda-time-2.3.jar</resource>
${INDENT_WS_16_______}<resource name="org.joda:joda-convert" version="2.1.1" minVersion="2.1.1">lib/joda-convert-2.1.1.jar</resource>
${INDENT_WS_12___}</web-resources>
${INDENT_WS_8}</web-app>
""".toString(), result.toString())
    }

    @Test
    void appendProjectAppTags() throws Exception {
        StringBuilder result = new StringBuilder()
        XmlTagAppender.appendProjectAppTags(project, new URLClassLoader(new URL[0], getClass().getClassLoader()), validAndInvalidProjectAppClasses, result)

        Assert.assertEquals("""
${INDENT_WS_8}<project-app>
${INDENT_WS_12___}<name>TestProjectAppComponentName</name>
${INDENT_WS_12___}<displayname>TestDisplayName</displayname>
${INDENT_WS_12___}<description>TestDescription</description>
${INDENT_WS_12___}<class>org.gradle.plugins.fsm.TestProjectAppComponent</class>
${INDENT_WS_12___}<configurable>org.gradle.plugins.fsm.TestProjectAppComponent\$TestConfigurable</configurable>
${INDENT_WS_12___}<resources>
${INDENT_WS_16_______}<resource name="com.google.guava:guava" version="24.0" scope="module" mode="legacy">lib/guava-24.0.jar</resource>
${INDENT_WS_12___}</resources>
${INDENT_WS_8}</project-app>

${INDENT_WS_8}<project-app>
${INDENT_WS_12___}<name>TestProjectAppComponentWithoutConfigurableName</name>
${INDENT_WS_12___}<displayname>TestDisplayName</displayname>
${INDENT_WS_12___}<description>TestDescription</description>
${INDENT_WS_12___}<class>org.gradle.plugins.fsm.TestProjectAppComponentWithoutConfigurable</class>
${INDENT_WS_8}</project-app>
""".toString(), result.toString())
    }

    @Test
    void project_app_output_should_have_no_configurable_tag_if_no_config_class_was_set() {
        StringBuilder result = new StringBuilder()
        XmlTagAppender.appendProjectAppTags(project, new URLClassLoader(new URL[0], getClass().getClassLoader()), [TestProjectAppComponentWithoutConfigurable.name], result)

        Assert.assertEquals("""
${INDENT_WS_8}<project-app>
${INDENT_WS_12___}<name>TestProjectAppComponentWithoutConfigurableName</name>
${INDENT_WS_12___}<displayname>TestDisplayName</displayname>
${INDENT_WS_12___}<description>TestDescription</description>
${INDENT_WS_12___}<class>org.gradle.plugins.fsm.TestProjectAppComponentWithoutConfigurable</class>
${INDENT_WS_8}</project-app>
""".toString(), result.toString())
    }

    @Test
    void appendServiceTags() throws Exception {
        StringBuilder result = new StringBuilder()
        XmlTagAppender.appendServiceTags( new URLClassLoader(new URL[0], getClass().getClassLoader()), [TestServiceComponent.getName()], result)

        Assert.assertEquals("""
${INDENT_WS_8}<service>
${INDENT_WS_12___}<name>TestServiceComponentName</name>
${INDENT_WS_12___}<displayname>TestDisplayName</displayname>
${INDENT_WS_12___}<description>TestDescription</description>
${INDENT_WS_12___}<class>org.gradle.plugins.fsm.TestServiceComponent</class>
${INDENT_WS_12___}<configurable>org.gradle.plugins.fsm.TestServiceComponent\$TestConfigurable</configurable>
${INDENT_WS_8}</service>
""".toString(), result.toString())
    }


    @Test
    void service_output_should_have_no_configurable_tag_if_no_config_class_was_set() {
        StringBuilder result = new StringBuilder()
        XmlTagAppender.appendServiceTags(new URLClassLoader(new URL[0], getClass().getClassLoader()), [TestServiceComponentWithoutConfigurable.name], result)

        Assert.assertEquals("""
${INDENT_WS_8}<service>
${INDENT_WS_12___}<name>TestServiceComponentWithoutConfigurableName</name>
${INDENT_WS_12___}<displayname>TestDisplayName</displayname>
${INDENT_WS_12___}<description>TestDescription</description>
${INDENT_WS_12___}<class>org.gradle.plugins.fsm.TestServiceComponentWithoutConfigurable</class>
${INDENT_WS_8}</service>
""".toString(), result.toString())
    }


    @Test
    void appendUrlCreatorTags() throws Exception {
        StringBuilder result = new StringBuilder()
        XmlTagAppender.appendUrlCreatorTags( new URLClassLoader(new URL[0], getClass().getClassLoader()), [TestUrlFactoryComponent.getName()], result)

        Assert.assertEquals("""
${INDENT_WS_8}<public>
${INDENT_WS_12___}<name>TestUrlFactoryComponentName</name>
${INDENT_WS_12___}<displayname>TestDisplayName</displayname>
${INDENT_WS_12___}<description>TestDescription</description>
${INDENT_WS_12___}<class>de.espirit.firstspirit.generate.UrlCreatorSpecification</class>
${INDENT_WS_12___}<configuration>
${INDENT_WS_16_______}<UrlFactory>org.gradle.plugins.fsm.TestUrlFactoryComponent</UrlFactory>
${INDENT_WS_16_______}<UseRegistry>true</UseRegistry>
${INDENT_WS_12___}</configuration>
${INDENT_WS_8}</public>
""".toString(), result.toString())
    }

    @Test
    void getResourceTagForDependency() throws Exception {
        ResolvedArtifact resolvedArtifact = createArtifactMock()
        ModuleVersionIdentifier moduleVersionIdentifier = createVersionIdentifierMock("mygroup", "myname", "1.0.0")

        def indent = INDENT_WS_8
        String result = XmlTagAppender.getResourceTagForDependency(indent, moduleVersionIdentifier, resolvedArtifact, "server", ModuleInfo.Mode.ISOLATED, true)
        Assert.assertEquals("""${indent}<resource name="mygroup:myname" scope="server" mode="isolated" version="1.0.0" minVersion="1.0.0">lib/myname-1.0.0.jar</resource>""".toString(), result)
    }

    @Test
    void getResourceTagForDependencyWithMinMaxVersion() throws Exception {
        ResolvedArtifact resolvedArtifact = createArtifactMock()
        ModuleVersionIdentifier moduleVersionIdentifier = createVersionIdentifierMock("mygroup", "myname", "1.0.0")
        def minMaxVersions = new HashSet<FSMConfigurationsPlugin.MinMaxVersion>()
        minMaxVersions.add(new FSMConfigurationsPlugin.MinMaxVersion(dependency: "mygroup:myname:1.0.0", minVersion: "0.9", maxVersion: "1.1.0"))

        def indent = INDENT_WS_8
        String result = XmlTagAppender.getResourceTagForDependency(indent, moduleVersionIdentifier, resolvedArtifact, "server", ModuleInfo.Mode.ISOLATED, true, minMaxVersions)
        Assert.assertEquals("""${indent}<resource name="mygroup:myname" scope="server" mode="isolated" version="1.0.0" minVersion="0.9" maxVersion="1.1.0">lib/myname-1.0.0.jar</resource>""".toString(), result)
    }

    @Test
    void getResourceTagForDependencyWithNullMinMaxVersions() throws Exception {
        ResolvedArtifact resolvedArtifact = createArtifactMock()
        ModuleVersionIdentifier moduleVersionIdentifier = createVersionIdentifierMock("mygroup", "myname", "1.0.0")
        def minMaxVersions = new HashSet<FSMConfigurationsPlugin.MinMaxVersion>()
        minMaxVersions.add(new FSMConfigurationsPlugin.MinMaxVersion(dependency: "mygroup:myname:1.0.0", maxVersion: "1.1.0"))

        def indent = INDENT_WS_8
        String result = XmlTagAppender.getResourceTagForDependency(indent, moduleVersionIdentifier, resolvedArtifact, "server", ModuleInfo.Mode.ISOLATED, false, minMaxVersions)
        Assert.assertEquals("""${indent}<resource name="mygroup:myname" scope="server" mode="isolated" version="1.0.0" maxVersion="1.1.0">lib/myname-1.0.0.jar</resource>""".toString(), result)
    }

    @Test
    void getResourcesTags() {
        String result = XmlTagAppender.getResourcesTags(project, new XmlTagAppender.WebXmlPaths(), ModuleInfo.Mode.ISOLATED, false)
        Assert.assertEquals("""${INDENT_WS_8}<resource name="${XmlTagAppender.getJarFilename(project)}" version="$VERSION" scope="module" mode="isolated">lib/$NAME-${VERSION}.jar</resource>""".toString(), result)
    }

    @Test
    void testProjectPropertyInterpolationInWebappResources() {
        WebAppComponent annotation = new CustomWebAppComponent() {
            @Override
            WebResource[] webResources() {
                return [createWebResource("/abc/nonexistent.txt", "\${project.group}-\${project.name}", "\${project.myCustomVersionPropertyString}"),
                        createWebResource("/abc/nonexistent2.txt", "\${project.group}-\${project.name}", "\${project.version}")]
            }
        }
        project.group = "myGroup"
        project.version = "1.0.2"
        project.ext.myCustomVersionPropertyString = "5"
        project.ext.myCustomVersionPropertyInt = 4

        String webAppComponentTagString = XmlTagAppender.evaluateResources(annotation,"", project)
        Assert.assertNotNull(webAppComponentTagString)
        Assert.assertEquals("""<resource name="myGroup-webapps-test-project" version="5" minVersion="myMinVersion" maxVersion="myMaxVersion" target="myTargetPath">/abc/nonexistent.txt</resource>
<resource name="myGroup-webapps-test-project" version="1.0.2" minVersion="myMinVersion" maxVersion="myMaxVersion" target="myTargetPath">/abc/nonexistent2.txt</resource>""", webAppComponentTagString)
    }

    @Test
    void testResourcePropertyInterpolationInWebappResources() {
        WebAppComponent annotation = new CustomWebAppComponent() {
            @Override
            WebResource[] webResources() {
                return [createWebResource("\${path}", "\${project.jodaConvertDependency}", "\$version")]
            }
        }

        project.ext.jodaConvertDependency = "org.joda:joda-convert"
        project.dependencies.add(FSMConfigurationsPlugin.FS_MODULE_COMPILE_CONFIGURATION_NAME, "org.joda:joda-convert:2.1.1")

        String webAppComponentTagString = XmlTagAppender.evaluateResources(annotation,"", project)
        Assert.assertNotNull(webAppComponentTagString)
        Assert.assertEquals("""<resource name="org.joda:joda-convert" version="2.1.1" minVersion="myMinVersion" maxVersion="myMaxVersion" target="myTargetPath">lib/joda-convert-2.1.1.jar</resource>""", webAppComponentTagString)
    }

    @Test
    void testResourcePropertyInterpolationInProjectAppResources() {
        ProjectAppComponent annotation = new CustomProjectAppComponent() {
            @Override
            Resource[] resources() {
                return [createResource("\$path", "\${project.jodaConvertDependency}", "\$version")]
            }
        }

        project.ext.jodaConvertDependency = "org.joda:joda-convert"

        project.dependencies.add(FSMConfigurationsPlugin.FS_MODULE_COMPILE_CONFIGURATION_NAME, "org.joda:joda-convert:2.1.1")

        String projectAppComponentTagString = XmlTagAppender.evaluateResources(annotation,"", project)
        Assert.assertNotNull(projectAppComponentTagString)
        Assert.assertEquals("""<resource name="org.joda:joda-convert" version="2.1.1" minVersion="myMinVersion" maxVersion="myMaxVersion" scope="module" mode="isolated">lib/joda-convert-2.1.1.jar</resource>""", projectAppComponentTagString)
    }

    @Test
    void getFsmDependencyTags() {
        def beginsWithANewLineAndIndentation = "\n" + """${INDENT_WS_8}"""
        def firstFsmDependency = beginsWithANewLineAndIndentation + "<dependency>oneFSM</dependency>"
        def secondFsmDependency = beginsWithANewLineAndIndentation + "<dependency>anotherFSM</dependency>"

        String result = XmlTagAppender.getFsmDependencyTags(project)
        Assert.assertEquals(firstFsmDependency + secondFsmDependency, result)
    }


    @Test
    void resolveScopeResourceConflict() {
        project.dependencies.add(FSMConfigurationsPlugin.FS_MODULE_COMPILE_CONFIGURATION_NAME, "org.joda:joda-convert:2.1.1")
        project.dependencies.add(FSMConfigurationsPlugin.FS_SERVER_COMPILE_CONFIGURATION_NAME, "org.joda:joda-convert:2.1.1")
        project.dependencies.add(FSMConfigurationsPlugin.FS_MODULE_COMPILE_CONFIGURATION_NAME, "org.slf4j:slf4j-api:1.7.25")
        project.dependencies.add(FSMConfigurationsPlugin.FS_SERVER_COMPILE_CONFIGURATION_NAME, "org.slf4j:slf4j-api:1.7.25")
        String result = XmlTagAppender.getResourcesTags(project, new XmlTagAppender.WebXmlPaths(), ModuleInfo.Mode.ISOLATED, false)
        Assert.assertEquals("""${INDENT_WS_8}<resource name="${XmlTagAppender.getJarFilename(project)}" version="$VERSION" scope="module" mode="isolated">lib/$NAME-${VERSION}.jar</resource>
${INDENT_WS_8}<resource name="org.joda:joda-convert" scope="server" mode="isolated" version="2.1.1">lib/joda-convert-2.1.1.jar</resource>
${INDENT_WS_8}<resource name="org.slf4j:slf4j-api" scope="server" mode="isolated" version="1.7.25">lib/slf4j-api-1.7.25.jar</resource>""".toString(), result)
    }


    private static ResolvedArtifact createArtifactMock() {
        def resolvedArtifact = mock(ResolvedArtifact)
        when(resolvedArtifact.extension).thenReturn("jar")
        resolvedArtifact
    }

    private static ModuleVersionIdentifier createVersionIdentifierMock(String group, String module, String version) {
        def moduleVersionIdentifier = mock(ModuleVersionIdentifier)
        when(moduleVersionIdentifier.group).thenReturn(group)
        when(moduleVersionIdentifier.name).thenReturn(module)
        when(moduleVersionIdentifier.version).thenReturn(version)
        moduleVersionIdentifier
    }
}
