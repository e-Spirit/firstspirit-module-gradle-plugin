package org.gradle.plugins.fsm.annotations

import org.gradle.api.Plugin
import org.gradle.api.Project

class FSMAnnotationsPlugin implements Plugin<Project> {

    static final String NAME = "fsmannotationsgradleplugin"

    Project project

    @Override
    void apply(Project target) {
        this.project = target
//        TODO: Transfer addFsmAnnotationsDependencyToProject logic from FSMPlugin to here
//        https://git.e-spirit.de/projects/DEVEX/repos/fsmgradleplugin/pull-requests/37/diff#src/main/groovy/org/gradle/plugins/fsm/FSMPlugin.groovy
    }
}
