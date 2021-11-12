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
import org.gradle.api.tasks.TaskProvider
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

        project.getExtensions().create(FSM_EXTENSION_NAME, FSMPluginExtension, project)

        project.getPlugins().apply(JavaPlugin.class)
        project.getPlugins().apply(LicenseReportPlugin.class)

        FSM fsmTask = configureFsmTask(project)

        TaskProvider<IsolationCheck> isolationCheck = configureIsolationCheckTask(project, fsmTask)
        Task checkTask = project.getTasksByName(JavaBasePlugin.CHECK_TASK_NAME, false).iterator().next()
        checkTask.dependsOn(isolationCheck)

        configureJarTask(project)
        configureLicenseReport(project)
        configureManifest(project)
    }

    void configureLicenseReport(Project project) {
        project.licenseReport {
            // Set output directory for the report data.
            outputDir = "${project.buildDir}/${FSM.LICENSES_DIR_NAME}"
            configurations = [JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME]
            renderers = [new CsvReportRenderer()]
        }
    }

    private static FSM configureFsmTask(final Project project) {

        FSM fsmTask = project.getTasks().create(FSM_TASK_NAME, FSM.class)
        fsmTask.setDescription("Assembles an fsmTask archive containing the FirstSpirit module.")
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
        project.tasks.getByName("assemble").dependsOn(fsmTask)

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

            return runtimeClasspath + outputs
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

    private static void addPublication(Project project, FSM fsm) {
        // remove jar artifact added by java the plugin (see http://issues.gradle.org/browse/GRADLE-687)
        Configuration archivesConfig = project.getConfigurations().getByName(Dependency.ARCHIVES_CONFIGURATION)
        archivesConfig.getArtifacts().clear()

        DefaultArtifactPublicationSet publicationSet = project.getExtensions()
            .getByType(DefaultArtifactPublicationSet.class)

        publicationSet.addCandidate(new ArchivePublishArtifact(fsm))
    }

    private static TaskProvider<IsolationCheck> configureIsolationCheckTask(final Project project, final FSM fsmTask) {
        def isolationCheck = project.getTasks().register(ISOLATION_CHECK_TASK_NAME, IsolationCheck.class)
        isolationCheck.configure {
            it.setDescription("Verifies the isolation of resources in the FSM.")
            it.setGroup(LifecycleBasePlugin.VERIFICATION_GROUP)
            it.getInputs().file(fsmTask.getOutputs().files.first())
            it.dependsOn(fsmTask)
        }

        Task checkTask = project.getTasksByName(JavaBasePlugin.CHECK_TASK_NAME, false).iterator().next()
        checkTask.dependsOn(isolationCheck)

        return isolationCheck
    }

    private static void configureJarTask(Project project) {
        final Jar jarTask = (Jar) project.getTasks().getByName(JavaPlugin.JAR_TASK_NAME)
        jarTask.exclude("module.xml")
    }

    private static void configureManifest(Project project) {
        // Configure each JAR's manifest in the project. This also includes the .fsm file
        // We cannot configure the tasks directly, because the project is not evaluated yet
        // We also cannot use project.afterEvaluate {...}, because the unit tests apply the plugin after
        // the project has already been evaluated, causing Gradle to throw an exception
        // Because of this, we use the task execution graph to find all Jar Tasks and configure them

        project.gradle.taskGraph.whenReady { taskGraph ->
            project.logger.info("Configuring JAR manifests...")
            taskGraph.allTasks.findAll { it instanceof Jar }.each { task ->
                def buildJdk = "${System.properties['java.runtime.version']} (${System.properties['java.vendor']})"
                project.logger.info("Configuring task of project ${task.project.name}, task name: ${task.path}")
                if (task instanceof FSM) {
                    def fsmTask = task as FSM
                    addManifestAttribute(fsmTask, "Created-By", "FirstSpirit Module Gradle Plugin ${getPluginVersion()}")
                    addManifestAttribute(fsmTask, "Build-Jdk", buildJdk)
                    addManifestAttribute(fsmTask, "Build-Tool", "Gradle $project.gradle.gradleVersion")
                } else {
                    def jarTask = task as Jar
                    addManifestAttribute(jarTask, "Build-Jdk", buildJdk)
                    addManifestAttribute(jarTask, "Created-By", "Gradle $project.gradle.gradleVersion")
                }
            }
        }
    }

    /**
     * Gets the version of the FSM gradle plugin from {@code versions.properties}.
     * It would also be possible to use {@code FSMPlugin.package.implementationVersion},
     * however, this relies on the implementation version being set in the plugin jar.
     * This is not the case for unit tests starting a gradle build.
     *
     * @return The plugin version
     */
    private static String getPluginVersion() {
        FSMPlugin.getResourceAsStream("/versions.properties").withCloseable {
            def properties = new Properties()
            properties.load(it)
            return properties["version"]
        }
    }

    /**
     * Sets a parameter for a {@link Jar} task's manifest, if it wasn't already set before.
     * If the parameter was already set, doesn't do anything.
     *
     * @param jarTask The {@link Task} to configure
     * @param name    The name of the attribute to configure
     * @param value   The value of the attribute
     */
    private static void addManifestAttribute(Jar jarTask, String name, Object value) {
        jarTask.manifest.attributes.putIfAbsent(name, value)
    }

}