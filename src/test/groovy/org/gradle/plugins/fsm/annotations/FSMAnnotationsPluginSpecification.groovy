package org.gradle.plugins.fsm.annotations

import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.plugins.fsm.FSMPlugin
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

    def 'applies java plugin to project'() {
        when:
        project.apply plugin: FSMAnnotationsPlugin.NAME

        then:
        project.plugins.hasPlugin(JavaPlugin)
    }

    def 'adds annotation dependency to project'() {
        when:
        project.apply plugin: FSMAnnotationsPlugin.NAME
        def annotationDependency = project.getConfigurations().getByName("compileOnly").dependencies.find {
            it.group == 'com.espirit.moddev.components' && it.name == 'annotations'
        }

        Properties props = new Properties()
        props.load(FSMPlugin.class.getResourceAsStream('/versions.properties'))

        then:
        annotationDependency != null
        props.get('fsm-annotations-version') == annotationDependency.version
    }
}
