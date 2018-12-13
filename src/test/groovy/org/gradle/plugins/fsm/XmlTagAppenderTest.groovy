package org.gradle.plugins.fsm

import de.espirit.firstspirit.access.Language
import de.espirit.firstspirit.access.project.Resolution
import de.espirit.firstspirit.access.project.TemplateSet
import de.espirit.firstspirit.access.store.ContentProducer
import de.espirit.firstspirit.access.store.PageParams
import de.espirit.firstspirit.access.store.mediastore.Media
import de.espirit.firstspirit.generate.PathLookup
import de.espirit.firstspirit.generate.UrlFactory
import com.espirit.moddev.components.annotations.ProjectAppComponent
import com.espirit.moddev.components.annotations.PublicComponent
import com.espirit.moddev.components.annotations.ServiceComponent
import com.espirit.moddev.components.annotations.Resource
import com.espirit.moddev.components.annotations.WebResource
import com.espirit.moddev.components.annotations.ScheduleTaskComponent
import com.espirit.moddev.components.annotations.UrlFactoryComponent
import com.espirit.moddev.components.annotations.WebAppComponent
import com.espirit.moddev.components.annotations.ModuleComponent
import de.espirit.firstspirit.agency.SpecialistsBroker
import de.espirit.firstspirit.module.Configuration
import de.espirit.firstspirit.module.ServerEnvironment
import de.espirit.firstspirit.scheduling.ScheduleTaskForm
import de.espirit.firstspirit.scheduling.ScheduleTaskFormFactory
import de.espirit.firstspirit.server.module.ModuleInfo
import de.espirit.firstspirit.module.Module
import de.espirit.firstspirit.module.descriptor.ModuleDescriptor
import org.gradle.api.Project
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.plugins.fsm.tasks.bundling.FSM
import org.gradle.plugins.fsm.util.BaseConfiguration
import org.gradle.plugins.fsm.util.BaseProjectApp
import org.gradle.plugins.fsm.util.BaseService
import org.gradle.plugins.fsm.util.BaseWebApp
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert
import org.junit.Before
import org.junit.Test

import javax.swing.JComponent
import java.awt.Frame

import static org.mockito.Mockito.mock
import static org.mockito.Mockito.when

class XmlTagAppenderTest {

    static final String INDENT_WS_4 = XmlTagAppender.INDENT_WS_4
    static final String INDENT_WS_8 = XmlTagAppender.INDENT_WS_8
    static final String INDENT_WS_12___ = XmlTagAppender.INDENT_WS_12
    static final String INDENT_WS_16_______ = XmlTagAppender.INDENT_WS_16

