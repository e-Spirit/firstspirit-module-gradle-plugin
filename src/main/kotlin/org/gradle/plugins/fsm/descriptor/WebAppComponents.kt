package org.gradle.plugins.fsm.descriptor

import com.espirit.moddev.components.annotations.WebAppComponent
import de.espirit.firstspirit.module.AbstractWebApp
import de.espirit.firstspirit.module.Configuration
import de.espirit.firstspirit.module.WebApp
import io.github.classgraph.AnnotationInfo
import io.github.classgraph.ClassInfo
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.plugins.fsm.DeclaredWebAppChecker
import org.gradle.plugins.fsm.configurations.FSMConfigurationsPlugin
import org.gradle.plugins.fsm.tasks.bundling.ResolvedDependencyInfo
import org.gradle.plugins.fsm.tasks.bundling.WebAppProjectInfo
import org.redundent.kotlin.xml.Node
import org.redundent.kotlin.xml.xml
import java.io.File

class WebAppComponents(
    private val scanResult: ComponentScan,
    private val fsmGradlePluginContext: FSMGradlePluginContext
): ComponentsWithResources(fsmGradlePluginContext.runtimeArtifacts) {

    val webXmlPaths = mutableListOf<String>()

    val nodes by lazy {
        val webAppClasses = scanResult.getClassesWithAnnotation(WebAppComponent::class)
        verify(webAppClasses)
        nodesForWebApps(webAppClasses)
    }

    private fun verify(webAppClasses: List<ClassInfo>) {
        val extension = fsmGradlePluginContext.extension
        val webAppChecker = DeclaredWebAppChecker(extension, webAppClasses)
        val declaredWebApps = extension.getWebApps()

        // Check if web-apps are complete
        // Warn if there is a @WebAppComponent annotation not defined in the `firstSpiritModule` block
        val undeclaredWebAppComponents = webAppChecker.webAppAnnotationsWithoutDeclaration
        if (declaredWebApps.isNotEmpty() && !undeclaredWebAppComponents.isNullOrEmpty()) {
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

    private fun nodesForWebApps(webAppClasses: List<ClassInfo>): List<Node> {
        // We might find the same dependencies in different subprojects / configurations, but with different versions
        // Because only one version ends up in the FSM archive, we need to make sure we always use the correct version
        val allCompileDependencies = resolvedArtifacts.get()
        val sharedWebCompileDependencies = getSharedWebCompileDependencies(allCompileDependencies,
            fsmGradlePluginContext.fsWebCompileArtifacts.get())

        val webAppProjects = fsmGradlePluginContext.webAppProjects.get()
        return webAppClasses
            .map { nodeForWebApp(it, webAppProjects, allCompileDependencies, sharedWebCompileDependencies) }
    }

    /**
     * Finds all dependencies on the runtime classpath that are also defined for `fsWebCompile`
     *
     * @param runtimeDependencies   All dependencies available on the runtime classpath of the project
     * @param fsWebCompileArtifacts Dependencies declared for `fsWebCompile`
     *
     * @return All dependencies on the runtime classpath that are also defined for `fsWebCompile`
     */
    private fun getSharedWebCompileDependencies(
        runtimeDependencies: Set<ResolvedDependencyInfo>,
        fsWebCompileArtifacts: Set<ResolvedDependencyInfo>
    ): Set<ResolvedDependencyInfo> {
        return runtimeDependencies.filter { dep ->
            fsWebCompileArtifacts.any { it.hasSameModuleAs(dep) }
        }.toSet()
    }

    private fun nodeForWebApp(
        webAppClass: ClassInfo,
        webAppProjects: Map<String, WebAppProjectInfo>,
        allCompileDependencies: Set<ResolvedDependencyInfo>,
        sharedWebCompileDependencies: Set<ResolvedDependencyInfo>
    ): Node {
        // Report if WebApp does not seem to implement WebApp or AbstractWebApp
        if (webAppClass.superclass?.name !in WEB_APP_TYPES) {
            LOGGER.info("Web App '${webAppClass.name}' does not appear to implement interface '${WebApp::class.qualifiedName}'.")
            LOGGER.info("This might be because the class implements or extends an intermediary type inheriting from ${WebApp::class.simpleName}.")
        }

        val webResources = LinkedHashSet<Node>()
        // fsm-resources directory of root project and fsWebCompile subprojects (shared between all webapps)
        webResources.addAll(fsmGradlePluginContext.webCompileResources.get())

        val annotation = webAppClass.annotationInfo
            .filter { it.isClass(WebAppComponent::class) }
            .first()
        val webAppName = annotation.getString("name")
        val extension = fsmGradlePluginContext.extension
        if (extension.getWebApps().containsKey(webAppName)) {
            val webAppInfo = webAppProjects[webAppName]

            if (webAppInfo != null) {
                webResources.addAll(resourcesForWebAppProject(webAppInfo, allCompileDependencies))
            }
        }

        // fsWebCompile for all subprojects
        sharedWebCompileDependencies
            .map { Resource(fsmGradlePluginContext, it, "", false).node }
            .forEach(webResources::add)

        val webXmlPath = annotation.getString("webXml")
        webXmlPaths.add(webXmlPath)

        return webAppNode(annotation, webAppName, webAppClass, webXmlPath, webResources)
    }

    private fun resourcesForWebAppProject(
        webAppInfo: WebAppProjectInfo,
        allCompileDependencies: Set<ResolvedDependencyInfo>
    ): List<Node> {
        val webResources = mutableListOf<Node>()

        // fsm-resources directories of current web-app and all its dependencies
        webResources.addAll(webAppInfo.fsmResources)

        // compile dependencies of web-app subproject -
        // If we registered a subproject for a given web-app, evaluate its compile dependencies
        val webAppProjectDependencies = allCompileDependencies
            .filter { dep -> webAppInfo.runtimeArtifacts.any { it.hasSameModuleAs(dep) } }
            .toMutableSet()

        if (!webAppInfo.jarFile.exists()) {
            LOGGER.warn("Jar file '${webAppInfo.jarFile}' not found!")
        } else if (Resources.isEmptyJarFile(webAppInfo.jarFile)) {
            LOGGER.info("Skipping empty Jar file.")
        } else {
            webResources.add(xml("resource") {
                attribute("name", "${webAppInfo.group}:${webAppInfo.name}")
                attribute("version", webAppInfo.version)
                -"lib/${webAppInfo.jarFile.name}"
            })
        }

        // Add dependencies
        webAppProjectDependencies
            .map { Resource(fsmGradlePluginContext, it, "", false).node }
            .forEach(webResources::add)

        return webResources
    }

    private fun webAppNode(
        annotation: AnnotationInfo,
        webAppName: String,
        webAppClass: ClassInfo,
        webXmlPath: String,
        webResources: LinkedHashSet<Node>
    ): Node = xml("web-app") {
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
            val jarFile = fsmGradlePluginContext.buildJar.get()
            if (fsmGradlePluginContext.extension.addDefaultJarTaskOutputToWebResources && !Resources.isEmptyJarFile(jarFile)) {
                "resource" {
                    attribute(
                        "name",
                        "${fsmGradlePluginContext.projectGroup.get()}:${fsmGradlePluginContext.projectName.get()}"
                    )
                    attribute("version", fsmGradlePluginContext.projectVersion.get())
                    -"lib/${jarFile.name}"
                }
            }
            nodesForWebResources(annotation).forEach(this::addElement)
            webResources.forEach(this::addElement)
        }
        if (annotation.getString("hidden").toBoolean()) {
            "hidden" { -"true" }
        }
    }

    private fun nodesForWebResources(annotation: AnnotationInfo): List<Node> {
        val resources = annotation.getAnnotationValues("webResources")
        val nodes = mutableListOf<Node>()

        resources.forEach { resource ->
            val projectContext = ProjectContext(fsmGradlePluginContext)
            val nameFromAnnotation = expand(resource.getString("name"), mutableMapOf("project" to projectContext))
            val dependencyForName = getCompileDependencyForName(nameFromAnnotation)
            val context = getContextForCurrentResource(dependencyForName, projectContext)
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
        private val LOGGER: Logger = Logging.getLogger(WebAppComponents::class.java)

        private val WEB_APP_TYPES = setOf(
                WebApp::class.qualifiedName,
                AbstractWebApp::class.qualifiedName
        )

        fun fsmResources(project: Project): List<Node> {
            val fsmWebResourcesPath = project.projectDir.resolve(FSMConfigurationsPlugin.FSM_RESOURCES_PATH).absolutePath
            val fsmWebResourcesFolder = File(fsmWebResourcesPath)
            return if (fsmWebResourcesFolder.exists()) {
                fsmWebResourcesFolder.listFiles()?.map { file ->
                    val relPath = fsmWebResourcesFolder.toPath().relativize(file.toPath())
                    xml("resource") {
                        attribute("name", "${project.group}:${project.name}-$relPath")
                        attribute("version", project.version)
                        -relPath.toString()
                    }
                }.orEmpty()
            } else {
                emptyList()
            }
        }
    }
}