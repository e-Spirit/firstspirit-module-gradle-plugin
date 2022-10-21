package org.gradle.plugins.fsm

import com.github.jk1.license.LicenseReportExtension
import com.github.jk1.license.LicenseReportPlugin
import com.github.jk1.license.render.CsvReportRenderer
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Dependency
import org.gradle.api.internal.artifacts.publish.ArchivePublishArtifact
import org.gradle.api.internal.plugins.DefaultArtifactPublicationSet
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskProvider
import org.gradle.jvm.tasks.Jar
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.plugins.fsm.annotations.FSMAnnotationsPlugin
import org.gradle.plugins.fsm.configurations.FSMConfigurationsPlugin
import org.gradle.plugins.fsm.tasks.bundling.FSM
import org.gradle.plugins.fsm.tasks.verification.IsolationCheck
import org.gradle.plugins.fsm.tasks.verification.ValidateDescriptor
import java.util.*

class FSMPlugin: Plugin<Project> {

    override fun apply(project: Project) {
        project.plugins.apply(JavaPlugin::class.java)
        project.plugins.apply(FSMConfigurationsPlugin::class.java)
        project.plugins.apply(FSMAnnotationsPlugin::class.java)

        project.extensions.create(FSM_EXTENSION_NAME, FSMPluginExtension::class.java, project)

        project.plugins.apply(LicenseReportPlugin::class.java)

        val configurationTask = configureConfigurationTask(project)
        val validateTask = project.tasks.register(VALIDATE_DESCRIPTOR_TASK_NAME, ValidateDescriptor::class.java)
        val fsmTask = configureFsmTask(project, configurationTask, validateTask)

        configureValidateTask(validateTask, fsmTask)
        val isolationCheck = configureIsolationCheckTask(project, fsmTask)
        val checkTask = project.tasks.getByName(JavaBasePlugin.CHECK_TASK_NAME)
        checkTask.dependsOn(validateTask, isolationCheck)

        configureJarTask(project)
        configureLicenseReport(project)
        configureManifest(project)
    }

    private fun configureConfigurationTask(project: Project): Task {
        val configurationTask = project.tasks.create(CONFIGURE_FSM_TASK_NAME)
        configurationTask.dependsOn(project.tasks.getByName(GENERATE_LICENSE_REPORT_TASK_NAME))
        configurationTask.dependsOn(JavaPlugin.JAR_TASK_NAME)

        @Suppress("ObjectLiteralToLambda") // Lambdas cannot be used as Gradle task inputs
        val action = object : Action<Task> {
            override fun execute(t: Task) {
                (project.tasks.getByName(FSM_TASK_NAME) as FSM).lazyConfiguration()
            }
        }

        configurationTask.doLast(action)

        return configurationTask
    }

    private fun configureFsmTask(
        project: Project,
        configurationTask: Task,
        validateTask: TaskProvider<ValidateDescriptor>
    ): FSM {
        val fsmTask = project.tasks.create(FSM_TASK_NAME, FSM::class.java)
        fsmTask.description = "Assembles an fsmTask archive containing the FirstSpirit module."
        fsmTask.group = BasePlugin.BUILD_GROUP

        addPublication(project, fsmTask)

        val javaPlugin = project.extensions.getByType(JavaPluginExtension::class.java)
        val runtimeClasspath = javaPlugin.sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME).runtimeClasspath

        fsmTask.dependsOn(runtimeClasspath)
        fsmTask.dependsOn(project.tasks.getByName(GENERATE_LICENSE_REPORT_TASK_NAME))
        fsmTask.dependsOn(JavaPlugin.JAR_TASK_NAME)
        fsmTask.dependsOn(configurationTask)
        project.tasks.getByName("assemble").dependsOn(fsmTask)

        // Validate FSM immediately
        fsmTask.finalizedBy(validateTask)

        val outputs = project.tasks.getByName(JavaPlugin.JAR_TASK_NAME).outputs.files
        fsmTask.addToClasspath(runtimeClasspath + outputs)

