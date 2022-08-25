package org.gradle.plugins.fsm.annotations

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin

class FSMAnnotationsPlugin implements Plugin<Project> {

    static final String NAME = "de.espirit.firstspirit-module-annotations"

    Project project

    @Override
    void apply(Project target) {
        this.project = target
        project.plugins.apply(JavaPlugin)
        addFsmAnnotationsDependencyToProject()
    }

    def addFsmAnnotationsDependencyToProject() {
        def props = new Properties()
        def versionFile = FSMAnnotationsPlugin.getResourceAsStream("/versions.properties")
        if (versionFile == null) {
            throw new IllegalStateException("Couldn't find versions.properties file that should be a generated resource!")
        }
        versionFile.withCloseable {
            props.load(versionFile)
        }
        project.configurations.maybeCreate("fsmAnnotations")

        project.dependencies {
            def annotationsDep = "com.espirit.moddev.components:annotations:${props.get("fsm-annotations-version")}"
            project.logger.debug("fsmgradleplugin uses $annotationsDep")

            it.compileOnly(annotationsDep)
            it.fsmAnnotations(annotationsDep)
        }
    }
}
