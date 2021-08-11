package org.gradle.plugins.fsm.configurations

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.plugins.JavaLibraryPlugin
import org.gradle.api.plugins.JavaPlugin

fun Project.fsDependency(vararg args: Any): String {
    if (args.isEmpty()) {
        throw IllegalArgumentException("Please provide at least a dependency as String for fsDependency! You can also use named parameters.")
    }

    return if (args[0] is Map<*, *>) {
        fsDependencyFromMap(this, args[0] as Map<*, *>)
    } else {
        val argsStack = ArrayDeque(args.drop(1))
        val skipInLegacy = argsStack.removeFirstOrNull() as Boolean? ?: false
        val minVersion = argsStack.removeFirstOrNull() as String?
        val maxVersion = argsStack.removeFirstOrNull() as String?
        fsDependencyFromArgs(this, args[0] as String?, skipInLegacy, minVersion, maxVersion)
    }
}

fun fsDependencyFromMap(project: Project, map: Map<*, *>): String {
    val skipInLegacy = map["skipInLegacy"] as Boolean? ?: false

    return fsDependencyFromArgs(
        project, map["dependency"] as String?, skipInLegacy,
        map["minVersion"] as String?, map["maxVersion"] as String?
    )
}

fun fsDependencyFromArgs(
    project: Project,
    dependency: String?,
    skipInLegacy: Boolean = false,
    minVersion: String? = null,
    maxVersion: String? = null
): String {
    if (dependency.isNullOrBlank()) {
        error("You have to specify a non-empty dependency!")
    }

    if (skipInLegacy) {
        FSMConfigurationsPlugin.LOGGER.debug("Adding dependency $dependency as skippedInLegacy for project $project")
        project.dependencies.add(FSMConfigurationsPlugin.FS_SKIPPED_IN_LEGACY_CONFIGURATION_NAME, dependency)
    }

    if (minVersion != null || maxVersion != null) {
        val plugin = project.plugins.getPlugin(FSMConfigurationsPlugin::class.java)
        val dependencyConfigurations = plugin.getDependencyConfigurations()

        if (dependencyConfigurations.any { it.dependency == dependency }) {
            throw IllegalArgumentException("You cannot specify minVersion or maxVersion twice for dependency ${dependency}!")
        } else {
            val minMaxVersionDefinition = MinMaxVersion(dependency, minVersion, maxVersion)
            FSMConfigurationsPlugin.LOGGER.debug("Adding definition for minVersion and maxVersion to project: $minMaxVersionDefinition")
            dependencyConfigurations.add(minMaxVersionDefinition)
        }
    }

    return dependency
}

/**
 * A [Plugin] that defines different configurations for FSM project dependencies.
 */
class FSMConfigurationsPlugin : Plugin<Project> {

    private val minMaxVersions = mutableSetOf<MinMaxVersion>()

    fun getDependencyConfigurations(): MutableSet<MinMaxVersion> {
        return minMaxVersions
    }

    override fun apply(project: Project) {
        project.plugins.apply(JavaLibraryPlugin::class.java)
        configureConfigurations(project.configurations)
    }

    private fun configureConfigurations(configurationContainer: ConfigurationContainer) {

        val fsServerCompileConfiguration = configurationContainer
            .create(FS_SERVER_COMPILE_CONFIGURATION_NAME)
            .setVisible(false)
            .setDescription("Added automatically to module.xml with server scope")

        val fsModuleCompileConfiguration = configurationContainer
            .create(FS_MODULE_COMPILE_CONFIGURATION_NAME)
            .extendsFrom(fsServerCompileConfiguration)
            .setVisible(false)
            .setDescription("Added automatically to module.xml with module scope")

        val fsWebCompileConfiguration = configurationContainer
            .create(FS_WEB_COMPILE_CONFIGURATION_NAME)
            .setVisible(false)
            .setDescription("Added automatically to web resources of WebApp components in module.xml")

        configurationContainer
            .create(FS_SKIPPED_IN_LEGACY_CONFIGURATION_NAME)
            .setVisible(false)
            .setDescription("Those dependencies are not included in the module.xml, but in the module-isolated.xml")


        val provideCompileConfiguration = configurationContainer
            .create(PROVIDED_COMPILE_CONFIGURATION_NAME)
            .setVisible(false)
            .setDescription("Additional compile classpath for libraries that should not be part of the FSM archive.")

        val provideRuntimeConfiguration = configurationContainer
            .create(PROVIDED_RUNTIME_CONFIGURATION_NAME)
            .setVisible(false)
            .extendsFrom(provideCompileConfiguration)
            .setDescription("Additional runtime classpath for libraries that should not be part of the FSM archive.")

        configurationContainer.getByName(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME)
            .extendsFrom(provideCompileConfiguration)
        configurationContainer.getByName(JavaPlugin.RUNTIME_ONLY_CONFIGURATION_NAME)
            .extendsFrom(provideRuntimeConfiguration)

        configurationContainer.getByName(JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME)
            .extendsFrom(fsServerCompileConfiguration)
            .extendsFrom(fsModuleCompileConfiguration)
            .extendsFrom(fsWebCompileConfiguration)
    }

    companion object {
        val LOGGER: Logger = Logging.getLogger(FSMConfigurationsPlugin::class.java)

        const val PROVIDED_COMPILE_CONFIGURATION_NAME = "fsProvidedCompile"
        const val PROVIDED_RUNTIME_CONFIGURATION_NAME = "fsProvidedRuntime"

        const val FS_SERVER_COMPILE_CONFIGURATION_NAME = "fsServerCompile"
        const val FS_MODULE_COMPILE_CONFIGURATION_NAME = "fsModuleCompile"

        const val FS_WEB_COMPILE_CONFIGURATION_NAME = "fsWebCompile"

        val FS_CONFIGURATIONS = setOf(
            FS_SERVER_COMPILE_CONFIGURATION_NAME,
            FS_MODULE_COMPILE_CONFIGURATION_NAME,
            FS_WEB_COMPILE_CONFIGURATION_NAME
        )

        val COMPILE_CONFIGURATIONS = listOf(
            FS_SERVER_COMPILE_CONFIGURATION_NAME,
            FS_MODULE_COMPILE_CONFIGURATION_NAME,
            FS_WEB_COMPILE_CONFIGURATION_NAME
        )

        const val FS_SKIPPED_IN_LEGACY_CONFIGURATION_NAME = "skippedInLegacy"
        const val NAME = "de.espirit.firstspirit-module-configurations"

        const val FSM_RESOURCES_PATH = "src/main/fsm-resources"
    }

}