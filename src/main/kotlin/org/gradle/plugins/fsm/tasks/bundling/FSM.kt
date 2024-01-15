package org.gradle.plugins.fsm.tasks.bundling

import org.gradle.api.Project
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.FileCollection
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.jvm.tasks.Jar
import org.gradle.plugins.fsm.FSMPluginExtension
import org.gradle.plugins.fsm.compileDependencies
import org.gradle.plugins.fsm.configurations.FSMConfigurationsPlugin
import org.gradle.plugins.fsm.configurations.FSMConfigurationsPlugin.Companion.FS_WEB_COMPILE_CONFIGURATION_NAME
import org.gradle.plugins.fsm.descriptor.LibraryComponents
import org.gradle.plugins.fsm.descriptor.ModuleDescriptor
import org.gradle.plugins.fsm.descriptor.moduleScopeDependencies
import org.gradle.plugins.fsm.descriptor.serverScopeDependencies
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.util.zip.ZipFile

abstract class FSM: Jar() {

    private val pluginExtension: FSMPluginExtension

    /**
     * Contains all resource file paths added with an fsm-resources folder.
     * Used for duplicate checking
     */
    private val fsmResourceFiles = mutableSetOf<File>()

    /**
     * Contains all resource file paths of all fsm-resources folders that are duplicates. Used for duplicate warning
     */
    @Internal("Visible for tests")
    val duplicateFsmResourceFiles = mutableSetOf<File>()

    /**
     * The fsm runtime classpath. All libraries in this classpath will be copied to 'fsm/lib' folder
     */
    @get:InputFiles
    var classpath: FileCollection = project.files()

    init {
        archiveExtension.set(FSM_EXTENSION)
        destinationDirectory.set(project.layout.buildDirectory.dir("fsm"))
        pluginExtension = project.extensions.getByType(FSMPluginExtension::class.java)
        duplicatesStrategy = DuplicatesStrategy.WARN
    }

    fun lazyConfiguration() {
        into("lib") { lib ->
            project.serverScopeDependencies().forEach { lib.from(it.file) }
            project.moduleScopeDependencies().forEach { lib.from(it.file) }
            project.configurations.getByName(FS_WEB_COMPILE_CONFIGURATION_NAME).resolve().forEach { lib.from(it) }
            lib.from(project.tasks.named(JavaPlugin.JAR_TASK_NAME))
            pluginExtension.getWebApps().values
                .map { project -> project.configurations.getByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME) }
                .forEach { lib.from(it) }
            pluginExtension.getWebApps().values
                .map { project -> project.tasks.named(JavaPlugin.JAR_TASK_NAME) }
                .filter { task -> task.isPresent }
                .map { task -> task.map { it.outputs.files.singleFile } }
                .forEach { jarFile -> lib.from(jarFile) }
            pluginExtension.libraries
                .asSequence().map { it.configuration }.filterNotNull()
                .flatMap { LibraryComponents.getResolvedDependencies(project, it) }
                .forEach { lib.from(it.file) }
        }

        // include licenses report
        into("META-INF") {
            it.from(project.layout.buildDirectory.dir(LICENSES_DIR_NAME)) { from ->
                from.include("licenses.csv")
            }
        }

        // include license texts
        into("/") { into ->
            with(into.from(project.layout.buildDirectory.dir(LICENSES_DIR_NAME))) {
                includeEmptyDirs = false
                eachFile { file ->
                    // Set output path
                    // - Remove "META-INF/" directory from collected licenses
                    // - Add .txt if the file doesn't have an extension
                    file.path = "META-INF/licenses/" + file.path.replace("META-INF/", "/")
                    if (!file.name.contains(".")) {
                        file.path += ".txt"
                    }
                }
                exclude("licenses.csv")
                exclude("index.html")
            }
        }

        // Merge fsm-resources folders of projects added as dependency
        project.compileDependencies().forEach {
            copyResourceFolderToFsm(it)
        }

