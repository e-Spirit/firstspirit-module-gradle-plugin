package org.gradle.plugins.fsm.tasks.bundling

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.FileCollection
import org.gradle.api.file.ProjectLayout
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.jvm.tasks.Jar
import org.gradle.plugins.fsm.FSMPlugin.Companion.WEBAPPS_CONFIGURATION_NAME
import org.gradle.plugins.fsm.FSMPluginExtension
import org.gradle.plugins.fsm.configurations.FSMConfigurationsPlugin
import org.gradle.plugins.fsm.configurations.FSMConfigurationsPlugin.Companion.FS_MODULE_COMPILE_CONFIGURATION_NAME
import org.gradle.plugins.fsm.configurations.FSMConfigurationsPlugin.Companion.FS_SERVER_COMPILE_CONFIGURATION_NAME
import org.gradle.plugins.fsm.configurations.FSMConfigurationsPlugin.Companion.FS_WEB_COMPILE_CONFIGURATION_NAME
import org.gradle.plugins.fsm.dependencyProject
import org.gradle.plugins.fsm.descriptor.LibraryComponents
import org.gradle.plugins.fsm.descriptor.ModuleDescriptor
import org.gradle.plugins.fsm.descriptor.moduleScopeDependencies
import org.gradle.plugins.fsm.descriptor.serverScopeDependencies
import org.gradle.plugins.fsm.projectDependencies
import org.jetbrains.annotations.TestOnly
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import javax.inject.Inject

abstract class FSM: Jar() {

    private val pluginExtension: FSMPluginExtension

    /**
     * Contains all resource file paths of all fsm-resources folders that are duplicates. Used for duplicate warning
     */
    @Internal("Visible for tests")
    val fsmResourceFileToProject = mutableMapOf<File, MutableSet<Project>>()

    @get:Inject
    abstract val layout: ProjectLayout

    init {
        archiveExtension.set(FSM_EXTENSION)
        destinationDirectory.set(project.layout.buildDirectory.dir("fsm"))
        pluginExtension = project.extensions.getByType(FSMPluginExtension::class.java)
        duplicatesStrategy = DuplicatesStrategy.WARN

        pluginExtension.moduleDirName?.let { inputs.dir(layout.projectDirectory.dir(it)) }

        configureProjectDependencies()
        into("lib") {
            from(project.provider { project.serverScopeDependencies().map { it.file } })
            from(project.provider { project.moduleScopeDependencies().map { it.file } })
            from(project.provider {
                project.configurations.getByName(FS_WEB_COMPILE_CONFIGURATION_NAME).resolve()
            })
            from(project.tasks.named(JavaPlugin.JAR_TASK_NAME))
            from(project.provider {
                project.configurations.getByName(WEBAPPS_CONFIGURATION_NAME).resolve()
            })
            from(project.provider {
                pluginExtension.getWebApps().values
                    .mapNotNull { it.tasks.findByName(JavaPlugin.JAR_TASK_NAME) }
                    .map { it.outputs.files.singleFile }
            })
            from(project.provider {
                pluginExtension.libraries
                    .asSequence().mapNotNull { it.configuration }
                    .flatMap { LibraryComponents.getResolvedDependencies(project, it) }
                    .map { it.file }
                    .toList()
            })
        }

        // include licenses report
        into("META-INF") {
            from(project.layout.buildDirectory.dir(LICENSES_DIR_NAME)) {
                include("licenses.csv")
            }
        }

        // include license texts
        into("/") {
            from(project.layout.buildDirectory.dir(LICENSES_DIR_NAME)) {
                includeEmptyDirs = false
                eachFile {
                    // Set output path
                    // - Remove "META-INF/" directory from collected licenses
                    // - Add .txt if the file doesn't have an extension
                    path = "META-INF/licenses/" + path.replace("META-INF/", "/")
                    if (!name.contains(".")) {
                        path += ".txt"
                    }
                }
                exclude("licenses.csv")
                exclude("index.html")
            }
        }

        into("/") {
            from(fsmResourcesFolder(project))

            // Merge fsm-resources folders of projects added as dependency
            from(project.provider {
                listOf(
                    FS_MODULE_COMPILE_CONFIGURATION_NAME, FS_SERVER_COMPILE_CONFIGURATION_NAME,
                    FS_WEB_COMPILE_CONFIGURATION_NAME, WEBAPPS_CONFIGURATION_NAME)
                    .flatMap { project.configurations.getByName(it).projectDependencies(project) }
                    .distinct()
                    .map { fsmResourcesFolder(it) }
            })
        }

        doFirst {
            // Warn about duplicate fsm-resources files
            fsmResourceFileToProject.filter { it.value.size > 1 }.forEach { (file, projects) ->
                logger.warn("File {} found in multiple projects: {}", file, projects)
            }
        }
    }

