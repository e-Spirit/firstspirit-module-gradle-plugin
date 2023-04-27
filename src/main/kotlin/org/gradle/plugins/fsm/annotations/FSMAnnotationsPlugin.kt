package org.gradle.plugins.fsm.annotations

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.plugins.fsm.FSMPlugin
import java.util.*

class FSMAnnotationsPlugin: Plugin<Project> {

    @Override
    override fun apply(project: Project) {
        project.plugins.apply(JavaPlugin::class.java)
        addFsmAnnotationsDependencyToProject(project)
    }

    private fun addFsmAnnotationsDependencyToProject(project: Project) {
        val props = Properties()
        val versionFile = FSMAnnotationsPlugin::class.java.getResourceAsStream(FSMPlugin.VERSIONS_PROPERTIES_FILE)
            ?: throw IllegalStateException("Couldn't find properties file '${FSMPlugin.VERSIONS_PROPERTIES_FILE}'!")

        versionFile.use {
            props.load(it)
        }

        project.configurations.maybeCreate("fsmAnnotations")
        project.dependencies.let {
            val annotationsDep = "com.espirit.moddev.components:annotations:${props["fsm-annotations-version"]}"
            project.logger.debug("fsmgradleplugin uses $annotationsDep")

            it.add("compileOnly", annotationsDep)
            it.add("fsmAnnotations", annotationsDep)
        }

    }

    companion object {
        const val NAME = "de.espirit.firstspirit-module-annotations"
    }

}