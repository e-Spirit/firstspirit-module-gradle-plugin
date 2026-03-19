package org.gradle.plugins.fsm.descriptor

/**
 * Simple context used by Gradle's template engine to resolve values like `project.name`
 */
data class ProjectContext(
    val name: String,
    val group: String,
    val version: String
) {
    constructor(fsmGradlePluginContext: FSMGradlePluginContext) : this(
        fsmGradlePluginContext.projectName.get(),
        fsmGradlePluginContext.projectGroup.get(),
        fsmGradlePluginContext.projectVersion.get()
    )
}
