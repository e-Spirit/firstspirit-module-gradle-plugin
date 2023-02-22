package org.gradle.plugins.fsm.descriptor

import com.espirit.moddev.components.annotations.WebAppComponent
import de.espirit.firstspirit.module.Configuration
import de.espirit.firstspirit.module.ProjectApp
import de.espirit.firstspirit.module.WebApp
import io.github.classgraph.AnnotationInfo
import io.github.classgraph.ClassInfo
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

class WebAppComponents(project: Project, private val scanResult: ComponentScan): ComponentsWithResources(project) {

    lateinit var webXmlPaths: List<String>

    val nodes by lazy {
        val webAppClasses = scanResult.getClassesWithAnnotation(WebAppComponent::class)
        verify(webAppClasses)
        nodesForWebApp(webAppClasses)
    }

    private fun verify(webAppClasses: List<ClassInfo>) {
        val webAppChecker = DeclaredWebAppChecker(project, webAppClasses)
        val declaredWebApps = project.extensions.getByType(FSMPluginExtension::class.java).getWebApps()

        // Check if web-apps are complete
        // Warn if there is a @WebAppComponent annotation not defined in the `firstSpiritModule` block
        val undeclaredWebAppComponents = webAppChecker.webAppAnnotationsWithoutDeclaration
        if (declaredWebApps.isNotEmpty() && !(undeclaredWebAppComponents.isNullOrEmpty())) {
            val warningStringBuilder = StringBuilder()
            warningStringBuilder.append("@WebAppComponent annotations found that are not registered in the firstSpiritModule configuration block:\n")
            undeclaredWebAppComponents.forEach {
                val displayName = it.getStringOrNull("displayName", "")?.let { name -> " ($name)" } ?: ""
                warningStringBuilder.append("- ${it.getString("name")}${displayName}\n")
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

    private fun nodesForWebApp(webAppClasses: List<ClassInfo>): List<Node> {
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
            if (!webAppClass.implementsInterface(WebApp::class.java)) {
                throw GradleException("Web App '${webAppClass.name}' does not implement interface '${WebApp::class}'")
            }

            val annotation = webAppClass.annotationInfo
                    .filter { it.isClass(WebAppComponent::class) }
                    .first()
            val webCompileConfiguration = project.configurations.getByName(FSMConfigurationsPlugin.FS_WEB_COMPILE_CONFIGURATION_NAME)
            val projectDependencies = webCompileConfiguration.allDependencies.withType(ProjectDependency::class.java)

            val webResources = mutableListOf<Node>()

            // fsm-resources directory of root project and fsWebCompile subprojects (shared between all webapps)
            webResources.addAll(projectDependencies
                .map(ProjectDependency::getDependencyProject)
                .flatMap(this::fsmResources)
            )

            val webAppName = annotation.getString("name")
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
                val jarFile = jarTask.archiveFile.get().asFile
                if (!jarFile.exists()) {
                    Resources.LOGGER.warn("Jar file '$jarFile' not found!")
                } else if (Resources.isEmptyJarFile(jarFile)) {
                    Resources.LOGGER.info("Skipping empty Jar file.")
                } else {
                    webResources.add(xml("resource") {
                        attribute("name", "${webAppProject.group}:${webAppProject.name}")
                        attribute("version", webAppProject.version)
                        -"lib/${jarTask.archiveFileName.get()}"
                    })
                }

                // Add dependencies
                webAppProjectDependencies
                    .map { Resource(project, it, "", false).node }
                    .forEach(webResources::add)
            }

            // fsWebCompile for all subprojects
            sharedWebCompileDependencies
                .map { Resource(project, it, "", false).node }
                .forEach(webResources::add)

            val webXmlPath = annotation.getString("webXml")
            webXmlPaths.add(webXmlPath)

            nodes.add(xml("web-app") {
                val scopes = annotation.getEnumValues("scope")
                if (scopes.isNotEmpty()) {
                    attribute("scopes", scopes.joinToString(",") { it.valueName })
                }
                val xmlSchemaVersion = annotation.getString("xmlSchemaVersion")
                if (xmlSchemaVersion.isNotEmpty()) {
                    attribute("xml-schema-version", xmlSchemaVersion)
                }
                "name" { -webAppName }
                "displayname" { -annotation.getString("displayName") }
                "description" { -annotation.getString("description") }
                "class" { -webAppClass.name }
                annotation.getClassNameOrNull("configurable", Configuration::class)?.let { "configurable" { -it } }
                "web-xml" { -webXmlPath }
                "web-resources" {
                    val jarTask = project.tasks.findByName("jar") as Jar
                    if (!Resources.isEmptyJarFile(jarTask.archiveFile.get().asFile)) {
                        "resource" {
                            attribute("name", "${project.group}:${project.name}")
                            attribute("version", project.version)
                            -"lib/${jarTask.archiveFileName.get()}"
                        }
                    }

                    nodesForWebResources(annotation).forEach(this::addNode)
                    webResources.forEach(this::addNode)

                }

            })

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

    private fun nodesForWebResources(annotation: AnnotationInfo): List<Node> {
        val resources = annotation.getAnnotationValues("webResources")
        val nodes = mutableListOf<Node>()

        resources.forEach { resource ->
            val nameFromAnnotation = expand(resource.getString("name"), mutableMapOf("project" to project))
            val dependencyForName = getCompileDependencyForName(nameFromAnnotation)
            val context = getContextForCurrentResource(dependencyForName)
            val versionFromAnnotation = expandVersion(resource.getString("version"), context, nameFromAnnotation, annotation.getString("name"))
            val pathFromAnnotation = expand(resource.getString("path"), context)

            nodes.add(xml("resource") {
                attribute("name", nameFromAnnotation)
                attribute("version", versionFromAnnotation)
                resource.getStringOrNull("minVersion", "")?.let { attribute("minVersion", it) }
                resource.getStringOrNull("maxVersion", "")?.let { attribute("maxVersion", it) }
                resource.getStringOrNull("targetPath", "")?.let { attribute("target", it) }
                -pathFromAnnotation
            })
        }

        return nodes
    }

    companion object {
        val LOGGER: Logger = Logging.getLogger(WebAppComponents::class.java)
    }
}