        return fsmTask
    }

    private fun addPublication(project: Project, fsm: FSM) {
        // remove jar artifact added by java the plugin (see http://issues.gradle.org/browse/GRADLE-687)
        val archivesConfig = project.configurations.getByName(Dependency.ARCHIVES_CONFIGURATION)
        archivesConfig.artifacts.clear()

        val publicationSet = project.extensions.getByType(DefaultArtifactPublicationSet::class.java)
        publicationSet.addCandidate(ArchivePublishArtifact(fsm))
    }

    private fun configureValidateTask(validateTask: TaskProvider<ValidateDescriptor>, fsmTask: FSM) {
        validateTask.configure {
            it.description = "Validates the module descriptor."
            it.group = LifecycleBasePlugin.VERIFICATION_GROUP
            it.inputs.file(fsmTask.outputs.files.singleFile)
            it.dependsOn(fsmTask)
        }
    }

    private fun configureIsolationCheckTask(project: Project, fsmTask: FSM): TaskProvider<IsolationCheck> {
        val isolationCheck = project.tasks.register(ISOLATION_CHECK_TASK_NAME, IsolationCheck::class.java) {
            it.description = "Verifies the isolation of resources in the FSM."
            it.group = LifecycleBasePlugin.VERIFICATION_GROUP
            it.inputs.file(fsmTask.outputs.files.singleFile)
            it.dependsOn(fsmTask)
        }

        return isolationCheck
    }

    private fun configureJarTask(project: Project) {
        val jarTask = project.tasks.getByName(JavaPlugin.JAR_TASK_NAME) as Jar
        jarTask.exclude("module-isolated.xml")
    }

    private fun configureLicenseReport(project: Project) {
        with(project.extensions.getByType(LicenseReportExtension::class.java)) {
            // Set output directory for the report data.
            outputDir = "${project.buildDir}/${FSM.LICENSES_DIR_NAME}"
            configurations = arrayOf(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME)
            renderers = arrayOf(CsvReportRenderer())
        }
    }

    private fun configureManifest(project: Project) {
        // Configure each JAR's manifest in the project. This also includes the .fsm file
        // We cannot configure the tasks directly, because the project is not evaluated yet
        // We also cannot use project.afterEvaluate {...}, because the unit tests apply the plugin after
        // the project has already been evaluated, causing Gradle to throw an exception
        // Because of this, we use the task execution graph to find all Jar Tasks and configure them

        project.gradle.taskGraph.whenReady { taskGraph ->
            project.logger.info("Configuring JAR manifests...")
            taskGraph.allTasks.filterIsInstance<Jar>().forEach { task ->
                val buildJdk = "${System.getProperty("java.runtime.version")} (${System.getProperty("java.vendor")})"
                project.logger.info("Configuring task of project ${task.project.name}, task name: ${task.path}")
                if (task is FSM) {
                    task.addManifestAttribute("Created-By", "FirstSpirit Module Gradle Plugin ${getPluginVersion()}")
                    task.addManifestAttribute("Build-Jdk", buildJdk)
                    task.addManifestAttribute("Build-Tool", "Gradle ${project.gradle.gradleVersion}")
                } else {
                    task.addManifestAttribute("Build-Jdk", buildJdk)
                    task.addManifestAttribute("Created-By", "Gradle ${project.gradle.gradleVersion}")
                }
            }
        }
    }

    /**
     * Gets the version of the FSM gradle plugin from `versions.properties`.
     * It would also be possible to use the [Package.getImplementationVersion] property of FSMPlugin,
     * however, this relies on the implementation version being set in the plugin jar.
     * This is not the case for unit tests starting a gradle build.
     *
     * @return The plugin version
     */
    private fun getPluginVersion(): String {
        FSMPlugin::class.java.getResourceAsStream("/versions.properties").use {
            val properties = Properties()
            properties.load(it)
            return properties.getProperty("version")
        }
    }


    companion object {
        const val NAME = "de.espirit.firstspirit-module"
        const val FSM_EXTENSION_NAME = "firstSpiritModule"
        const val FSM_TASK_NAME = "assembleFSM"
        const val CONFIGURE_FSM_TASK_NAME = "configureAssembleFSM"
        const val VALIDATE_DESCRIPTOR_TASK_NAME = "validateDescriptor"
        const val ISOLATION_CHECK_TASK_NAME = "checkIsolation"
        const val GENERATE_LICENSE_REPORT_TASK_NAME = "generateLicenseReport"
    }

}