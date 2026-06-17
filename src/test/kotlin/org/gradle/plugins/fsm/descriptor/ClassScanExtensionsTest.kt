package org.gradle.plugins.fsm.descriptor

import com.espirit.moddev.components.annotations.ProjectAppComponent
import com.espirit.moddev.components.annotations.WebAppComponent
import de.espirit.firstspirit.module.AbstractWebApp
import de.espirit.firstspirit.module.ProjectApp
import de.espirit.firstspirit.module.WebApp
import io.github.classgraph.ClassInfo
import org.assertj.core.api.Assertions.assertThat
import org.gradle.api.Project
import org.gradle.plugins.fsm.FSMPluginExtension
import org.gradle.plugins.fsm.annotations.FSMAnnotationsPlugin
import org.gradle.plugins.fsm.configurations.FSMConfigurationsPlugin
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.reflect.KClass

/**
 * Tests [hasSupertype], which decides whether the "does not appear to implement interface" warning
 * is emitted for a `@WebAppComponent` / `@ProjectAppComponent`. The warning fires iff [hasSupertype]
 * returns `false`, so asserting the function directly covers both DEVEX-689 acceptance criteria.
 */
class ClassScanExtensionsTest {

    private val project: Project = ProjectBuilder.builder().build()
    private lateinit var scan: ComponentScan

    private val webAppTypes = setOf(WebApp::class.qualifiedName, AbstractWebApp::class.qualifiedName)
    private val projectAppTypes = setOf(ProjectApp::class.qualifiedName)

    @BeforeEach
    fun setup() {
        project.plugins.apply("java-library")
        project.plugins.apply(FSMAnnotationsPlugin::class.java)
        project.plugins.apply(FSMConfigurationsPlugin::class.java)
        project.extensions.create("fsmPlugin", FSMPluginExtension::class.java)
        project.setArtifactoryCredentialsFromLocalProperties()
        project.defineArtifactoryForProject()
        project.copyTestJar()
        project.addClassToTestJar("org/gradle/plugins/fsm/AbstractWebAppComponent.class")
        project.addClassToTestJar("org/gradle/plugins/fsm/NoInterfaceWebAppComponent.class")
        project.addClassToTestJar("org/gradle/plugins/fsm/NoInterfaceProjectAppComponent.class")

        val context = FSMGradlePluginContext(project)
        scan = ComponentScan(context.fsmAnnotationsJar, context.projectJarFiles)
    }

    @AfterEach
    fun tearDown() {
        scan.close()
    }

    @Test
    fun `web app implementing WebApp is recognized`() {
        assertThat(webApp("TestMinimalWebAppComponent").hasSupertype(webAppTypes)).isTrue()
    }

    @Test
    fun `web app extending AbstractWebApp is recognized`() {
        assertThat(webApp("AbstractWebAppComponent").hasSupertype(webAppTypes)).isTrue()
    }

    @Test
    fun `project app implementing ProjectApp is recognized`() {
        assertThat(projectApp("TestMinimalProjectAppComponent").hasSupertype(projectAppTypes)).isTrue()
    }

    @Test
    fun `web app without interface is not recognized`() {
        assertThat(webApp("NoInterfaceWebAppComponent").hasSupertype(webAppTypes)).isFalse()
    }

    @Test
    fun `project app without interface is not recognized`() {
        assertThat(projectApp("NoInterfaceProjectAppComponent").hasSupertype(projectAppTypes)).isFalse()
    }

    private fun webApp(simpleName: String): ClassInfo = scanned(WebAppComponent::class, simpleName)

    private fun projectApp(simpleName: String): ClassInfo = scanned(ProjectAppComponent::class, simpleName)

    private fun scanned(annotation: KClass<out Annotation>, simpleName: String): ClassInfo =
        scan.getClassesWithAnnotation(annotation).single { it.simpleName == simpleName }

}
