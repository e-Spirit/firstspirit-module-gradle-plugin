package org.gradle.plugins.fsm.descriptor

import org.gradle.api.Project
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.plugins.JavaPlugin
import org.gradle.jvm.tasks.Jar
import org.gradle.plugins.fsm.configurations.FSMConfigurationsPlugin.Companion.FS_MODULE_COMPILE_CONFIGURATION_NAME
import org.gradle.plugins.fsm.configurations.FSMConfigurationsPlugin.Companion.FS_SERVER_COMPILE_CONFIGURATION_NAME
import java.io.File

fun ResolvedArtifact.hasSameModuleAs(other: ResolvedArtifact): Boolean {
    return moduleVersion.id.name == other.moduleVersion.id.name &&
            moduleVersion.id.group == other.moduleVersion.id.group &&
            extension == other.extension &&
            classifier == other.classifier &&
            type == other.type
}

fun Project.buildJar(): File {
    val jarTask = tasks.getByName(JavaPlugin.JAR_TASK_NAME) as Jar
    return jarTask.archiveFile.get().asFile
}

/**
 * Returns all artifacts defined on the server scope, i.e. with `fsServerCompile`
 */
fun Project.serverScopeDependencies(): Set<ResolvedArtifact> {
    val fsModuleCompileConfiguration = configurations.getByName(FS_MODULE_COMPILE_CONFIGURATION_NAME)
    val fsServerCompileConfiguration = configurations.getByName(FS_SERVER_COMPILE_CONFIGURATION_NAME)

    // Remove duplicate resolved resources from module scope
    val resolvedModuleScopeArtifacts = fsModuleCompileConfiguration.resolvedConfiguration.resolvedArtifacts
    val resolvedServerScopeArtifacts = fsServerCompileConfiguration.resolvedConfiguration.resolvedArtifacts
    return resolvedModuleScopeArtifacts.filter {
            // Module scope configuration extends server scope configuration, so we need to filter duplicates
            moduleScoped -> resolvedServerScopeArtifacts.any { it.hasSameModuleAs(moduleScoped) }
    }.toSet()
}


/**
 * Returns all artifacts on the module scope not superseded by server-scoped dependencies
 */
fun Project.moduleScopeDependencies(): Set<ResolvedArtifact> {
    val fsModuleCompileConfiguration = configurations.getByName(FS_MODULE_COMPILE_CONFIGURATION_NAME)
    val resolvedModuleScopeArtifacts = fsModuleCompileConfiguration.resolvedConfiguration.resolvedArtifacts

    // Remove duplicate resolved resources from module scope
    val cleanedCompileDependenciesModuleScoped = resolvedModuleScopeArtifacts.toMutableSet()
    cleanedCompileDependenciesModuleScoped.removeAll(serverScopeDependencies())
    return cleanedCompileDependenciesModuleScoped
}