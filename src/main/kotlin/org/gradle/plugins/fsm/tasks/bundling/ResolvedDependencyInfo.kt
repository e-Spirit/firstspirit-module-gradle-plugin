package org.gradle.plugins.fsm.tasks.bundling

import org.gradle.api.artifacts.ResolvedArtifact
import java.io.File

data class ResolvedDependencyInfo(
    val groupId: String,
    val moduleName: String,
    val moduleVersion: String,
    val classifier: String?,
    val extension: String?,
    val file: File
) {
    constructor(artifact: ResolvedArtifact) : this(
        groupId = artifact.moduleVersion.id.group,
        moduleName = artifact.moduleVersion.id.name,
        moduleVersion = artifact.moduleVersion.id.version,
        classifier = artifact.classifier,
        extension = artifact.extension,
        file = artifact.file
    )

    fun hasSameModuleAs(other: ResolvedDependencyInfo): Boolean {
        return moduleName == other.moduleName &&
                groupId == other.groupId &&
                extension == other.extension &&
                classifier == other.classifier
    }

    fun hasSameModuleAs(other: ResolvedArtifact): Boolean {
        return moduleName == other.moduleVersion.id.name &&
                groupId == other.moduleVersion.id.group &&
                extension == other.extension &&
                classifier == other.classifier
    }
}