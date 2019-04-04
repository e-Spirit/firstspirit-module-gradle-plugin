package org.gradle.plugins.fsm.annotations

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.logging.Logging
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
        Properties props = new Properties()
        InputStream versionsFile = FSMAnnotationsPlugin.class.getResourceAsStream("/versions.properties")
        if(versionsFile == null) {
            throw new IllegalStateException("Couldn't find versions.properties file that should be a generated resource!")
        }
        props.load(versionsFile)

        project.dependencies {
            def annotationsDep = "com.espirit.moddev.components:annotations:${props.get("fsm-annotations-version")}"
            Logging.getLogger(this.getClass()).debug("fsmgradleplugin uses $annotationsDep")

            delegate.compileOnly(annotationsDep)
        }
    }
}