        with(metaInf) {
            if (pluginExtension.moduleDirName != null) {
                // include module-isolated.xml file
                val moduleDirPath = trimPathToDirectory(pluginExtension.moduleDirName)

                val deprecatedModuleXmlFileName = "module.xml"
                val isolatedModuleXmlFileName = "module-isolated.xml"
                val deprecatedModuleXml = project.file("$moduleDirPath/$deprecatedModuleXmlFileName")
                val moduleIsolatedXml = project.file("$moduleDirPath/$isolatedModuleXmlFileName")

                from(moduleDirPath)

                if (!deprecatedModuleXml.exists() && !moduleIsolatedXml.exists()) {
                    throw IllegalArgumentException("No module.xml or module-isolated.xml found in moduleDir " + pluginExtension.moduleDirName)
                } else if (deprecatedModuleXml.exists() && moduleIsolatedXml.exists()) {
                    throw IllegalArgumentException("Both xml files exist in moduleDir " + moduleDirPath +
                            " but legacy modules are no longer supported. Please remove the old module.xml file.")
                } else if (deprecatedModuleXml.exists() && !moduleIsolatedXml.exists()) {
                    logger.warn("Found only a module.xml in moduleDir " + moduleDirPath +
                            ". Renaming it to module-isolated.xml")
                    include(deprecatedModuleXmlFileName)
                    rename { filename -> filename.replace("module.xml", "module-isolated.xml") }
                } else if (!deprecatedModuleXml.exists() && moduleIsolatedXml.exists()) {
                    include(isolatedModuleXmlFileName)
                }
            }
        }
    }

    private fun copyResourceFolderToFsm(dep: Project) {
        val fsmResourcesPath = dep.projectDir.absolutePath + '/' + FSM_RESOURCES_PATH
        val fsmResourcesFolder = File(fsmResourcesPath)
        if (fsmResourcesFolder.exists()) {
            logger.info("Adding folder $fsmResourcesPath from project ${dep.name} to fsm")
            // Record duplicates to warn later
            fsmResourcesFolder.walk().filter { it.isFile }.forEach { file ->
                val relativePath = file.relativeTo(fsmResourcesFolder)
                if (!fsmResourceFiles.add(relativePath)) {
                    duplicateFsmResourceFiles.add(relativePath)
                }
            }
            into("/") {
                from(fsmResourcesPath)
            }
        } else {
            logger.debug("Not adding folder $fsmResourcesPath from project ${dep.name} to fsm, because it doesn't exist.")
        }
    }

    
    // Checks if a path contains a filename and removes the filename
    fun trimPathToDirectory(path: String?): String {
        if (path != null) {
            if (path.lastIndexOf("/") < path.lastIndexOf(".")) {
                return path.substring(0, path.lastIndexOf("/"))
            }
            return path
        }
        return ""
    }

    @TaskAction
    fun generateModuleXml() {
        logger.info("Generating module.xml files")
        val archive = archiveFile.get().asFile
        logger.info("Found archive ${archive.path}")
        FileSystems.newFileSystem(archive.toPath(), javaClass.classLoader).use { fs ->
            ZipFile(archive).use { zipFile ->
                writeModuleDescriptorToZipFile(fs, getUnfilteredModuleXml(zipFile))
            }
        }

        // Test if any duplicate fsm-resources files could overwrite each other
        if (duplicateFsmResourceFiles.isNotEmpty()) {
            val warningMessage = StringBuilder("Warning: Multiple files in fsm-resources with same path found! Files may be overwritten by each other in the FSM archive!\n")
            duplicateFsmResourceFiles.forEach {
                warningMessage.append("- ${it}\n")
            }
            logger.warn(warningMessage.toString())
        }
    }

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

    private fun getUnfilteredModuleXml(zipFile: ZipFile): String? {
        val moduleXmlFile = zipFile.getEntry("META-INF/module-isolated.xml")
        return if (moduleXmlFile == null) {
            logger.info("module-isolated.xml not found in ZipArchive ${zipFile.name}, using an empty one")
            null
        } else {
            zipFile.getInputStream(moduleXmlFile).use { it.bufferedReader().readText() }
        }
    }


    /**
     * Adds files to the classpath to include in the FSM archive.
     *
     * @param classpathToAdd The files to add.
     */
    fun addToClasspath(classpathToAdd: FileCollection) {
        classpath += classpathToAdd
    }

    /**
     * Helper method for executing Unit tests
     */
    fun execute() {
        lazyConfiguration()

        Files.createDirectories(archiveFile.get().asFile.parentFile.toPath())
        Files.createFile(archiveFile.get().asFile.toPath())
        super.copy()
        generateModuleXml()
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