    final List<String> componentImplementingClasses = [TestPublicComponent.getName(), TestScheduleTaskComponentWithConfigurable.getName(),
                                                       TestPublicComponentWithConfiguration.getName(), TestWebAppComponent.getName(),
                                                       TestProjectAppComponent.getName(), TestProjectAppComponentWithoutConfigurable.getName(),
                                                       TestScheduleTaskComponentWithForm.getName(), TestScheduleTaskComponentWithoutForm.getName(),
                                                       TestServiceComponent.getName(), TestServiceComponentWithoutConfigurable.getName()]
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
        project.dependencies.add(FSMPlugin.FS_WEB_COMPILE_CONFIGURATION_NAME, "joda-time:joda-time:2.3")
        project.dependencies.add(FSMPlugin.FS_WEB_COMPILE_CONFIGURATION_NAME, "org.joda:joda-convert:2.1.1")
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
${INDENT_WS_12___}<class>org.gradle.plugins.fsm.XmlTagAppenderTest\$TestPublicComponent</class>
${INDENT_WS_8}</public>
${INDENT_WS_8}<public>
${INDENT_WS_12___}<name>TestPublicComponentWithConfigName</name>
${INDENT_WS_12___}<displayname>TestDisplayName</displayname>
${INDENT_WS_12___}<class>org.gradle.plugins.fsm.XmlTagAppenderTest\$TestPublicComponentWithConfiguration</class>
${INDENT_WS_12___}<configurable>org.gradle.plugins.fsm.XmlTagAppenderTest\$TestConfigurable</configurable>
${INDENT_WS_8}</public>
${INDENT_WS_8}<public>
${INDENT_WS_12___}<name>Test task</name>
${INDENT_WS_12___}<description>A task for test purpose</description>
${INDENT_WS_12___}<class>de.espirit.firstspirit.module.ScheduleTaskSpecification</class>
${INDENT_WS_12___}<configuration>
${INDENT_WS_16_______}<application>org.gradle.plugins.fsm.XmlTagAppenderTest\$TestScheduleTaskComponentWithConfigurable</application>
${INDENT_WS_12___}</configuration>
${INDENT_WS_12___}<configurable>org.gradle.plugins.fsm.XmlTagAppenderTest\$TestConfigurable</configurable>
${INDENT_WS_8}</public>
${INDENT_WS_8}<public>
${INDENT_WS_12___}<name>TestUrlFactoryComponentName</name>
${INDENT_WS_12___}<displayname>TestDisplayName</displayname>
${INDENT_WS_12___}<description>TestDescription</description>
${INDENT_WS_12___}<class>de.espirit.firstspirit.generate.UrlCreatorSpecification</class>
${INDENT_WS_12___}<configuration>
${INDENT_WS_16_______}<UrlFactory>org.gradle.plugins.fsm.XmlTagAppenderTest\$TestUrlFactoryComponent</UrlFactory>
${INDENT_WS_16_______}<UseRegistry>true</UseRegistry>
${INDENT_WS_12___}</configuration>
${INDENT_WS_8}</public>

${INDENT_WS_8}<service>
${INDENT_WS_12___}<name>TestServiceComponentName</name>
${INDENT_WS_12___}<displayname>TestDisplayName</displayname>
${INDENT_WS_12___}<description>TestDescription</description>
${INDENT_WS_12___}<class>org.gradle.plugins.fsm.XmlTagAppenderTest\$TestServiceComponent</class>
${INDENT_WS_12___}<configurable>org.gradle.plugins.fsm.XmlTagAppenderTest\$TestServiceComponent\$TestConfigurable</configurable>
${INDENT_WS_8}</service>

${INDENT_WS_8}<service>
${INDENT_WS_12___}<name>TestServiceComponentWithoutConfigurableName</name>
${INDENT_WS_12___}<displayname>TestDisplayName</displayname>
${INDENT_WS_12___}<description>TestDescription</description>
${INDENT_WS_12___}<class>${getClass().name}\$TestServiceComponentWithoutConfigurable</class>
${INDENT_WS_8}</service>

${INDENT_WS_8}<project-app>
${INDENT_WS_12___}<name>TestProjectAppComponentName</name>
${INDENT_WS_12___}<displayname>TestDisplayName</displayname>
${INDENT_WS_12___}<description>TestDescription</description>
${INDENT_WS_12___}<class>org.gradle.plugins.fsm.XmlTagAppenderTest\$TestProjectAppComponent</class>
${INDENT_WS_12___}<configurable>org.gradle.plugins.fsm.XmlTagAppenderTest\$TestProjectAppComponent\$TestConfigurable</configurable>
${INDENT_WS_12___}<resources>
${INDENT_WS_16_______}<resource name="com.google.guava:guava" version="24.0" scope="MODULE" mode="LEGACY">lib/guava-24.0.jar</resource>
${INDENT_WS_12___}</resources>
${INDENT_WS_8}</project-app>

${INDENT_WS_8}<project-app>
${INDENT_WS_12___}<name>TestProjectAppComponentWithoutConfigurableName</name>
${INDENT_WS_12___}<displayname>TestDisplayName</displayname>
${INDENT_WS_12___}<description>TestDescription</description>
${INDENT_WS_12___}<class>${getClass().name}\$TestProjectAppComponentWithoutConfigurable</class>
${INDENT_WS_8}</project-app>

${INDENT_WS_8}<web-app scopes="project,global">
${INDENT_WS_12___}<name>TestWebAppComponentName</name>
${INDENT_WS_12___}<displayname>TestDisplayName</displayname>
${INDENT_WS_12___}<description>TestDescription</description>
${INDENT_WS_12___}<class>org.gradle.plugins.fsm.XmlTagAppenderTest\$TestWebAppComponent</class>
${INDENT_WS_12___}<configurable>org.gradle.plugins.fsm.XmlTagAppenderTest\$TestWebAppComponent\$TestConfigurable</configurable>
${INDENT_WS_12___}<web-xml>/test/web.xml</web-xml>
${INDENT_WS_12___}<web-resources>
${INDENT_WS_16_______}<resource name="webapps-test-project-1.2.jar" version="1.2">lib/webapps-test-project-1.2.jar</resource>
${INDENT_WS_16_______}<resource>/test/web.xml</resource>
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
        XmlTagAppender.appendModuleAnnotationTags(new URLClassLoader(new URL[0], getClass().getClassLoader()), scannerResultProvider, result)
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
        XmlTagAppender.appendModuleAnnotationTags(new URLClassLoader(new URL[0], getClass().getClassLoader()), scannerResultProvider, result)
    }

    @Test
    void testModuleTagAppending() throws Exception {
        StringBuilder result = new StringBuilder()

        XmlTagAppender.appendModuleClassAndConfigTags(TestModuleImpl, result)

        Assert.assertEquals("""${INDENT_WS_4}<class>org.gradle.plugins.fsm.XmlTagAppenderTest\$TestModuleImpl</class>""".toString(), result.toString())
    }


    @Test
    void testModuleAnnotationWithConfigurable(){
        StringBuilder result = new StringBuilder()

        XmlTagAppender.appendModuleClassAndConfigTags(TestModuleImplWithConfiguration, result)
        Assert.assertEquals("""${INDENT_WS_4}<class>org.gradle.plugins.fsm.XmlTagAppenderTest\$TestModuleImplWithConfiguration</class>
${INDENT_WS_4}<configurable>org.gradle.plugins.fsm.XmlTagAppenderTest\$TestConfigurable</configurable>""".toString(), result.toString())
    }

    @Test
    void testPublicComponentTagAppending() throws Exception {
        StringBuilder result = new StringBuilder()
        XmlTagAppender.appendPublicComponentTags(new URLClassLoader(new URL[0], getClass().getClassLoader()), [TestPublicComponent.getName()], result)

        Assert.assertEquals("""
${INDENT_WS_8}<public>
${INDENT_WS_12___}<name>TestPublicComponentName</name>
${INDENT_WS_12___}<displayname>TestDisplayName</displayname>
${INDENT_WS_12___}<class>org.gradle.plugins.fsm.XmlTagAppenderTest\$TestPublicComponent</class>
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
${INDENT_WS_12___}<class>org.gradle.plugins.fsm.XmlTagAppenderTest\$TestPublicComponentWithConfiguration</class>
${INDENT_WS_12___}<configurable>org.gradle.plugins.fsm.XmlTagAppenderTest\$TestConfigurable</configurable>
${INDENT_WS_8}</public>""".toString(), result.toString())
    }

    @Test
    void testScheduleTaskComponentTagAppendingWithForm() {
        StringBuilder result = new StringBuilder()
        XmlTagAppender.appendScheduleTaskComponentTags(new URLClassLoader(new URL[0], getClass().getClassLoader()), [TestScheduleTaskComponentWithForm.getName()], result)

        Assert.assertEquals("""
${INDENT_WS_8}<public>
${INDENT_WS_12___}<name>Test task</name>
${INDENT_WS_12___}<description>A task for test purpose</description>
${INDENT_WS_12___}<class>de.espirit.firstspirit.module.ScheduleTaskSpecification</class>
${INDENT_WS_12___}<configuration>
${INDENT_WS_16_______}<application>org.gradle.plugins.fsm.XmlTagAppenderTest\$TestScheduleTaskComponentWithForm</application>
${INDENT_WS_16_______}<form>org.gradle.plugins.fsm.XmlTagAppenderTest\$TestScheduleTaskFormFactory</form>
${INDENT_WS_12___}</configuration>
${INDENT_WS_8}</public>""".toString(), result.toString())
    }



    @Test
    void testScheduleTaskComponentTagAppending_configurable() {
        StringBuilder result = new StringBuilder()
        XmlTagAppender.appendScheduleTaskComponentTags(new URLClassLoader(new URL[0], getClass().getClassLoader()), [TestScheduleTaskComponentWithConfigurable.getName()], result)

        Assert.assertEquals("""
${INDENT_WS_8}<public>
${INDENT_WS_12___}<name>Test task</name>
${INDENT_WS_12___}<description>A task for test purpose</description>
${INDENT_WS_12___}<class>de.espirit.firstspirit.module.ScheduleTaskSpecification</class>
${INDENT_WS_12___}<configuration>
${INDENT_WS_16_______}<application>org.gradle.plugins.fsm.XmlTagAppenderTest\$TestScheduleTaskComponentWithConfigurable</application>
${INDENT_WS_12___}</configuration>
${INDENT_WS_12___}<configurable>org.gradle.plugins.fsm.XmlTagAppenderTest\$TestConfigurable</configurable>
${INDENT_WS_8}</public>""".toString(), result.toString())
    }

    @Test
    void testScheduleTaskComponentTagAppendingWithoutForm() {
        StringBuilder result = new StringBuilder()
        XmlTagAppender.appendScheduleTaskComponentTags(new URLClassLoader(new URL[0], getClass().getClassLoader()),[TestScheduleTaskComponentWithoutForm.getName()], result)

        Assert.assertEquals("""
${INDENT_WS_8}<public>
${INDENT_WS_12___}<name>Test task</name>
${INDENT_WS_12___}<description>A task for test purpose</description>
${INDENT_WS_12___}<class>de.espirit.firstspirit.module.ScheduleTaskSpecification</class>
${INDENT_WS_12___}<configuration>
${INDENT_WS_16_______}<application>org.gradle.plugins.fsm.XmlTagAppenderTest\$TestScheduleTaskComponentWithoutForm</application>
${INDENT_WS_12___}</configuration>
${INDENT_WS_8}</public>""".toString(), result.toString())
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
${INDENT_WS_12___}<class>org.gradle.plugins.fsm.XmlTagAppenderTest\$TestWebAppComponent</class>
${INDENT_WS_12___}<configurable>org.gradle.plugins.fsm.XmlTagAppenderTest\$TestWebAppComponent\$TestConfigurable</configurable>
${INDENT_WS_12___}<web-xml>/test/web.xml</web-xml>
${INDENT_WS_12___}<web-resources>
${INDENT_WS_16_______}<resource name="${XmlTagAppender.getJarFilename(project)}" version="${VERSION}">lib/$NAME-${VERSION}.jar</resource>
${INDENT_WS_16_______}<resource>/test/web.xml</resource>
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
${INDENT_WS_12___}<class>org.gradle.plugins.fsm.XmlTagAppenderTest\$TestWebAppComponentWithoutConfiguration</class>
${INDENT_WS_12___}<web-xml>/test/web.xml</web-xml>
${INDENT_WS_12___}<web-resources>
${INDENT_WS_16_______}<resource name="${XmlTagAppender.getJarFilename(project)}" version="${VERSION}">lib/$NAME-${VERSION}.jar</resource>
${INDENT_WS_16_______}<resource>/test/web.xml</resource>
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
        XmlTagAppender.appendProjectAppTags( new URLClassLoader(new URL[0], getClass().getClassLoader()), validAndInvalidProjectAppClasses, result)

        Assert.assertEquals("""
${INDENT_WS_8}<project-app>
${INDENT_WS_12___}<name>TestProjectAppComponentName</name>
${INDENT_WS_12___}<displayname>TestDisplayName</displayname>
${INDENT_WS_12___}<description>TestDescription</description>
${INDENT_WS_12___}<class>org.gradle.plugins.fsm.XmlTagAppenderTest\$TestProjectAppComponent</class>
${INDENT_WS_12___}<configurable>org.gradle.plugins.fsm.XmlTagAppenderTest\$TestProjectAppComponent\$TestConfigurable</configurable>
${INDENT_WS_12___}<resources>
${INDENT_WS_16_______}<resource name="com.google.guava:guava" version="24.0" scope="MODULE" mode="LEGACY">lib/guava-24.0.jar</resource>
${INDENT_WS_12___}</resources>
${INDENT_WS_8}</project-app>

${INDENT_WS_8}<project-app>
${INDENT_WS_12___}<name>TestProjectAppComponentWithoutConfigurableName</name>
${INDENT_WS_12___}<displayname>TestDisplayName</displayname>
${INDENT_WS_12___}<description>TestDescription</description>
${INDENT_WS_12___}<class>${getClass().name}\$TestProjectAppComponentWithoutConfigurable</class>
${INDENT_WS_8}</project-app>
""".toString(), result.toString())
    }

    @Test
    void project_app_output_should_have_no_configurable_tag_if_no_config_class_was_set() {
        StringBuilder result = new StringBuilder()
        XmlTagAppender.appendProjectAppTags(new URLClassLoader(new URL[0], getClass().getClassLoader()), [TestProjectAppComponentWithoutConfigurable.name], result)

        Assert.assertEquals("""
${INDENT_WS_8}<project-app>
${INDENT_WS_12___}<name>TestProjectAppComponentWithoutConfigurableName</name>
${INDENT_WS_12___}<displayname>TestDisplayName</displayname>
${INDENT_WS_12___}<description>TestDescription</description>
${INDENT_WS_12___}<class>${getClass().name}\$TestProjectAppComponentWithoutConfigurable</class>
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
${INDENT_WS_12___}<class>org.gradle.plugins.fsm.XmlTagAppenderTest\$TestServiceComponent</class>
${INDENT_WS_12___}<configurable>org.gradle.plugins.fsm.XmlTagAppenderTest\$TestServiceComponent\$TestConfigurable</configurable>
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
${INDENT_WS_12___}<class>${getClass().name}\$TestServiceComponentWithoutConfigurable</class>
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
${INDENT_WS_16_______}<UrlFactory>org.gradle.plugins.fsm.XmlTagAppenderTest\$TestUrlFactoryComponent</UrlFactory>
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
        def minMaxVersions = new HashSet<FSMPlugin.MinMaxVersion>()
        minMaxVersions.add(new FSMPlugin.MinMaxVersion(dependency: "mygroup:myname:1.0.0", minVersion: "0.9", maxVersion: "1.1.0"))

        def indent = INDENT_WS_8
        String result = XmlTagAppender.getResourceTagForDependency(indent, moduleVersionIdentifier, resolvedArtifact, "server", ModuleInfo.Mode.ISOLATED, true, minMaxVersions)
        Assert.assertEquals("""${indent}<resource name="mygroup:myname" scope="server" mode="isolated" version="1.0.0" minVersion="0.9" maxVersion="1.1.0">lib/myname-1.0.0.jar</resource>""".toString(), result)
    }

    @Test
    void getResourceTagForDependencyWithNullMinMaxVersions() throws Exception {
        ResolvedArtifact resolvedArtifact = createArtifactMock()
        ModuleVersionIdentifier moduleVersionIdentifier = createVersionIdentifierMock("mygroup", "myname", "1.0.0")
        def minMaxVersions = new HashSet<FSMPlugin.MinMaxVersion>()
        minMaxVersions.add(new FSMPlugin.MinMaxVersion(dependency: "mygroup:myname:1.0.0", maxVersion: "1.1.0"))

        def indent = INDENT_WS_8
        String result = XmlTagAppender.getResourceTagForDependency(indent, moduleVersionIdentifier, resolvedArtifact, "server", ModuleInfo.Mode.ISOLATED, false, minMaxVersions)
        Assert.assertEquals("""${indent}<resource name="mygroup:myname" scope="server" mode="isolated" version="1.0.0" maxVersion="1.1.0">lib/myname-1.0.0.jar</resource>""".toString(), result)
    }

    @Test
    void getResourcesTags() {
        String result = XmlTagAppender.getResourcesTags(project, ModuleInfo.Mode.ISOLATED, false)
        Assert.assertEquals("""${INDENT_WS_8}<resource name="${XmlTagAppender.getJarFilename(project)}" version="$VERSION" scope="module" mode="isolated">lib/$NAME-${VERSION}.jar</resource>""".toString(), result)
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
        project.dependencies.add(FSMPlugin.FS_MODULE_COMPILE_CONFIGURATION_NAME, "org.joda:joda-convert:2.1.1")
        project.dependencies.add(FSMPlugin.FS_SERVER_COMPILE_CONFIGURATION_NAME, "org.joda:joda-convert:2.1.1")
        project.dependencies.add(FSMPlugin.FS_MODULE_COMPILE_CONFIGURATION_NAME, "org.slf4j:slf4j-api:1.7.25")
        project.dependencies.add(FSMPlugin.FS_SERVER_COMPILE_CONFIGURATION_NAME, "org.slf4j:slf4j-api:1.7.25")
        String result = XmlTagAppender.getResourcesTags(project, ModuleInfo.Mode.ISOLATED, false)
        Assert.assertEquals("""${INDENT_WS_8}<resource name="${XmlTagAppender.getJarFilename(project)}" version="$VERSION" scope="module" mode="isolated">lib/$NAME-${VERSION}.jar</resource>
${INDENT_WS_8}<resource name="org.joda:joda-convert" scope="server" mode="isolated" version="2.1.1">lib/joda-convert-2.1.1.jar</resource>
${INDENT_WS_8}<resource name="org.slf4j:slf4j-api" scope="server" mode="isolated" version="1.7.25">lib/slf4j-api-1.7.25.jar</resource>""".toString(), result)
    }
    @ModuleComponent
    class TestModuleImpl implements Module {

        @Override
        void init(ModuleDescriptor moduleDescriptor, ServerEnvironment serverEnvironment) {

        }

        @Override
        void installed() {

        }

        @Override
        void uninstalling() {

        }

        @Override
        void updated(String s) {

        }

    }
    @ModuleComponent(configurable = TestConfigurable.class)
    static class TestModuleImplWithConfiguration implements Module {
        @Override
        void init(ModuleDescriptor moduleDescriptor, ServerEnvironment serverEnvironment) {

        }

        @Override
        void installed() {

        }

        @Override
        void uninstalling() {

        }

        @Override
        void updated(String s) {

        }
    }

    @ProjectAppComponent(name = "TestProjectAppComponentName",
            displayName = "TestDisplayName",
            description = "TestDescription",
            configurable = TestConfigurable,
            resources = [@Resource(path = "lib/guava-24.0.jar", name = "com.google.guava:guava", version = "24.0")])
    static class TestProjectAppComponent extends BaseProjectApp {
        static class TestConfigurable extends BaseConfiguration { }
    }

    @ProjectAppComponent(name = "TestProjectAppComponentWithoutConfigurableName",
            displayName = "TestDisplayName",
            description = "TestDescription")
    static class TestProjectAppComponentWithoutConfigurable extends BaseProjectApp {

    }

    @WebAppComponent(name = "TestWebAppComponentName",
            displayName = "TestDisplayName",
            description = "TestDescription",
            configurable = TestConfigurable,
            webXml = "/test/web.xml",
            webResources = [@WebResource(path = "lib/guava-24.0.jar", name = "com.google.guava:guava", version = "24.0"),
                            @WebResource(targetPath = "targetPath", path = "lib/commons-lang-3.0.jar", name = "org.apache.commons:commons-lang3", version = "3.0", minVersion = "2.9", maxVersion = "3.1")])
    static class TestWebAppComponent extends BaseWebApp {
        static class TestConfigurable extends BaseConfiguration { }
    }

    @WebAppComponent(name = "TestWebAppComponentName",
            displayName = "TestDisplayName",
            description = "TestDescription",
            webXml = "/test/web.xml",
            webResources = [@WebResource(path = "lib/guava-24.0.jar", name = "com.google.guava:guava", version = "24.0"),
                            @WebResource(path = "lib/commons-lang-3.0.jar", name = "org.apache.commons:commons-lang3", version = "3.0", minVersion = "2.9", maxVersion = "3.1")])
    static class TestWebAppComponentWithoutConfiguration extends BaseWebApp {
        static class TestConfigurable extends BaseConfiguration { }
    }

    @PublicComponent(name = "TestPublicComponentName", displayName = "TestDisplayName")
    static class TestPublicComponent {
    }

    @PublicComponent(name = "TestPublicComponentWithConfigName", displayName = "TestDisplayName", configurable = TestConfigurable.class)
    static class TestPublicComponentWithConfiguration {
    }

    @ScheduleTaskComponent(taskName = "Test task", description = "A task for test purpose")
    static class TestScheduleTaskComponentWithoutForm {
    }

    @ScheduleTaskComponent(taskName = "Test task", description = "A task for test purpose", configurable = TestConfigurable.class)
    static class TestScheduleTaskComponentWithConfigurable {
    }

    @ScheduleTaskComponent(taskName = "Test task", description = "A task for test purpose", formClass = TestScheduleTaskFormFactory.class )
    static class TestScheduleTaskComponentWithForm {

    }

    static class TestScheduleTaskFormFactory implements ScheduleTaskFormFactory {

        @Override
        ScheduleTaskForm createForm(final SpecialistsBroker specialistsBroker) {
            return null
        }
    }

    static class TestConfigurable implements Configuration<ServerEnvironment> {

        @Override
        boolean hasGui() {
            return false
        }

        @Override
        JComponent getGui(Frame applicationFrame) {
            return null
        }

        @Override
        void load() {

        }

        @Override
        void store() {

        }

        @Override
        Set<String> getParameterNames() {
            return null
        }

        @Override
        String getParameter(String s) {
            return null
        }

        @Override
        void init(String moduleName, String componentName, ServerEnvironment env) {

        }

        @Override
        ServerEnvironment getEnvironment() {
            return null
        }
    }



    @ServiceComponent(name = "TestServiceComponentName",
            displayName = "TestDisplayName",
            description = "TestDescription",
            configurable = TestConfigurable)
    static class TestServiceComponent extends BaseService {
        static class TestConfigurable extends BaseConfiguration { }

        static class ServiceResource {}
    }


    @ServiceComponent(name = "TestServiceComponentWithoutConfigurableName",
            displayName = "TestDisplayName",
            description = "TestDescription")
    static class TestServiceComponentWithoutConfigurable extends BaseService {

    }

    @UrlFactoryComponent(name = "TestUrlFactoryComponentName",
                          displayName = "TestDisplayName",
                          description = "TestDescription",
                          useRegistry = true)
    static class TestUrlFactoryComponent implements UrlFactory {
        @Override
        void init(Map<String, String> map, PathLookup pathLookup) {

        }

        @Override
        String getUrl(ContentProducer contentProducer, TemplateSet templateSet, Language language, PageParams pageParams) {
            return null
        }

        @Override
        String getUrl(Media media, Language language, Resolution resolution) {
            return null
        }
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
