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
    private lateinit var extension: FSMPluginExtension

    private lateinit var webAppSubprojectA: Project
    private lateinit var webAppSubprojectB: Project
    private lateinit var webAppSubprojectC: Project

    @BeforeEach
    fun setUp() {
        project = getProject(NAME)
        project.pluginManager.apply(FSMPlugin::class.java)
        extension = project.extensions.getByType(FSMPluginExtension::class.java)
        webAppSubprojectA = getProject(WEBAPP_A_NAME, project)
        webAppSubprojectB = getProject(WEBAPP_B_NAME, project)
        webAppSubprojectC = getProject(WEBAPP_C_NAME, project)
        extension.webAppComponent("TestWebAppA", webAppSubprojectA)
        extension.webAppComponent("TestWebAppC", webAppSubprojectC)
    }

    @Test
    fun testMissingDeclaredWebApps() {
        val classGraph = ClassGraph()
            .enableClassInfo()
            .enableAnnotationInfo()
            .acceptClasses(TestWebAppA::class.qualifiedName, TestWebAppB::class.qualifiedName)
        classGraph.scan().use { scan ->
            val annotations = scan.getClassesWithAnnotation(WebAppComponent::class.java)
            val webAppChecker = DeclaredWebAppChecker(extension, annotations)

            val annotationsWithoutDeclaration = webAppChecker.webAppAnnotationsWithoutDeclaration
            assertThat(annotationsWithoutDeclaration?.map { it.parameterValues.getValue("name") }).containsExactly("TestWebAppB")

            val declarationsWithoutAnnotation = webAppChecker.declaredProjectsWithoutAnnotation
            assertThat(declarationsWithoutAnnotation).containsExactly("TestWebAppC")
        }
    }

    private fun getProject(name: String, rootProject: Project? = null): Project {
        val project = ProjectBuilder.builder().withName(name).withParent(rootProject).build()
        project.group = GROUP
        project.version = VERSION
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