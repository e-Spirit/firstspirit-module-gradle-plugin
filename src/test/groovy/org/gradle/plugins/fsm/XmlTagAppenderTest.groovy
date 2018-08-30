package org.gradle.plugins.fsm

import com.espirit.moddev.components.annotations.*
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
import com.espirit.moddev.components.annotations.ScheduleTaskComponent
import com.espirit.moddev.components.annotations.WebAppComponent
import de.espirit.firstspirit.agency.SpecialistsBroker
import de.espirit.firstspirit.module.Configuration
import de.espirit.firstspirit.module.ScheduleTaskSpecification
import de.espirit.firstspirit.module.ServerEnvironment
import de.espirit.firstspirit.scheduling.ScheduleTaskForm
import de.espirit.firstspirit.scheduling.ScheduleTaskFormFactory
import de.espirit.firstspirit.server.module.ModuleInfo
import org.gradle.api.Project
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ResolvedArtifact
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

    final List<String> componentImplementingClasses = [TestPublicComponent.getName(), TestWebAppComponent.getName(), TestProjectAppComponent.getName(),
                                                 TestScheduleTaskComponentWithForm.getName(), TestScheduleTaskComponentWithoutForm.getName()]
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
    }

    @Test
    void testPublicComponentTagAppending() throws Exception {
        StringBuilder result = new StringBuilder()
        XmlTagAppender.appendPublicComponentTags(new URLClassLoader(new URL[0], getClass().getClassLoader()), componentImplementingClasses, result)

        Assert.assertEquals("""
<public>
    <name>TestPublicComponentName</name>
    <displayname>TestDisplayName</displayname>
    <class>org.gradle.plugins.fsm.XmlTagAppenderTest\$TestPublicComponent</class>
</public>""", result.toString())
    }

    @Test
    void testScheduleTaskComponentTagAppendingWithForm() {
        StringBuilder result = new StringBuilder()
        XmlTagAppender.appendScheduleTaskComponentTags(new URLClassLoader(new URL[0], getClass().getClassLoader()), [TestScheduleTaskComponentWithForm.getName()], result)

        Assert.assertEquals("""
<public>
    <name>Test task</name>
    <description>A task for test purpose</description>
    <class>de.espirit.firstspirit.module.ScheduleTaskSpecification</class>
    <configuration>
        <application>org.gradle.plugins.fsm.XmlTagAppenderTest\$TestScheduleTaskComponentWithForm</application>
        <form>org.gradle.plugins.fsm.XmlTagAppenderTest\$TestScheduleTaskFormFactory</form>
    </configuration>
</public>""", result.toString())
    }

    @Test
    void testScheduleTaskComponentTagAppendingWithoutForm() {
        StringBuilder result = new StringBuilder()
        XmlTagAppender.appendScheduleTaskComponentTags(new URLClassLoader(new URL[0], getClass().getClassLoader()),[TestScheduleTaskComponentWithoutForm.getName()], result)

        Assert.assertEquals("""
<public>
    <name>Test task</name>
    <description>A task for test purpose</description>
    <class>de.espirit.firstspirit.module.ScheduleTaskSpecification</class>
    <configuration>
        <application>org.gradle.plugins.fsm.XmlTagAppenderTest\$TestScheduleTaskComponentWithoutForm</application>
    </configuration>
</public>""", result.toString())
    }

    @Test
    void appendWebAppTags() throws Exception {
        StringBuilder result = new StringBuilder()
        XmlTagAppender.appendWebAppTags(project, new URLClassLoader(new URL[0], getClass().getClassLoader()), componentImplementingClasses, result, true)

        Assert.assertEquals("""
<web-app scopes="project,global">
    <name>TestWebAppComponentName</name>
    <displayname>TestDisplayName</displayname>
    <description>TestDescription</description>
    <class>org.gradle.plugins.fsm.XmlTagAppenderTest\$TestWebAppComponent</class>
    <configurable>org.gradle.plugins.fsm.XmlTagAppenderTest\$TestConfigurable</configurable>
    <web-xml>/test/web.xml</web-xml>
    <web-resources>
        <resource name="$GROUP:$NAME" version="${VERSION}">lib/$NAME-${VERSION}.jar</resource>
        <resource>/test/web.xml</resource>
        <resource name="com.google.guava:guava" version="24.0">lib/guava-24.0.jar</resource><resource name="org.apache.commons:commons-lang3" version="3.0" minVersion="2.9" maxVersion="3.1">lib/commons-lang-3.0.jar</resource>
        <resource name="joda-time:joda-time" version="2.3" minVersion="2.3">lib/joda-time-2.3.jar</resource>
    </web-resources>
</web-app>
""".toString(), result.toString())
    }

    @Test
    void appendProjectAppTags() throws Exception {
        StringBuilder result = new StringBuilder()
        XmlTagAppender.appendProjectAppTags( new URLClassLoader(new URL[0], getClass().getClassLoader()), validAndInvalidProjectAppClasses, result)

        Assert.assertEquals("""
<project-app>
    <name>TestProjectAppComponentName</name>
    <displayname>TestDisplayName</displayname>
    <description>TestDescription</description>
    <class>org.gradle.plugins.fsm.XmlTagAppenderTest\$TestProjectAppComponent</class>
    <configurable>org.gradle.plugins.fsm.XmlTagAppenderTest\$TestConfigurable</configurable>
    <resources>
        <resource name="com.google.guava:guava" version="24.0" scope="MODULE" mode="LEGACY">lib/guava-24.0.jar</resource>
    </resources>
</project-app>
""", result.toString())
    }

    @Test
    void appendServiceTags() throws Exception {
        StringBuilder result = new StringBuilder()
        XmlTagAppender.appendServiceTags( new URLClassLoader(new URL[0], getClass().getClassLoader()), [TestServiceComponent.getName()], result)

        Assert.assertEquals("""
<service>
    <name>TestServiceComponentName</name>
    <displayname>TestDisplayName</displayname>
    <description>TestDescription</description>
    <class>org.gradle.plugins.fsm.XmlTagAppenderTest\$TestServiceComponent</class>
    <configurable>org.gradle.plugins.fsm.XmlTagAppenderTest\$TestServiceComponent\$TestConfigurable</configurable>
</service>
""", result.toString())
    }

    @Test
    void appendUrlCreatorTags() throws Exception {
        StringBuilder result = new StringBuilder()
        XmlTagAppender.appendUrlCreatorTags( new URLClassLoader(new URL[0], getClass().getClassLoader()), [TestUrlFactoryComponent.getName()], result)

        Assert.assertEquals("""
<public>
    <name>TestUrlFactoryComponentName</name>
    <displayname>TestDisplayName</displayname>
    <description>TestDescription</description>
    <class>de.espirit.firstspirit.generate.UrlCreatorSpecification</class>
    <configuration>
        <UrlFactory>org.gradle.plugins.fsm.XmlTagAppenderTest\$TestUrlFactoryComponent</UrlFactory>
        <UseRegistry>true</UseRegistry>
    </configuration>
</public>
""", result.toString())
    }

    @Test
    void getResourceTagForDependency() throws Exception {
        ResolvedArtifact resolvedArtifact = createArtifactMock()
        ModuleVersionIdentifier moduleVersionIdentifier = createVersionIdentifierMock("mygroup", "myname", "1.0.0")

        String result = XmlTagAppender.getResourceTagForDependency(moduleVersionIdentifier, resolvedArtifact, "server", ModuleInfo.Mode.ISOLATED, true)
        Assert.assertEquals("""<resource name="mygroup:myname" scope="server" mode="isolated" version="1.0.0" minVersion="1.0.0">lib/myname-1.0.0.jar</resource>""", result)
    }

    @Test
    void getResourcesTags() {
        String result = XmlTagAppender.getResourcesTags(project, ModuleInfo.Mode.ISOLATED, false)
        Assert.assertEquals("""<resource name="$GROUP:$NAME" version="$VERSION" scope="module" mode="isolated">lib/$NAME-${VERSION}.jar</resource>""".toString(), result)
    }

    @ProjectAppComponent(name = "TestProjectAppComponentName",
            displayName = "TestDisplayName",
            description = "TestDescription",
            configurable = TestConfigurable,
            resources = [@Resource(path = "lib/guava-24.0.jar", name = "com.google.guava:guava", version = "24.0")])
    static class TestProjectAppComponent extends BaseProjectApp {
        static class TestConfigurable extends BaseConfiguration { }
    }

    @WebAppComponent(name = "TestWebAppComponentName",
            displayName = "TestDisplayName",
            description = "TestDescription",
            configurable = TestConfigurable,
            webXml = "/test/web.xml",
            webResources = [@Resource(path = "lib/guava-24.0.jar", name = "com.google.guava:guava", version = "24.0"),
                            @Resource(path = "lib/commons-lang-3.0.jar", name = "org.apache.commons:commons-lang3", version = "3.0", minVersion = "2.9", maxVersion = "3.1")])
    static class TestWebAppComponent extends BaseWebApp {
        static class TestConfigurable extends BaseConfiguration { }
    }

    @PublicComponent(name = "TestPublicComponentName", displayName = "TestDisplayName")
    static class TestPublicComponent {
    }

    @ScheduleTaskComponent(taskName = "Test task", description = "A task for test purpose")
    static class TestScheduleTaskComponentWithoutForm {

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