    /**
     * Jars of project dependencies need to exist before the FSM is built
     */
    private fun configureProjectDependencies() {
        dependsOn(project.provider {
            project.configurations.getByName(FS_MODULE_COMPILE_CONFIGURATION_NAME)
                .allDependencies
                .filterIsInstance<ProjectDependency>()
                .map { it.dependencyProject(project) }
                .map { it.tasks.named(JavaPlugin.JAR_TASK_NAME) }
        })

        dependsOn(project.provider {
            project.configurations.getByName(FS_SERVER_COMPILE_CONFIGURATION_NAME)
                .allDependencies
                .filterIsInstance<ProjectDependency>()
                .map { it.dependencyProject(project) }
                .map { it.tasks.named(JavaPlugin.JAR_TASK_NAME) }
        })
    }

    private fun fsmResourcesFolder(dep: Project): Provider<FileCollection> {
        return project.provider {
            val fsmResourcesPath = dep.projectDir.absolutePath + '/' + FSM_RESOURCES_PATH
            val fsmResourcesFolder = File(fsmResourcesPath)
            if (fsmResourcesFolder.exists()) {
                logger.info("Adding folder $fsmResourcesPath from project ${dep.name} to fsm")
                // Record files to warn about duplicates later
                fsmResourcesFolder.walk().filter { it.isFile }.forEach { file ->
                    val relativePath = file.relativeTo(fsmResourcesFolder)
                    fsmResourceFileToProject.getOrPut(relativePath) { mutableSetOf() }.add(dep)
                }
                project.files(fsmResourcesPath)
            } else {
                logger.debug("Not adding folder $fsmResourcesPath from project ${dep.name} to fsm, because it doesn't exist.")
                project.files()
            }
        }
    }


    @TaskAction
    override fun copy() {
        super.copy()

        logger.info("Generating module.xml files")
        val archive = archiveFile.get().asFile
        logger.info("Found archive ${archive.path}")
        FileSystems.newFileSystem(archive.toPath(), javaClass.classLoader).use { fs ->
            writeModuleDescriptorToZipFile(fs, getUnfilteredModuleXml())
        }
    }

    @Suppress("CanConvertToMultiDollarString") // Not supported in Kotlin shipped with Gradle 8.11
    private fun writeModuleDescriptorToZipFile(fs: FileSystem, unfilteredModuleXml: String?) {
        val filteredModuleXml: String

        val moduleDescriptor = ModuleDescriptor(project)

        if (unfilteredModuleXml != null) {
            // Replace values in XML provided by user
            filteredModuleXml = unfilteredModuleXml
                .replace("\$name", pluginExtension.moduleName ?: project.name)
                .replace("\$displayName", pluginExtension.displayName ?: project.name)
                .replace("\$version", project.version.toString())
                .replace("\$minimalFirstSpiritVersion", pluginExtension.minimalFirstSpiritVersion ?: "")
                .replace("\$description", project.description ?: project.name)
                .replace("\$vendor", pluginExtension.vendor ?: "")
                .replace("\$artifact", project.tasks.named("jar", Jar::class.java).get()
                    .archiveFileName.getOrElse("unknown-archiveFileName"))
                .replace("\$class", moduleDescriptor.moduleClass.toString())
                .replace("\$dependencies", moduleDescriptor.fsmDependencies())
                .replace("\$resources", moduleDescriptor.resources.innerResourcesToString())
                .replace("\$components", moduleDescriptor.components.innerComponentsToString())
                .replace("\$licensesFile", "META-INF/licenses.csv")
        } else {
            // Create descriptor from scratch
            filteredModuleXml = moduleDescriptor.toString()
        }

        val moduleXmlFile = fs.getPath("/META-INF/module-isolated.xml")
        Files.newBufferedWriter(moduleXmlFile, StandardCharsets.UTF_8, StandardOpenOption.CREATE).use {
            it.write(moduleDescriptor.reformat(filteredModuleXml))
        }
    }

    private fun getUnfilteredModuleXml(): String? {
        val moduleDirName = pluginExtension.moduleDirName ?: return null

        val moduleDirPath = layout.projectDirectory.dir(moduleDirName).asFile
        if (!moduleDirPath.isDirectory) {
            throw GradleException("moduleDirName '$moduleDirPath' is not a directory!")
        }

        val moduleXmlFileName = "module-isolated.xml"
        val moduleXml = moduleDirPath.resolve(moduleXmlFileName)

        return if (moduleXml.exists()) {
            moduleXml.readText()
        } else {
            throw GradleException("No $moduleXmlFileName found in moduleDir $moduleDirPath")
        }
    }


    /**
     * Helper method for executing Unit tests
     */
    @TestOnly
    fun execute() {
        Files.createDirectories(archiveFile.get().asFile.parentFile.toPath())
        Files.createFile(archiveFile.get().asFile.toPath())
        copy()
    }


    companion object {
        const val FSM_EXTENSION = "fsm"
        const val FSM_RESOURCES_PATH = FSMConfigurationsPlugin.FSM_RESOURCES_PATH

        /**
         * Output dir name for license reports of license report plugin.
         */
        const val LICENSES_DIR_NAME = "licenses"
    }

}