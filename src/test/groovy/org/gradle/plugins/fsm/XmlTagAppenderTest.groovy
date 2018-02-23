package org.gradle.plugins.fsm

import com.espirit.moddev.components.annotations.ProjectAppComponent
import com.espirit.moddev.components.annotations.PublicComponent
import com.espirit.moddev.components.annotations.Resource
import com.espirit.moddev.components.annotations.WebAppComponent
import org.gradle.api.Project
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert
import org.junit.Before
import org.junit.Test

import static org.mockito.Mockito.mock
import static org.mockito.Mockito.when

class XmlTagAppenderTest {

    List<String> componentImplementingClasses = [TestPublicComponent.getName(), TestWebAppComponent.getName(), TestProjectAppComponent.getName()]
    List<String> validAndInvalidProjectAppClasses = [XmlTagAppender.PROJECTAPP_BLACKLIST, componentImplementingClasses].flatten()

    Project project

    @Before
    void setUp() {
        project = ProjectBuilder.builder().withName("webapps-test-project").build()
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
    void appendWebAppTags() throws Exception {
        StringBuilder result = new StringBuilder()
        XmlTagAppender.appendWebAppTags(project, new URLClassLoader(new URL[0], getClass().getClassLoader()), componentImplementingClasses, result)

        Assert.assertEquals("""
<web-app>
    <name>TestWebAppComponentName</name>
    <displayname>TestDisplayName</displayname>
    <description>TestDescription</description>
    <class>org.gradle.plugins.fsm.XmlTagAppenderTest\$TestWebAppComponent</class>
    <configurable>org.gradle.plugins.fsm.XmlTagAppenderTest\$TestWebAppComponent\$TestConfigurable</configurable>
    <web-xml>/test/web.xml</web-xml>
    <web-resources>
        <resource>lib/webapps-test-project.jar</resource>
        <resource>/test/web.xml</resource>
        <resource name="com.google.guava:guava" version="24.0">lib/guava-24.0.jar</resource>
        <resource name="joda-time.joda-time" version="2.3">lib/joda-time-2.3.jar</resource>
    </web-resources>
</web-app>
""", result.toString())
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
    <configurable>org.gradle.plugins.fsm.XmlTagAppenderTest\$TestProjectAppComponent\$TestConfigurable</configurable>
    <resources>
        <resource name="com.google.guava:guava" version="24.0" scope="MODULE" mode="LEGACY">lib/guava-24.0.jar</resource>
    </resources>
</project-app>
""", result.toString())
    }

    @Test
    void getResourceTagForDependency() throws Exception {
        def resolvedArtifact = mock(ResolvedArtifact)
        when(resolvedArtifact.extension).thenReturn("jar")
        def moduleVersionIdentifier = mock(ModuleVersionIdentifier)
        when(moduleVersionIdentifier.group).thenReturn("mygroup")
        when(moduleVersionIdentifier.name).thenReturn("myname")
        when(moduleVersionIdentifier.version).thenReturn("1.0.0")

        String result = XmlTagAppender.getResourceTagForDependency(moduleVersionIdentifier, resolvedArtifact, "server")
        Assert.assertEquals("""<resource name="mygroup.myname" scope="server" version="1.0.0">lib/myname-1.0.0.jar</resource>""", result)
    }

    @ProjectAppComponent(name = "TestProjectAppComponentName",
            displayName = "TestDisplayName",
            description = "TestDescription",
            configurable = TestConfigurable,
            resources = [@Resource(path = "lib/guava-24.0.jar", name = "com.google.guava:guava", version = "24.0")])
    static class TestProjectAppComponent {
        static class TestConfigurable{}
    }

    @WebAppComponent(name = "TestWebAppComponentName",
            displayName = "TestDisplayName",
            description = "TestDescription",
            configurable = TestConfigurable,
            webXml = "/test/web.xml",
            webResources = [@Resource(path = "lib/guava-24.0.jar", name = "com.google.guava:guava", version = "24.0")])
    static class TestWebAppComponent {
        static class TestConfigurable{}
    }
    @PublicComponent(name = "TestPublicComponentName", displayName = "TestDisplayName")
    static class TestPublicComponent {
    }
}
