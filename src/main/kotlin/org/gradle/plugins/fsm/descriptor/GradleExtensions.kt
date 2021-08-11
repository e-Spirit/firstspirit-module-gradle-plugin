package org.gradle.plugins.fsm.descriptor

import org.gradle.api.artifacts.ResolvedArtifact

fun ResolvedArtifact.hasSameModuleAs(other: ResolvedArtifact): Boolean {
    return moduleVersion.id.name == other.moduleVersion.id.name &&
            moduleVersion.id.group == other.moduleVersion.id.group &&
            extension == other.extension &&
            classifier == other.classifier &&
            type == other.type
}