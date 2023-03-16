package org.gradle.plugins.fsm.descriptor

import org.gradle.api.Project
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.plugins.JavaPlugin
import org.gradle.jvm.tasks.Jar
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