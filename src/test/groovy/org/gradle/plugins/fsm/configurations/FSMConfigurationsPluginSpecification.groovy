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

    def 'jar runtimeOnly configuration extends FirstSpirit scopes'() {
        when:
        project.apply plugin: FSMConfigurationsPlugin.NAME
        def runtimeConfig = project.configurations.getByName(JavaPlugin.RUNTIME_ONLY_CONFIGURATION_NAME)

        then:
        runtimeConfig.extendsFrom.collect { it.name } == [FSMConfigurationsPlugin.PROVIDED_RUNTIME_CONFIGURATION_NAME]
        !runtimeConfig.visible
        runtimeConfig.transitive
    }

    def 'jar compileOnly configuration extends FirstSpirit scopes'() {
        when:
        project.apply plugin: FSMConfigurationsPlugin.NAME
        def runtimeConfig = project.configurations.getByName(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME)

        then:
        runtimeConfig.extendsFrom.collect { it.name } == [JavaPlugin.COMPILE_ONLY_API_CONFIGURATION_NAME, FSMConfigurationsPlugin.PROVIDED_COMPILE_CONFIGURATION_NAME]
        !runtimeConfig.visible
        runtimeConfig.transitive
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

    def 'runtimeOnly configuration extends fsProvidedRuntime configuration'() {
        when:
        project.apply plugin: FSMConfigurationsPlugin.NAME
        def providedRuntimeConfig = project.configurations.getByName(FSMConfigurationsPlugin.PROVIDED_RUNTIME_CONFIGURATION_NAME)
        def runtimeOnlyConfig = project.configurations.getByName(JavaPlugin.RUNTIME_ONLY_CONFIGURATION_NAME)

        then:
        runtimeOnlyConfig.extendsFrom providedRuntimeConfig
        !providedRuntimeConfig.visible
        providedRuntimeConfig.transitive
    }

    def 'project provides and handles fsDependency method'() {
        when:
        project.apply plugin: FSMConfigurationsPlugin.NAME
        project.repositories.add(project.getRepositories().mavenCentral())
        def resultingDependency = null
        use(FSMConfigurationsPluginKt) {
             resultingDependency = project.fsDependency("com.google.guava:guava:24.0-jre", true)
        }
        def skippedInLegacyDependencies = project.configurations.getByName(FSMConfigurationsPlugin.FS_SKIPPED_IN_LEGACY_CONFIGURATION_NAME).dependencies.collect { it }
        def skippedDependency = skippedInLegacyDependencies.get(0)

        then:
        resultingDependency == "com.google.guava:guava:24.0-jre"
        skippedInLegacyDependencies.size() == 1
        skippedDependency.group == "com.google.guava"
        skippedDependency.name == "guava"
        skippedDependency.version == "24.0-jre"
    }

    def 'plugin exposes named arguments method for excluded dependencies'() {
        when:
        project.repositories.add(project.getRepositories().mavenCentral())
        project.apply plugin: FSMConfigurationsPlugin.NAME
        def resultingDependency = null
        use (FSMConfigurationsPluginKt) {
            resultingDependency = project.fsDependency(dependency: "com.google.guava:guava:24.0-jre", skipInLegacy: true, maxVersion: "31.0")
        }
        def skippedInLegacyDependencies = project.configurations.getByName(FSMConfigurationsPlugin.FS_SKIPPED_IN_LEGACY_CONFIGURATION_NAME).dependencies.collect { it }
        def dependencyConfigurations = project.plugins.getPlugin(FSMConfigurationsPlugin.class).getDependencyConfigurations()

        then:
        resultingDependency == "com.google.guava:guava:24.0-jre"
        skippedInLegacyDependencies.size() == 1
        skippedInLegacyDependencies.get(0).group == "com.google.guava"
        skippedInLegacyDependencies.get(0).name == "guava"
        skippedInLegacyDependencies.get(0).version == "24.0-jre"

        dependencyConfigurations.contains(
                new MinMaxVersion("com.google.guava:guava:24.0-jre", null, "31.0"))
    }

    def 'fsDependency method fails for duplicated excluded dependency'() {
        when:
        project.repositories.add(project.getRepositories().mavenCentral())
        project.apply plugin: FSMConfigurationsPlugin.NAME
        use (FSMConfigurationsPluginKt) {
            project.fsDependency(dependency: "com.google.guava:guava:24.0-jre", skipInLegacy: true, maxVersion: "31.0")
            project.fsDependency(dependency: "com.google.guava:guava:24.0-jre", minVersion: "2.0")
        }

        then:
        thrown(IllegalArgumentException.class)
    }

    def 'fsDependency method fails on non-boolean type for skipInLegacy argument'() {
        when:
        project.repositories.add(project.getRepositories().mavenCentral())
        project.apply plugin: FSMConfigurationsPlugin.NAME
        use (FSMConfigurationsPluginKt) {
            project.fsDependency("com.google.guava:guava:24.0-jre", "31.0")
        }

        then:
        thrown(ClassCastException.class)
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
