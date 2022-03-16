package org.gradle.plugins.fsm.configurations

import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

class FSMConfigurationsPluginSpecification extends Specification {

    Project project = ProjectBuilder.builder().build()

    def 'applies configuration plugin to project'() {
        when:
        project.apply plugin: FSMConfigurationsPlugin.NAME

        then:
        project.plugins.hasPlugin(FSMConfigurationsPlugin)
    }

    def 'applies java plugin to project'() {
        when:
        project.apply plugin: FSMConfigurationsPlugin.NAME

        then:
        project.plugins.hasPlugin(JavaPlugin)
    }

    def 'jar implementation configuration extends FirstSpirit scopes'() {
        when:
        project.apply plugin: FSMConfigurationsPlugin.NAME
        def implementationConfig = project.configurations.getByName(JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME)

        then:
        implementationConfig.extendsFrom.collect { it.name }.containsAll(FSMConfigurationsPlugin.COMPILE_CONFIGURATIONS)
        !implementationConfig.visible
        implementationConfig.transitive
    }

    def 'fsModuleCompile configuration extends fsServerCompile configuration'() {
        when:
        project.apply plugin: FSMConfigurationsPlugin.NAME
        def fsModuleCompileConfig = project.configurations.getByName(FSMConfigurationsPlugin.FS_MODULE_COMPILE_CONFIGURATION_NAME)

        then:
        fsModuleCompileConfig.extendsFrom.collect { it.name } == [FSMConfigurationsPlugin.FS_SERVER_COMPILE_CONFIGURATION_NAME]
        !fsModuleCompileConfig.visible
        fsModuleCompileConfig.transitive
    }

    def 'project provides and handles fsDependency method'() {
        when:
        project.apply plugin: FSMConfigurationsPlugin.NAME
        project.repositories.add(project.getRepositories().mavenCentral())
        def resultingDependency = null
        use(FSMConfigurationsPluginKt) {
             resultingDependency = project.fsDependency("com.google.guava:guava:24.0-jre")
        }

        then:
        resultingDependency == "com.google.guava:guava:24.0-jre"
    }

    def 'fsDependency method fails for duplicated excluded dependency'() {
        when:
        project.repositories.add(project.getRepositories().mavenCentral())
        project.apply plugin: FSMConfigurationsPlugin.NAME
        use (FSMConfigurationsPluginKt) {
            project.fsDependency(dependency: "com.google.guava:guava:24.0-jre", maxVersion: "31.0")
            project.fsDependency(dependency: "com.google.guava:guava:24.0-jre", minVersion: "2.0")
        }

        then:
        thrown(IllegalArgumentException.class)
    }

    def 'fsDependency method fails on non-String type for minVersion argument'() {
        when:
        project.repositories.add(project.getRepositories().mavenCentral())
        project.apply plugin: FSMConfigurationsPlugin.NAME
        use (FSMConfigurationsPluginKt) {
            project.fsDependency("com.google.guava:guava:24.0-jre", true, true)
        }

        then:
        thrown(ClassCastException.class)
    }

    def 'fsDependency method fails on non-String type for maxVersion argument'() {
        when:
        project.repositories.add(project.getRepositories().mavenCentral())
        project.apply plugin: FSMConfigurationsPlugin.NAME
        use (FSMConfigurationsPluginKt) {
            project.fsDependency("com.google.guava:guava:24.0-jre", true, "1.0.0", true)
        }

        then:
        thrown(ClassCastException.class)
    }
}
