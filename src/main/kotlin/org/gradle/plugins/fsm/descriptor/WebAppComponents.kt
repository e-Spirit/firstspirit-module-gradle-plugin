package org.gradle.plugins.fsm.descriptor

import com.espirit.moddev.components.annotations.WebAppComponent
import de.espirit.firstspirit.module.Configuration
import de.espirit.firstspirit.module.WebApp
import io.github.classgraph.ScanResult
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.plugins.JavaPlugin
import org.gradle.jvm.tasks.Jar
import org.gradle.plugins.fsm.DeclaredWebAppChecker
import org.gradle.plugins.fsm.FSMPluginExtension
import org.gradle.plugins.fsm.configurations.FSMConfigurationsPlugin
import org.redundent.kotlin.xml.Node
import org.redundent.kotlin.xml.xml
import java.io.File
import java.util.*

class WebAppComponents(
    project: Project, private val scanResult: ScanResult, private val classLoader: ClassLoader): ComponentsWithResources(project) {

    lateinit var webXmlPaths: List<String>

    val nodes by lazy {
        val webAppClasses = scanResult
            .getClassesImplementing(WebApp::class.java.name).names
            .map(classLoader::loadClass)
        verify(webAppClasses)
        nodesForWebApp(webAppClasses)
    }

    private fun verify(webAppClasses: List<Class<*>>) {
        val webAppChecker = DeclaredWebAppChecker(project, webAppClasses)
        val declaredWebApps = project.extensions.getByType(FSMPluginExtension::class.java).getWebApps()

        // Check if web-apps are complete
        // Warn if there is a @WebAppComponent annotation not defined in the `firstSpiritModule` block
        val undeclaredWebAppComponents = webAppChecker.webAppAnnotationsWithoutDeclaration
        if (declaredWebApps.isNotEmpty() && !(undeclaredWebAppComponents.isNullOrEmpty())) {
            val warningStringBuilder = StringBuilder()
            warningStringBuilder.append("@WebAppComponent annotations found that are not registered in the firstSpiritModule configuration block:\n")
            undeclaredWebAppComponents.forEach {
                val displayName = if (it.displayName.isEmpty()) { "" } else { " (${it.displayName})" }
                warningStringBuilder.append("- ${it.name}${displayName}\n")
            }
            LOGGER.warn(warningStringBuilder.toString())
        }
        // ... or if there is a web-app defined in the `firstSpiritModule` block we cannot find a @WebAppComponent annotation for, throw an error
        val declaredWebAppNames = webAppChecker.declaredProjectsWithoutAnnotation
        if (!declaredWebAppNames.isNullOrEmpty()) {
            val errorStringBuilder = StringBuilder()
            errorStringBuilder.append("No @WebAppComponent annotation found for the following web-apps registered in the firstSpiritModule configuration block:\n")
            declaredWebAppNames.forEach {
                errorStringBuilder.append("- ${it}\n")
            }
            throw GradleException(errorStringBuilder.toString())
        }
    }

    private fun nodesForWebApp(webAppClasses: List<Class<*>>): List<Node> {
        // We might find the same dependencies in different subprojects / configurations, but with different versions
        // Because only one version ends up in the FSM archive, we need to make sure we always use the correct version
        val allCompileDependencies = project.configurations.getByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME)
            .resolvedConfiguration.resolvedArtifacts
        val sharedWebCompileDependencies = getResolvedDependencies(project,
            FSMConfigurationsPlugin.FS_WEB_COMPILE_CONFIGURATION_NAME, allCompileDependencies)

        val webXmlPaths = mutableListOf<String>()
        val declaredWebApps = project.extensions.getByType(FSMPluginExtension::class.java).getWebApps()
        val nodes = mutableListOf<Node>()

        webAppClasses.forEach { webAppClass ->
            val annotation = webAppClass.annotations.filterIsInstance<WebAppComponent>().firstOrNull()
            if (annotation != null) {
                val webCompileConfiguration = project.configurations.getByName(FSMConfigurationsPlugin.FS_WEB_COMPILE_CONFIGURATION_NAME)
                val projectDependencies = webCompileConfiguration.allDependencies.withType(ProjectDependency::class.java)

                val webResources = mutableListOf<Node>()

                // fsm-resources directory of root project and fsWebCompile subprojects (shared between all webapps)
                webResources.addAll(projectDependencies
                    .map(ProjectDependency::getDependencyProject)
                    .flatMap(this::fsmResources))

                val webAppName = annotation.name
                if (declaredWebApps.containsKey(webAppName)) {
                    val webAppProject = declaredWebApps[webAppName]!!

                    // fsm-resources directory of current web-app
                    // - safety check to avoid duplicates
                    if (!projectDependencies.map(ProjectDependency::getDependencyProject).contains(webAppProject)) {
                        webResources.addAll(fsmResources(webAppProject))
                    }

                    // compile dependencies of web-app subproject -
                    // If we registered a subproject for a given web-app, evaluate its compile dependencies
                    val webAppProjectDependencies = getResolvedDependencies(webAppProject, JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME, allCompileDependencies)

                    // Don't want duplicate resources
                    webAppProjectDependencies.removeAll(sharedWebCompileDependencies)

                    val jarTask = webAppProject.tasks.findByName("jar") as Jar

                    // Add dependencies
                    webResources.add(xml("resource") {
                        attribute("name", "${webAppProject.group}:${webAppProject.name}")
                        attribute("version", webAppProject.version)
                        -"lib/${jarTask.archiveFileName.get()}"
                    })

                    webAppProjectDependencies
                        .map { Resource(project, it, "", false).node }
                        .forEach(webResources::add)
                }

                // fsWebCompile for all subprojects
                sharedWebCompileDependencies
                    .map { Resource(project, it, "", false).node }
                    .forEach(webResources::add)

                val webXmlPath = annotation.webXml
                webXmlPaths.add(webXmlPath)

                nodes.add(xml("web-app") {
                    if (annotation.scope.isNotEmpty()) {
                        attribute("scopes", annotation.scope.joinToString(","))
                    }
                    "name" { -webAppName }
                    "displayname" { -annotation.displayName }
                    "description" { -annotation.description }
                    "class" { -webAppClass.name }
                    if (annotation.configurable != Configuration::class) {
                        "configurable" { -annotation.configurable.java.name }
                    }
                    "web-xml" { -webXmlPath }
                    "web-resources" {
                        "resource" {
                            attribute("name", "${project.group}:${project.name}")
                            attribute("version", project.version)
                            val jarTask = project.tasks.findByName("jar") as Jar
                            -"lib/${jarTask.archiveFileName.get()}"
                        }

                        nodesForWebResources(annotation).forEach(this::addNode)
                        webResources.forEach(this::addNode)

                    }

                })

            }
        }

        this.webXmlPaths = webXmlPaths

        return nodes
    }

    /**
     * Finds all dependencies of a given configuration and finds the global version of each dependency
     *
     * @param project           The project
     * @param configurationName The configuration to fetch the dependencies for
     * @param allDependencies   All dependencies of the project, with the correct version
     * @return The dependencies of `configurationName`, with the correct version
     */
    private fun getResolvedDependencies(project: Project, configurationName: String, allDependencies: Set<ResolvedArtifact>): MutableSet<ResolvedArtifact> {
        val configuration = project.configurations.findByName(configurationName) ?: return Collections.emptySet()
        val resolvedArtifacts = configuration.resolvedConfiguration.resolvedArtifacts
        return allDependencies.filter { resource ->
            resolvedArtifacts.any { it.hasSameModuleAs(resource) }
        }.toMutableSet()
    }

    private fun fsmResources(project: Project): List<Node> {
        val fsmWebResourcesPath = project.projectDir.resolve(FSMConfigurationsPlugin.FSM_RESOURCES_PATH).absolutePath
        val fsmWebResourcesFolder = File(fsmWebResourcesPath)
        return if (fsmWebResourcesFolder.exists()) {
            fsmWebResourcesFolder.listFiles()?.map { file ->
                val relPath = fsmWebResourcesFolder.toPath().relativize(file.toPath())
                xml("resource") {
                    attribute("name", relPath)
                    attribute("version", project.version)
                    -relPath.toString()
                }
            }.orEmpty()
        } else {
            emptyList()
        }
    }

    private fun nodesForWebResources(annotation: WebAppComponent): List<Node> {
        val resources = annotation.webResources
        val nodes = mutableListOf<Node>()

        resources.forEach { resource ->
            val nameFromAnnotation = expand(resource.name, mutableMapOf("project" to project))
            val dependencyForName = getCompileDependencyForName(nameFromAnnotation)
            val context = getContextForCurrentResource(dependencyForName)
            val versionFromAnnotation = expandVersion(resource.version, context, nameFromAnnotation, annotation.name)
            val pathFromAnnotation = expand(resource.path, context)

            nodes.add(xml("resource") {
                attribute("name", nameFromAnnotation)
                attribute("version", versionFromAnnotation)
                if (resource.minVersion.isNotEmpty()) {
                    attribute("minVersion", resource.minVersion)
                }
                if (resource.maxVersion.isNotEmpty()) {
                    attribute("maxVersion", resource.maxVersion)
                }
                if (resource.targetPath.isNotEmpty()) {
                    attribute("target", resource.targetPath)
                }
                -pathFromAnnotation
            })
        }

        return nodes
    }

    companion object {
        val LOGGER: Logger = Logging.getLogger(WebAppComponents::class.java)
    }
}