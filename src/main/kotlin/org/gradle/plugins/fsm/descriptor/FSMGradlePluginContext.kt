package org.gradle.plugins.fsm.descriptor

import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME
import org.gradle.api.provider.Provider
import org.gradle.plugins.fsm.FSMPluginExtension
import org.gradle.plugins.fsm.compileDependencies
import org.gradle.plugins.fsm.configurations.FSMConfigurationsPlugin
import org.gradle.plugins.fsm.configurations.FSMConfigurationsPlugin.Companion.FS_WEB_COMPILE_CONFIGURATION_NAME
import org.gradle.plugins.fsm.projectDependencies
import org.gradle.plugins.fsm.tasks.bundling.ResolvedDependencyInfo
import org.gradle.plugins.fsm.tasks.bundling.WebAppProjectInfo
import org.redundent.kotlin.xml.Node
import java.io.File

/**
 * The final construction of the module's descriptor requires access to a lot of values that may
 * be changed during the configuration phase. Since Gradle's configuration cache prohibits the usage
 * of objects like [Project], we need to evaluate the required values with specialized [Providers][Provider]
 * or by accessing the [FSMPluginExtension] and [org.gradle.plugins.fsm.configurations.FSMConfigurationsPlugin] directly.
 */
data class FSMGradlePluginContext(

    /** Extension that has been added when the plugin was applied to the project */
    val extension: FSMPluginExtension,

    /** Plugin that manages dependency information */
    val configurationsPlugin: FSMConfigurationsPlugin,

    /** Name of the project applying the FSM plugin */
    val projectName: Provider<String>,

    /** Group ID of the project applying the FSM plugin */
    val projectGroup: Provider<String>,

    /** Description of the project applying the FSM plugin */
    val projectDescription: Provider<String>,

    /** Version of the project applying the FSM plugin */
    val projectVersion: Provider<String>,

    /** Jar file containing the FSM annotations */
    val fsmAnnotationsJar: Provider<File>,

    /** Jar output of the project applying the FSM plugin */
    val buildJar: Provider<File>,

    /** Output of all Jar tasks for projects defined as a dependency */
    val projectJarFiles: Provider<List<File>>,

    /** All artifacts of dependencies defined on the server scope of the project applying the FSM plugin */
    val serverScopeDependencies: Provider<Set<ResolvedDependencyInfo>>,

    /** All artifacts of dependencies defined on the module scope of the project applying the FSM plugin */
    val moduleScopeDependencies: Provider<Set<ResolvedDependencyInfo>>,

    /** All artifacts of `fsWebCompile` dependencies for the project applying the FSM plugin */
    val fsWebCompileArtifacts: Provider<Set<ResolvedDependencyInfo>>,

    /** Information about all projects defined as a web-application in the plugin extension */
    val webAppProjects: Provider<Map<String, WebAppProjectInfo>>,

    /** Resources in `fsm-resources` directories available in projects, both with module and server scope */
    val scopedResources: Provider<List<FsmResources.ResourceEntry>>,

    /** Resources in `fsm-resources` directories available in projects defined for `fsWebCompile` */
    val webCompileResources: Provider<List<Node>>,

    /** All artifacts available on the runtime classpath */
    val runtimeArtifacts: Provider<Set<ResolvedDependencyInfo>>,

    /** Pre-resolved resources for each library, keyed by library name. Null value means no configuration was set. */
    val libraryResources: Provider<Map<String, Set<ResolvedDependencyInfo>?>>
) {
    constructor(project: Project) : this(
        extension = project.extensions.getByType(FSMPluginExtension::class.java),
        configurationsPlugin = project.plugins.getPlugin(FSMConfigurationsPlugin::class.java),
        projectName = project.provider { project.name },
        projectGroup = project.provider { project.group.toString() },
        projectDescription = project.provider { project.description ?: project.name },
        projectVersion = project.provider { project.version.toString()},
        fsmAnnotationsJar = project.provider { project.configurations.getByName("fsmAnnotations").singleFile },
        buildJar = project.provider { project.buildJar() },
        serverScopeDependencies = project.provider { project.serverScopeDependencies() },
        moduleScopeDependencies = project.provider { project.moduleScopeDependencies() },
        projectJarFiles = project.provider { project.compileDependencies().map { it.buildJar() } },
        fsWebCompileArtifacts = project.provider {
            project.configurations.getByName(FS_WEB_COMPILE_CONFIGURATION_NAME)
                .resolvedConfiguration.resolvedArtifacts
                .map { ResolvedDependencyInfo(it) }
                .toSet()
        },
        webAppProjects = project.provider {
            project.extensions.getByType(FSMPluginExtension::class.java).getWebApps().mapValues { (_, projectPath) ->
                WebAppProjectInfo(project.project(projectPath))
            }
        },
        scopedResources = project.provider { FsmResources.fsmResources(project) },
        webCompileResources = project.provider {
            val webCompileConfiguration = project.configurations.getByName(FS_WEB_COMPILE_CONFIGURATION_NAME)
            val projectDependencies = webCompileConfiguration.projectDependencies(project)

            // fsm-resources directory of root project and fsWebCompile subprojects (shared between all webapps)
            projectDependencies.flatMap(WebAppComponents::fsmResources)
        },
        runtimeArtifacts = project.provider {
            project.configurations
                .getByName(RUNTIME_CLASSPATH_CONFIGURATION_NAME)
                .resolvedConfiguration.resolvedArtifacts
                .map { ResolvedDependencyInfo(it) }
                .toSet()
        },
        libraryResources = project.provider {
            val runtimeDeps = project.configurations
                .getByName(RUNTIME_CLASSPATH_CONFIGURATION_NAME)
                .resolvedConfiguration.resolvedArtifacts
                .map { ResolvedDependencyInfo(it) }
                .toSet()
            project.extensions.getByType(FSMPluginExtension::class.java).libraries
                .associate { library ->
                    library.name to library.configuration?.let { config ->
                        LibraryComponents.getResolvedDependencies(runtimeDeps, config)
                    }
                }
        },

    )
}