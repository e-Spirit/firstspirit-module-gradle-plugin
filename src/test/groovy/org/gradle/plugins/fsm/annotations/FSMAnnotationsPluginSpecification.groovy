package org.gradle.plugins.fsm.annotations

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

class FSMAnnotationsPluginSpecification extends Specification {


    Project project = ProjectBuilder.builder().build()

    def 'applies annotation plugin to project'() {
        when:
        project.apply plugin: FSMAnnotationsPlugin.NAME

        then:
        project.plugins.hasPlugin(FSMAnnotationsPlugin)
    }

    def 'adds annotation dependency to project'() {
        when:
        project.apply plugin: FSMAnnotationsPlugin.NAME

        then:
//        TODO: Transfer addsAnnotationsDependencyToProject logic to here from
//        https://git.e-spirit.de/projects/DEVEX/repos/fsmgradleplugin/pull-requests/37/diff#src/test/groovy/org/gradle/plugins/fsm/FSMPluginTest.groovy
        throw new IllegalStateException("This is blocked by DEVEX-226")
    }
}
