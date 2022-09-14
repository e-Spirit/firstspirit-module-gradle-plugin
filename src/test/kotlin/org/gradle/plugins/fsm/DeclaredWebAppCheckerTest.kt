package org.gradle.plugins.fsm

import com.espirit.moddev.components.annotations.WebAppComponent
import io.github.classgraph.ClassGraph
import org.assertj.core.api.Assertions.assertThat
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DeclaredWebAppCheckerTest {

    private lateinit var project: Project

    private lateinit var webAppSubprojectA: Project
    private lateinit var webAppSubprojectB: Project
    private lateinit var webAppSubprojectC: Project

    @BeforeEach
    fun setUp() {
        project = getProject(NAME)
        webAppSubprojectA = getProject(WEBAPP_A_NAME)
        webAppSubprojectB = getProject(WEBAPP_B_NAME)
        webAppSubprojectC = getProject(WEBAPP_C_NAME)
        project.extensions.getByType(FSMPluginExtension::class.java).webAppComponent("TestWebAppA", webAppSubprojectA)
        project.extensions.getByType(FSMPluginExtension::class.java).webAppComponent("TestWebAppC", webAppSubprojectC)
    }

    @Test
    fun testMissingDeclaredWebApps() {
        val classGraph = ClassGraph()
            .enableClassInfo()
            .enableAnnotationInfo()
            .acceptClasses(TestWebAppA::class.qualifiedName, TestWebAppB::class.qualifiedName)
        classGraph.scan().use { scan ->
            val annotations = scan.getClassesWithAnnotation(WebAppComponent::class.java)
            val webAppChecker = DeclaredWebAppChecker(project, annotations)

            val annotationsWithoutDeclaration = webAppChecker.webAppAnnotationsWithoutDeclaration
            assertThat(annotationsWithoutDeclaration?.map { it.parameterValues.getValue("name") }).containsExactly("TestWebAppB")

            val declarationsWithoutAnnotation = webAppChecker.declaredProjectsWithoutAnnotation
            assertThat(declarationsWithoutAnnotation).containsExactly("TestWebAppC")
        }
    }

    private fun getProject(name: String): Project {
        val project = ProjectBuilder.builder().withName(name).build()
        project.group = GROUP
        project.version = VERSION
        project.repositories.add(project.repositories.mavenCentral())
        project.pluginManager.apply(FSMPlugin::class.java)
        return project
    }

    companion object {
        private const val NAME = "webapps-test-project"
        private const val GROUP = "test"
        private const val VERSION = "1.2"

        private const val WEBAPP_A_NAME = "a"
        private const val WEBAPP_B_NAME = "b"
        private const val WEBAPP_C_NAME = "c"
    }


}