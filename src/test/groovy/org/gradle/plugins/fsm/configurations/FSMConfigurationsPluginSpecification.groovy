package org.gradle.plugins.fsm.configurations

import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.plugins.fsm.FSMPlugin
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

    def 'jar compile configuration extends FirstSpirit scopes'() {
        when:
        project.apply plugin: FSMConfigurationsPlugin.NAME
        def compileConfig = project.configurations.getByName(JavaPlugin.COMPILE_CONFIGURATION_NAME)

        then:
        compileConfig.extendsFrom.collect { it.name } == FSMConfigurationsPlugin.COMPILE_CONFIGURATIONS
        !compileConfig.visible
        compileConfig.transitive
    }

    def 'jar runtime configuration extends FirstSpirit scopes'() {
        when:
        project.apply plugin: FSMConfigurationsPlugin.NAME
        def runtimeConfig = project.configurations.getByName(JavaPlugin.RUNTIME_CONFIGURATION_NAME)

        then:
        runtimeConfig.extendsFrom.collect { it.name } == [JavaPlugin.COMPILE_CONFIGURATION_NAME, FSMConfigurationsPlugin.PROVIDED_RUNTIME_CONFIGURATION_NAME]
        !runtimeConfig.visible
        runtimeConfig.transitive
    }

    def 'jar compileOnly configuration extends FirstSpirit scopes'() {
        when:
        project.apply plugin: FSMConfigurationsPlugin.NAME
        def runtimeConfig = project.configurations.getByName(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME)

        then:
        runtimeConfig.extendsFrom.collect { it.name } == [FSMConfigurationsPlugin.PROVIDED_COMPILE_CONFIGURATION_NAME]
        !runtimeConfig.visible
        runtimeConfig.transitive
    }

    def 'fsProvidedRuntime configuration extends compile configuration'() {
        when:
        project.apply plugin: FSMConfigurationsPlugin.NAME

        then:
        def providedRuntimeConfig = project.configurations.getByName(FSMConfigurationsPlugin.PROVIDED_RUNTIME_CONFIGURATION_NAME)
        providedRuntimeConfig.extendsFrom.collect { it.name } == [FSMConfigurationsPlugin.PROVIDED_COMPILE_CONFIGURATION_NAME]
        !providedRuntimeConfig.visible
        providedRuntimeConfig.transitive
    }

    def 'project provides and handles fsDependency method'() {
        when:
        project.apply plugin: FSMConfigurationsPlugin.NAME
        project.repositories.add(project.getRepositories().mavenCentral())
        def resultingDependency = project.fsDependency("com.google.guava:guava:24.0-jre", true)
        def skippedInLegacyDependencies = project.configurations.getByName(FSMPlugin.FS_SKIPPED_IN_LEGACY_CONFIGURATION_NAME).dependencies.collect { it }

        then:
        resultingDependency == "com.google.guava:guava:24.0-jre"
        skippedInLegacyDependencies.size() == 1
        skippedInLegacyDependencies.get(0).group == "com.google.guava"
        skippedInLegacyDependencies.get(0).name == "guava"
        skippedInLegacyDependencies.get(0).version == "24.0-jre"
    }


    def 'plugin exposes named arguments method for excluded dependencies'() {
        when:
        project.repositories.add(project.getRepositories().mavenCentral())
        project.apply plugin: FSMConfigurationsPlugin.NAME
        def resultingDependency = project.fsDependency(dependency: "com.google.guava:guava:24.0-jre", skipInLegacy:  true, maxVersion: "31.0")
        def skippedInLegacyDependencies = project.configurations.getByName(FSMConfigurationsPlugin.FS_SKIPPED_IN_LEGACY_CONFIGURATION_NAME).dependencies.collect { it }
        def dependencyConfigurations = project.plugins.getPlugin(FSMConfigurationsPlugin.class).getDependencyConfigurations()

        then:
        resultingDependency == "com.google.guava:guava:24.0-jre"
        skippedInLegacyDependencies.size() == 1
        skippedInLegacyDependencies.get(0).group == "com.google.guava"
        skippedInLegacyDependencies.get(0).name == "guava"
        skippedInLegacyDependencies.get(0).version == "24.0-jre"

        dependencyConfigurations.contains(
                new FSMConfigurationsPlugin.MinMaxVersion("com.google.guava:guava:24.0-jre", null, "31.0"))
    }

    def 'fsDependency method fails for duplicated excluded dependency'() {
        when:
        project.repositories.add(project.getRepositories().mavenCentral())
        project.apply plugin: FSMConfigurationsPlugin.NAME
        project.fsDependency(dependency: "com.google.guava:guava:24.0-jre", skipInLegacy:  true, maxVersion: "31.0")
        project.fsDependency(dependency: "com.google.guava:guava:24.0-jre", minVersion: "2.0")

        then:
        thrown(IllegalArgumentException.class)

    }

    def 'fsDependency method fails on non-boolean type for skipInLegacy argument'() {
        when:
        project.repositories.add(project.getRepositories().mavenCentral())
        project.apply plugin: FSMConfigurationsPlugin.NAME
        project.fsDependency("com.google.guava:guava:24.0-jre", "31.0")

        then:
        thrown(IllegalArgumentException.class)
    }

    def 'fsDependency method fails on non-String type for minVersion argument'() {
        when:
        project.repositories.add(project.getRepositories().mavenCentral())
        project.apply plugin: FSMConfigurationsPlugin.NAME
        project.fsDependency("com.google.guava:guava:24.0-jre", true, true)

        then:
        thrown(IllegalArgumentException.class)
    }

    def 'fsDependency method fails on non-String type for maxVersion argument'() {
        when:
        project.repositories.add(project.getRepositories().mavenCentral())
        project.apply plugin: FSMConfigurationsPlugin.NAME
        project.fsDependency("com.google.guava:guava:24.0-jre", true, "1.0.0", true)

        then:
        thrown(IllegalArgumentException.class)
    }
}
