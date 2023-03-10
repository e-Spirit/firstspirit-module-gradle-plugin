package org.gradle.plugins.fsm.annotations

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import java.util.*

class FSMAnnotationsPlugin: Plugin<Project> {

    @Override
    override fun apply(project: Project) {
        project.plugins.apply(JavaPlugin::class.java)
        addFsmAnnotationsDependencyToProject(project)
    }

    private fun addFsmAnnotationsDependencyToProject(project: Project) {
        val props = Properties()
        val versionFile = FSMAnnotationsPlugin::class.java.getResourceAsStream("/versions.properties")
            ?: throw IllegalStateException("Couldn't find versions.properties file that should be a generated resource!")

        versionFile.use {
            props.load(it)
        }

        project.dependencies.let {
            val annotationsDep = "com.espirit.moddev.components:annotations:${props["fsm-annotations-version"]}"
            project.logger.debug("fsmgradleplugin uses $annotationsDep")

            it.add("compileOnly", annotationsDep)
        }
    }

    companion object {
        const val NAME = "de.espirit.firstspirit-module-annotations"
    }

}