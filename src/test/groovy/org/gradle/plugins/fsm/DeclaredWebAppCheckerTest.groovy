package org.gradle.plugins.fsm


import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

import static org.assertj.core.api.Assertions.assertThat

class DeclaredWebAppCheckerTest {

    Project project

    Project webAppSubprojectA
    Project webAppSubprojectB
    Project webAppSubprojectC

    private static final String NAME = "webapps-test-project"
    private static final String GROUP = "test"
    private static final String VERSION = "1.2"

    private static final String WEBAPP_A_NAME = "a"
    private static final String WEBAPP_B_NAME = "b"
    private static final String WEBAPP_C_NAME = "c"

    static Project getProject(String name) {
        def project = ProjectBuilder.builder().withName(name).build()
        project.setGroup(GROUP)
        project.setVersion(VERSION)
        project.repositories.add(project.repositories.mavenCentral())
        project.pluginManager.apply(FSMPlugin)
        project
    }

    @BeforeEach
    void setUp() {
        project = getProject(NAME)
        webAppSubprojectA = getProject(WEBAPP_A_NAME)
        webAppSubprojectB = getProject(WEBAPP_B_NAME)
        webAppSubprojectC = getProject(WEBAPP_C_NAME)
        project.extensions.getByType(FSMPluginExtension.class).webAppComponent("TestWebAppA", webAppSubprojectA)
        project.extensions.getByType(FSMPluginExtension.class).webAppComponent("TestWebAppC", webAppSubprojectC)
    }

    @Test
    void testMissingDeclaredWebApps() {
        def webAppAnnotations = [TestWebAppA, TestWebAppB].collect { it.name }
        def classLoader = new URLClassLoader(new URL[0], getClass().classLoader)
        def webAppClasses = webAppAnnotations.collect { classLoader.loadClass(it) }
        def webAppChecker = new DeclaredWebAppChecker(project, webAppClasses)

        def annotationsWithoutDeclaration = webAppChecker.webAppAnnotationsWithoutDeclaration
        assertThat(annotationsWithoutDeclaration.collect { it.name() }).containsExactly("TestWebAppB")

        def declarationsWithoutAnnotation = webAppChecker.declaredProjectsWithoutAnnotation
        assertThat(declarationsWithoutAnnotation).containsExactly("TestWebAppC")
    }
}

