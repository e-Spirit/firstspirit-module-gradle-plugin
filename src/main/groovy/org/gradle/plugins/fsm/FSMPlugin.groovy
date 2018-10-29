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
package org.gradle.plugins.fsm

import groovy.transform.Immutable
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.Dependency
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.artifacts.publish.ArchivePublishArtifact
import org.gradle.api.internal.plugins.DefaultArtifactPublicationSet
import org.gradle.api.logging.Logging
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.bundling.Jar
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.plugins.fsm.tasks.bundling.FSM
import org.gradle.plugins.fsm.tasks.verification.IsolationCheck

/**
 * <p>A {@link Plugin} with tasks which assemble a (java) application into an FSM (FirstSpirit module) file.</p>
 */
class FSMPlugin implements Plugin<Project> {

    public static final String NAME = "fsmgradleplugin"
    public static final String FSM_TASK_NAME = "fsm"
    public static final String ISOLATION_CHECK_TASK_NAME = "isolationCheck"

    static final String PROVIDED_COMPILE_CONFIGURATION_NAME = "fsProvidedCompile"
    static final String PROVIDED_RUNTIME_CONFIGURATION_NAME = "fsProvidedRuntime"

    static final String FS_SERVER_COMPILE_CONFIGURATION_NAME = "fsServerCompile"
    static final String FS_MODULE_COMPILE_CONFIGURATION_NAME = "fsModuleCompile"

    static final String FS_WEB_COMPILE_CONFIGURATION_NAME = "fsWebCompile"

    static final String FS_SKIPPED_IN_LEGACY_CONFIGURATION_NAME = "skippedInLegacy"

    @Immutable
    static class MinMaxVersion {
        String dependency
        String minVersion
        String maxVersion
    }
    private Set<MinMaxVersion> minMaxVersions = new HashSet<>()
    public Set<MinMaxVersion> getDependencyConfigurations() {
        return minMaxVersions
    }


    @Override
    public void apply(Project project) {

        project.getPlugins().apply(JavaPlugin.class)
        configureConfigurations(project.getConfigurations())

        FSMPluginExtension fsmPluginExtension = project.getExtensions().create("fsm", FSMPluginExtension.class)

        project.ext.fsDependency = { Object... args ->
            if(args.length < 1) {
                throw new IllegalArgumentException("Please provide at least a dependency as String for fsDependency! You can also use named parameters.")
            }

            String dependency
            boolean skipInLegacy
            String minVersion
            String maxVersion

            if(args[0] instanceof Map) {
                dependency = args[0].dependency
                skipInLegacy = args[0].skipInLegacy
                minVersion = args[0].minVersion
                maxVersion = args[0].maxVersion
            } else {

                def secondArgIsNoBoolean = args.length > 1 && !(args[1] instanceof Boolean)
                if(secondArgIsNoBoolean) {
                    throw new IllegalArgumentException("Please provide the skipInLegacyParameter as a boolean.")
                }

                def thirdArgIsNoString = args.length > 2 && !(args[2] instanceof String)
                if(thirdArgIsNoString) {
                    throw new IllegalArgumentException("Please provide the minVersion as a String.")
                }

                def fourthArgIsNoString = args.length > 3 && !(args[3] instanceof String)
                if(fourthArgIsNoString) {
                    throw new IllegalArgumentException("Please provide the maxVersion as a String.")
                }

                dependency = args[0]
                skipInLegacy = args.length > 1 ? args[1] : false
                minVersion = args.length > 2 ? args[2] : null
                maxVersion = args.length > 3 ? args[3] : null
            }

            if(skipInLegacy) {
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

        project.getPlugins().apply(JavaPlugin.class)

        FSM fsm = configureFsmTask(project)

        IsolationCheck isolationCheck = configureIsolationCheckTask(project, fsm)
        Task checkTask = project.getTasksByName(JavaBasePlugin.CHECK_TASK_NAME, false).iterator().next()
        checkTask.dependsOn(isolationCheck)


        configureJarTask(project)
    }

    private FSM configureFsmTask(final Project project) {

        FSM fsm = project.getTasks().create(FSM_TASK_NAME, FSM.class)
        fsm.setDescription("Assembles a fsm archive containing the FirstSpirit module.")
        fsm.setGroup(BasePlugin.BUILD_GROUP)

        addPublication(project, fsm)

        fsm.dependsOn({ project.getConvention()
            .getPlugin(JavaPluginConvention.class)
            .getSourceSets()
            .getByName(SourceSet.MAIN_SOURCE_SET_NAME)
            .getRuntimeClasspath()
        })

        fsm.dependsOn({ JavaPlugin.JAR_TASK_NAME})

        fsm.classpath({
            final FileCollection runtimeClasspath = project
                .getConvention()
                .getPlugin(JavaPluginConvention.class)
                .getSourceSets()
                .getByName(SourceSet.MAIN_SOURCE_SET_NAME)
                .getRuntimeClasspath()
            final FileCollection outputs = project.getTasks()
                .findByName(JavaPlugin.JAR_TASK_NAME)
                .getOutputs().getFiles()

            final Configuration providedRuntime = project
                .getConfigurations().getByName(
                    PROVIDED_RUNTIME_CONFIGURATION_NAME)
            return runtimeClasspath.minus(providedRuntime).plus(outputs)
        })

        project.gradle.taskGraph.beforeTask { task ->
            if (task.hasProperty('lazyConfiguration')) {
                if (task.lazyConfiguration instanceof List) {
                    task.lazyConfiguration.each { task.configure it }
                } else {
                    task.configure task.lazyConfiguration
                }
            }
        }

        return fsm
    }

    private void addPublication(Project project, FSM fsm) {
        // remove jar artifact added by java the plugin (see http://issues.gradle.org/browse/GRADLE-687)
        Configuration archivesConfig = project.getConfigurations().getByName(Dependency.ARCHIVES_CONFIGURATION)
        archivesConfig.getArtifacts().clear()

        DefaultArtifactPublicationSet publicationSet = project.getExtensions()
            .getByType(DefaultArtifactPublicationSet.class)

        publicationSet.addCandidate(new ArchivePublishArtifact(fsm))
    }

    private void configureConfigurations(ConfigurationContainer configurationContainer) {
        Configuration fsServerCompileConfiguration = configurationContainer
                .create(FS_SERVER_COMPILE_CONFIGURATION_NAME)
                .setVisible(false)
                .setDescription("Added automatically to module.xml with server scope")

        Configuration fsModuleCompileConfiguration = configurationContainer
                .create(FS_MODULE_COMPILE_CONFIGURATION_NAME)
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

    private IsolationCheck configureIsolationCheckTask(final Project project, final FSM fsmTask) {
        IsolationCheck isolationCheck = project.getTasks().create(ISOLATION_CHECK_TASK_NAME, IsolationCheck.class)
        isolationCheck.setDescription("Verifies the isolation of resources in the FSM.")
        isolationCheck.setGroup(LifecycleBasePlugin.VERIFICATION_GROUP)
        isolationCheck.getInputs().file(fsmTask.getOutputs().getFiles().getSingleFile())

        Task checkTask = project.getTasksByName(JavaBasePlugin.CHECK_TASK_NAME, false).iterator().next()
        checkTask.dependsOn(isolationCheck)

        return isolationCheck
    }

    private void configureJarTask(Project project) {
        final Jar jarTask = (Jar) project.getTasks().getByName(JavaPlugin.JAR_TASK_NAME)
        jarTask.exclude("module.xml")
    }
}
