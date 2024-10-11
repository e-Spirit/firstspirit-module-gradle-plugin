package org.gradle.plugins.fsm

import com.github.jk1.license.LicenseReportExtension
import com.github.jk1.license.LicenseReportPlugin
import com.github.jk1.license.render.CsvReportRenderer
import com.github.jk1.license.task.ReportTask
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Dependency
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaBasePlugin.VERIFICATION_GROUP
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.tasks.Jar
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.plugins.fsm.annotations.FSMAnnotationsPlugin
import org.gradle.plugins.fsm.configurations.FSMConfigurationsPlugin
import org.gradle.plugins.fsm.configurations.FSMConfigurationsPlugin.Companion.FS_CONFIGURATIONS
import org.gradle.plugins.fsm.tasks.bundling.FSM
import org.gradle.plugins.fsm.tasks.verification.IsolationCheck
import org.gradle.plugins.fsm.tasks.verification.ValidateDescriptor
import java.util.*

class FSMPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        project.plugins.apply(JavaPlugin::class.java)
        project.plugins.apply(FSMConfigurationsPlugin::class.java)
        project.plugins.apply(FSMAnnotationsPlugin::class.java)
        registerWebappsConfiguration(project)

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
        configureComplianceCheckTask(project)
    }

    private fun registerWebappsConfiguration(project: Project) {
        val webAppsConfiguration = project.configurations.create(WEBAPPS_CONFIGURATION_NAME)
        webAppsConfiguration.description = "Combined Runtime Classpath of all Web-Apps registered with the 'webAppComponent' method."
        val implementation = project.configurations.getByName(JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME)
        implementation.extendsFrom(webAppsConfiguration)
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

        removeDefaultJarArtifactFromArchives(project)

        fsmTask.dependsOn(project.tasks.getByName(GENERATE_LICENSE_REPORT_TASK_NAME))
        fsmTask.dependsOn(JavaPlugin.JAR_TASK_NAME)
        fsmTask.dependsOn(configurationTask)
        project.tasks.getByName("assemble").dependsOn(fsmTask)

        // Validate FSM immediately
        fsmTask.finalizedBy(validateTask)

        return fsmTask
    }

    private fun removeDefaultJarArtifactFromArchives(project: Project) {
        // remove jar artifact added by java the plugin (see http://issues.gradle.org/browse/GRADLE-687)
        val archivesConfig = project.configurations.getByName(Dependency.ARCHIVES_CONFIGURATION)
        archivesConfig.artifacts.clear()
    }

    private fun configureValidateTask(validateTask: TaskProvider<ValidateDescriptor>, fsmTask: FSM) {
        validateTask.configure {
            description = "Validates the module descriptor."
            group = LifecycleBasePlugin.VERIFICATION_GROUP
            inputs.file(fsmTask.outputs.files.singleFile)
            dependsOn(fsmTask)
        }
    }

    private fun configureIsolationCheckTask(project: Project, fsmTask: FSM): TaskProvider<IsolationCheck> {
        val isolationCheck = project.tasks.register(ISOLATION_CHECK_TASK_NAME, IsolationCheck::class.java) {
            description = "Verifies the isolation of resources in the FSM."
            group = LifecycleBasePlugin.VERIFICATION_GROUP
            inputs.file(fsmTask.outputs.files.singleFile)
            dependsOn(fsmTask)
        }

        return isolationCheck
    }

    private fun configureJarTask(project: Project) {
        val jarTask = project.tasks.getByName(JavaPlugin.JAR_TASK_NAME) as Jar
        jarTask.exclude("module-isolated.xml")
    }

    /**
     * Configures the generation of the FSM license report. A CSV license file is added into the module's META-INF/
     * directory. Additionally, we attempt to include all license texts into a subfolder.
     * All licenses of third-party libs included in the FSM should be included, i.e.,
     *
     * - Libraries added with `fsModuleCompile`, `fsServerCompile` or `fsWebCompile`
     * - Libraries included from web apps added with the `webAppComponent` method in the `firstSpiritModule` block
     * - Libraries included with the `libraries` method in the `firstSpiritModule` block
     */
    private fun configureLicenseReport(project: Project) {
        val licenseReportTask = project.tasks.withType(ReportTask::class.java).first()

        // Library names and web apps are only available after the configuration phase
        val preparationAction = { _: Task ->
            with(project.extensions.getByType(LicenseReportExtension::class.java)) {
                configurations = getLicenseReportConfigurations(project).toTypedArray()
            }
        }
        licenseReportTask.doFirst(preparationAction)

        // License Report Plugin escapes quotes not in accordance with RFC 4180. A " should be "", but instead becomes \"
        // Since there is no way to override this in the plugin configuration, we do a textual replace of \" to "".
        val repairQuotes = { task: Task ->
            val licenseFile = task.outputs.files.singleFile.resolve("licenses.csv")
            licenseFile.writeText(licenseFile.readText().replace("\\\"", "\"\""))
        }
        licenseReportTask.doLast(repairQuotes)

        with(project.extensions.getByType(LicenseReportExtension::class.java)) {
            // Set output directory for the report data.
            outputDir = project.layout.buildDirectory.dir(FSM.LICENSES_DIR_NAME).get().asFile.absolutePath
            configurations = arrayOf(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME) // Replaced with the correct configurations later, see preparationAction above
            renderers = arrayOf(CsvReportRenderer())
        }
    }

    private fun getLicenseReportConfigurations(project: Project): Set<String> {
        val fsmPluginExtension = project.extensions.getByType(FSMPluginExtension::class.java)
        return FS_CONFIGURATIONS + // fsModuleCompile, fsServerCompile, fsWebCompile
                fsmPluginExtension.libraries.asSequence().mapNotNull { it.configuration?.name } + // library components
                WEBAPPS_CONFIGURATION_NAME // webapps declared with the 'webAppComponent' method
    }

    private fun configureManifest(project: Project) {
        // Configure each JAR's manifest in the project. This also includes the .fsm file
        // We cannot configure the tasks directly, because the project is not evaluated yet
        // We also cannot use project.afterEvaluate {...}, because the unit tests apply the plugin after
        // the project has already been evaluated, causing Gradle to throw an exception
        // Because of this, we use the task execution graph to find all Jar Tasks and configure them

        project.gradle.taskGraph.whenReady {
            project.logger.info("Configuring JAR manifests...")
            allTasks.filterIsInstance<Jar>().forEach { task ->
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
     * Gets the version of the FSM gradle plugin from `fsm-gradle-plugin/versions.properties`.
     * It would also be possible to use the [Package.getImplementationVersion] property of FSMPlugin,
     * however, this relies on the implementation version being set in the plugin jar.
     * This is not the case for unit tests starting a gradle build.
     *
     * @return The plugin version
     */
    private fun getPluginVersion(): String {
        FSMPlugin::class.java.getResourceAsStream(VERSIONS_PROPERTIES_FILE).use {
            val properties = Properties()
            properties.load(it)
            return properties.getProperty("version")
        }
    }


    private fun configureComplianceCheckTask(project: Project) {
        val fsVersion = project.findProperty("complianceCheckFsVersion")
            ?: project.findProperty("firstSpirit.version")
            ?: "5.2.241212"
        project.logger.info("Compliance Check will use FirstSpirit version $fsVersion")

        val configuration = project.configurations.create("complianceCheck")
        project.dependencies.let {
            it.add(configuration.name, "com.tngtech.archunit:archunit-junit5:1.3.0")
            it.add(configuration.name, "org.jetbrains:annotations:26.0.1")
            it.add(configuration.name, "de.espirit.firstspirit:fs-isolated-runtime:$fsVersion")
        }

        val classesDir = project.layout.buildDirectory.dir("classes/checkCompliance").get().asFile

        val copyComplianceCheckClassesTask = project.tasks.register("copyComplianceCheckClasses")
        copyComplianceCheckClassesTask.configure {
            doLast {
                val classesToCopy = listOf(
                    "com/crownpeak/plugins/fsm/compliance/ComplianceCheck.class",
                    "com/crownpeak/plugins/fsm/compliance/ModLocationProvider.class",
                    "com/crownpeak/plugins/fsm/compliance/ModLocationProvider$1.class"
                )

                classesToCopy.forEach { classToCopy ->
                    val classStream = FSMPlugin::class.java.classLoader.getResourceAsStream(classToCopy)
                        ?: error("Compliance test case '$classToCopy' not found")
                    val classFile = classesDir.resolve(classToCopy)
                    classFile.parentFile.mkdirs()
                    classStream.use { source ->
                        classFile.outputStream().use { target ->
                            source.copyTo(target)
                        }
                    }
                }
            }
        }


        val complianceCheckTask = project.tasks.register(COMPLIANCE_CHECK_TASK_NAME, Test::class.java)
        val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)
        complianceCheckTask.configure {
            group = VERIFICATION_GROUP
            description = "Validates if the module is compliant to Crownpeak implementation standards," +
                    " i.e. if non-API methods are used"
            dependsOn(copyComplianceCheckClassesTask)
            useJUnitPlatform()
            include("com/crownpeak/**")
            testClassesDirs = project.files(classesDir)
            classpath = sourceSets.getByName("main").runtimeClasspath + configuration + project.files(classesDir)
        }

    }


    companion object {
        const val NAME = "de.espirit.firstspirit-module"
        const val FSM_EXTENSION_NAME = "firstSpiritModule"
        const val FSM_TASK_NAME = "assembleFSM"
        const val CONFIGURE_FSM_TASK_NAME = "configureAssembleFSM"
        const val VALIDATE_DESCRIPTOR_TASK_NAME = "validateDescriptor"
        const val ISOLATION_CHECK_TASK_NAME = "checkIsolation"
        const val COMPLIANCE_CHECK_TASK_NAME = "checkCompliance"
        const val GENERATE_LICENSE_REPORT_TASK_NAME = "generateLicenseReport"
        const val WEBAPPS_CONFIGURATION_NAME = "fsmWebappsRuntime"
        const val VERSIONS_PROPERTIES_FILE = "/fsm-gradle-plugin/versions.properties"
    }

}