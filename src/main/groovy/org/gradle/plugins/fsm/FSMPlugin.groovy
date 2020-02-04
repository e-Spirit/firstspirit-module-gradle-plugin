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


import com.github.jk1.license.LicenseReportPlugin
import com.github.jk1.license.render.CsvReportRenderer
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.artifacts.publish.ArchivePublishArtifact
import org.gradle.api.internal.plugins.DefaultArtifactPublicationSet
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.bundling.Jar
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.plugins.fsm.annotations.FSMAnnotationsPlugin
import org.gradle.plugins.fsm.configurations.FSMConfigurationsPlugin
import org.gradle.plugins.fsm.tasks.bundling.FSM
import org.gradle.plugins.fsm.tasks.verification.IsolationCheck

/**
 * <p>A {@link Plugin} with tasks which assemble a (java) application into an FSM (FirstSpirit module) file.</p>
 */
class FSMPlugin implements Plugin<Project> {

    public static final String NAME = "de.espirit.firstspirit-module"
    public static final String FSM_EXTENSION_NAME = "firstSpiritModule"
    public static final String FSM_TASK_NAME = "assembleFSM"
    public static final String ISOLATION_CHECK_TASK_NAME = "checkIsolation"
    public static final String GENERATE_LICENSE_REPORT_TASK_NAME = "generateLicenseReport"

    private Project project

    @Override
    void apply(Project project) {
        this.project = project

        project.getPlugins().apply(JavaPlugin)
        project.getPlugins().apply(FSMConfigurationsPlugin)
        project.getPlugins().apply(FSMAnnotationsPlugin)

        project.getExtensions().create(FSM_EXTENSION_NAME, FSMPluginExtension)

        project.getPlugins().apply(JavaPlugin.class)
        project.getPlugins().apply(LicenseReportPlugin.class)

        FSM fsmTask = configureFsmTask(project)

        IsolationCheck isolationCheck = configureIsolationCheckTask(project, fsmTask)
        Task checkTask = project.getTasksByName(JavaBasePlugin.CHECK_TASK_NAME, false).iterator().next()
        checkTask.dependsOn(isolationCheck)


        configureJarTask(project)
        configureLicenseReport(project)
    }

    void configureLicenseReport(Project project) {
        project.licenseReport {
            // Set output directory for the report data.
            // Defaults to ${project.buildDir}/reports/dependency-license.
            outputDir = "${project.buildDir}/${FSM.LICENSES_DIR_NAME}"

            // Adjust the configurations to fetch dependencies, e.g. for Android projects. Default is 'runtimeClasspath'
            configurations = ['compile']

            renderers = [new CsvReportRenderer()]
        }
    }

    private FSM configureFsmTask(final Project project) {

        FSM fsmTask = project.getTasks().create(FSM_TASK_NAME, FSM.class)
        fsmTask.setDescription("Assembles a fsmTask archive containing the FirstSpirit module.")
        fsmTask.setGroup(BasePlugin.BUILD_GROUP)

        addPublication(project, fsmTask)

        fsmTask.dependsOn({ project.getConvention()
            .getPlugin(JavaPluginConvention.class)
            .getSourceSets()
            .getByName(SourceSet.MAIN_SOURCE_SET_NAME)
            .getRuntimeClasspath()
        })

        fsmTask.dependsOn(project.getTasks().getByName(GENERATE_LICENSE_REPORT_TASK_NAME))

        fsmTask.dependsOn({ JavaPlugin.JAR_TASK_NAME})

        fsmTask.classpath({
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
                    FSMConfigurationsPlugin.PROVIDED_RUNTIME_CONFIGURATION_NAME)
            return (runtimeClasspath - providedRuntime) + outputs
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

        return fsmTask
    }

    private void addPublication(Project project, FSM fsm) {
        // remove jar artifact added by java the plugin (see http://issues.gradle.org/browse/GRADLE-687)
        Configuration archivesConfig = project.getConfigurations().getByName(Dependency.ARCHIVES_CONFIGURATION)
        archivesConfig.getArtifacts().clear()

        DefaultArtifactPublicationSet publicationSet = project.getExtensions()
            .getByType(DefaultArtifactPublicationSet.class)

        publicationSet.addCandidate(new ArchivePublishArtifact(fsm))
    }

    private IsolationCheck configureIsolationCheckTask(final Project project, final FSM fsmTask) {
        IsolationCheck isolationCheck = project.getTasks().create(ISOLATION_CHECK_TASK_NAME, IsolationCheck.class)
        isolationCheck.setDescription("Verifies the isolation of resources in the FSM.")
        isolationCheck.setGroup(LifecycleBasePlugin.VERIFICATION_GROUP)
        isolationCheck.getInputs().file(fsmTask.getOutputs().getFiles().getSingleFile())
        isolationCheck.dependsOn(fsmTask)

        Task checkTask = project.getTasksByName(JavaBasePlugin.CHECK_TASK_NAME, false).iterator().next()
        checkTask.dependsOn(isolationCheck)

        return isolationCheck
    }

    private void configureJarTask(Project project) {
        final Jar jarTask = (Jar) project.getTasks().getByName(JavaPlugin.JAR_TASK_NAME)
        jarTask.exclude("module.xml")
    }
}
