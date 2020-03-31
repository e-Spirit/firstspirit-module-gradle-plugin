/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.plugins.fsm.configurations

import groovy.transform.Immutable
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.logging.Logging
import org.gradle.api.plugins.JavaPlugin

/**
 * <p>A {@link Plugin} that defines different configurations for FSM project dependencies.</p>
 */
class FSMConfigurationsPlugin implements Plugin<Project> {

    static final String PROVIDED_COMPILE_CONFIGURATION_NAME = "fsProvidedCompile"
    static final String PROVIDED_RUNTIME_CONFIGURATION_NAME = "fsProvidedRuntime"

    static final String FS_SERVER_COMPILE_CONFIGURATION_NAME = "fsServerCompile"
    static final String FS_MODULE_COMPILE_CONFIGURATION_NAME = "fsModuleCompile"

    static final String FS_WEB_COMPILE_CONFIGURATION_NAME = "fsWebCompile"

    static final Set<String> FS_CONFIGURATIONS = [
            FS_SERVER_COMPILE_CONFIGURATION_NAME,
            FS_MODULE_COMPILE_CONFIGURATION_NAME,
            FS_WEB_COMPILE_CONFIGURATION_NAME
    ]

    static final List<String> COMPILE_CONFIGURATIONS = [FS_SERVER_COMPILE_CONFIGURATION_NAME, FS_MODULE_COMPILE_CONFIGURATION_NAME, FS_WEB_COMPILE_CONFIGURATION_NAME]

    static final String FS_SKIPPED_IN_LEGACY_CONFIGURATION_NAME = "skippedInLegacy"
    static String NAME = "de.espirit.firstspirit-module-configurations"

    @Immutable
    static class MinMaxVersion {
        String dependency
        String minVersion
        String maxVersion
    }
    private Set<MinMaxVersion> minMaxVersions = new HashSet<>()
    Set<MinMaxVersion> getDependencyConfigurations() {
        return minMaxVersions
    }

    private Project project

    String fsDependency(Map<String, Object> map) {
        return fsDependency(map.dependency, map.skipInLegacy, map.minVersion, map.maxVersion)
    }

    String fsDependency(String dependency, boolean skipInLegacy = false, String minVersion = null, String maxVersion = null) {
        if(dependency == null || dependency.allWhitespace) {
            throw new IllegalStateException('You have to specify a non-empty dependency!')
        }

        if(skipInLegacy) {
            Logging.getLogger(FSMConfigurationsPlugin).debug("Adding dependency $dependency as skippedInLegacy for project $project")
            project.dependencies.add(FS_SKIPPED_IN_LEGACY_CONFIGURATION_NAME, dependency)
        }

        if(minVersion != null || maxVersion != null) {
            if(minMaxVersions.find { it.dependency == dependency } != null) {
                throw new IllegalStateException("You cannot specify minVersion or maxVersion twice for depdendency ${dependency}!")
            } else {
                def minMaxVersionDefinition = new MinMaxVersion(dependency, minVersion, maxVersion)
                Logging.getLogger(this.getClass()).debug("Adding definition for minVersion and maxVersion to project: $minMaxVersionDefinition")
                minMaxVersions.add(minMaxVersionDefinition)
            }
        }

        return dependency
    }

    @Override
    void apply(Project project) {
        this.project = project

        project.getPlugins().apply(JavaPlugin.class)
        configureConfigurations(project.getConfigurations())

        project.ext.fsDependency = { Object... args ->
            if(args.length < 1) {
                throw new IllegalArgumentException("Please provide at least a dependency as String for fsDependency! You can also use named parameters.")
            }

            def dependency
            def isCalledWithNamedParams = args[0] instanceof Map

            try {
                if(isCalledWithNamedParams) {
                    dependency = fsDependency(args[0] as Map<String, Object>)
                } else {
                    dependency = fsDependency(*args)
                }
            } catch (MissingMethodException mme) {
                throw new IllegalArgumentException("The given argument types are not supported!", mme)
            }

            return dependency
        }
    }


    private void configureConfigurations(ConfigurationContainer configurationContainer) {

        Configuration fsServerCompileConfiguration = configurationContainer
                .create(FS_SERVER_COMPILE_CONFIGURATION_NAME)
                .setVisible(false)
                .setDescription("Added automatically to module.xml with server scope")

        Configuration fsModuleCompileConfiguration = configurationContainer
                .create(FS_MODULE_COMPILE_CONFIGURATION_NAME)
                .extendsFrom(fsServerCompileConfiguration)
                .setVisible(false)
                .setDescription("Added automatically to module.xml with module scope")

        Configuration fsWebCompileConfiguration = configurationContainer
                .create(FS_WEB_COMPILE_CONFIGURATION_NAME)
                .setVisible(false)
                .setDescription("Added automatically to web resources of WebApp components in module.xml")

        Configuration skippedInLegacy = configurationContainer
                .create(FS_SKIPPED_IN_LEGACY_CONFIGURATION_NAME)
                .setVisible(false)
                .setDescription("Those dependencies are not included in the module.xml, but in the module-isolated.xml")


        Configuration provideCompileConfiguration = configurationContainer
            .create(PROVIDED_COMPILE_CONFIGURATION_NAME)
            .setVisible(false)
            .setDescription("Additional compile classpath for libraries that should not be part of the FSM archive.")

        Configuration provideRuntimeConfiguration = configurationContainer
                .create(PROVIDED_RUNTIME_CONFIGURATION_NAME)
                .setVisible(false)
                .extendsFrom(provideCompileConfiguration)
                .setDescription("Additional runtime classpath for libraries that should not be part of the FSM archive.")

        configurationContainer.getByName(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME)
                .extendsFrom(provideCompileConfiguration)
        configurationContainer.getByName(JavaPlugin.RUNTIME_CONFIGURATION_NAME)
            .extendsFrom(provideRuntimeConfiguration)

        configurationContainer.getByName(JavaPlugin.COMPILE_CONFIGURATION_NAME)
                .extendsFrom(fsServerCompileConfiguration)
                .extendsFrom(fsModuleCompileConfiguration)
                .extendsFrom(fsWebCompileConfiguration)
    }

}
