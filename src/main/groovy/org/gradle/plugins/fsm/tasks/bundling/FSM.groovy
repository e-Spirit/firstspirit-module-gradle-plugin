/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
package org.gradle.plugins.fsm.tasks.bundling

import groovy.io.FileType
import groovy.transform.PackageScope
import groovy.xml.XmlUtil
import io.github.classgraph.ClassGraph
import io.github.classgraph.ScanResult
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.FileCollection
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.bundling.Jar
import org.gradle.plugins.fsm.FSMPluginExtension
import org.gradle.plugins.fsm.classloader.FsmComponentClassLoader

import java.nio.charset.StandardCharsets
import java.nio.file.*
import java.util.zip.ZipFile

import static org.gradle.plugins.fsm.XmlTagAppender.*

/**
 * Bundles the FSM using libraries from the internal {@link FileCollection} classpath
 * and the configured module.xml.
 *
 */
class FSM extends Jar {

    static final String FSM_EXTENSION = 'fsm'
    static final String FSM_RESOURCES_PATH = 'src/main/fsm-resources'
    /**
     * Output dir name for license reports of license report plugin.
     */
    static final String LICENSES_DIR_NAME = 'licenses'

    /**
     * The fsm runtime classpath. All libraries in this classpath will be copied to 'fsm/lib' folder
     */
    private FileCollection classpath

    /**
     * If set, overrides the classloader for the component scan.
     * Intended to be used for unit tests.<br/>
     *
     * If {@code null}, the default class scanning behaviour is used
     *
     * @see #configureComponentScan(ClassGraph)
     */
    private ClassLoader componentClassLoader

    /**
     * Contains all resource file paths added with an fsm-resources folder.
     * Used for duplicate checking
     */
    protected Set<String> fsmResourceFiles = new HashSet<>()

    /**
     * Contains all resource file paths of all fsm-resources folders that are duplicates. Used for duplicate warning
     */
    @PackageScope
    Set<String> duplicateFsmResourceFiles = new HashSet<>()

    public FSMPluginExtension pluginExtension
    private final List<String> moduleBlacklist = new ArrayList<>()

    @Internal
    protected List<String> getModuleBlacklist() {
        moduleBlacklist
    }

    @Internal
    ClassLoader getComponentClassLoader() {
        componentClassLoader
    }

    void setComponentClassLoader(ClassLoader classLoader) {
        this.componentClassLoader = classLoader
    }

    /**
     * Configures how the component scan finds components.
     * If {@link #componentClassLoader} is set (used in unit tests), the class loader
     * is used to find components. <br/>
     *
     * Otherwise, the default class loading behaviour is used, which scans all java
     * subprojects in this build which are part of the compile classpath
     *
     * @param classGraph The {@link ClassGraph} instance to configure
     */
    void configureComponentScan(ClassGraph classGraph) {
        if (componentClassLoader) {
            // If set, use the overridden class loader
            classGraph.addClassLoader(componentClassLoader)
        } else {
            // Get current project + all subprojects for which we have a compile dependency and get their
            // jar tasks' classpath
            def compileClasspathConfiguration = project.configurations.compileClasspath
            def projectDependencies = compileClasspathConfiguration.allDependencies.withType(ProjectDependency)
                    .collect { it.dependencyProject }
                    .findAll { it.plugins.hasPlugin(JavaPlugin) }
            def allProjects = [project] + projectDependencies
            def jarFiles = allProjects.collect {
                def jarTask = it.tasks.getByName(JavaPlugin.JAR_TASK_NAME) as Jar
                jarTask.archiveFile.get().asFile
            }
            classGraph.overrideClasspath(jarFiles)
        }
    }

    FSM() {
        archiveExtension.set(FSM_EXTENSION)
        destinationDirectory.set(project.file('build/fsm'))
        pluginExtension = project.extensions.getByType(FSMPluginExtension)
        duplicatesStrategy = DuplicatesStrategy.WARN

//        We're creating the fsm task and its config in the fsm plugin constructor, so the user's
//        configuration from the build script is not yet applied. The configuration should be deferred
//        to the time right before the task is executed (see project.gradle.taskGraph.beforeTask in FSMPlugin)
//        https://discuss.gradle.org/t/allow-tasks-to-be-configured-just-before-execution/537/2
        ext.lazyConfiguration = {

            into('lib') {
                from {
                    def classpath = getClasspath()
                    classpath ? classpath.filter { File file -> file.isFile() } : []
                }
            }

            // include licenses report
            into('META-INF') {
                from("${project.buildDir}/${LICENSES_DIR_NAME}") {
                    include "licenses.csv"
                }
            }

            // include license texts
            into('/') {
                from("${project.buildDir}/${LICENSES_DIR_NAME}") {
                    includeEmptyDirs = false
                    eachFile {
                        // Set output path
                        // - Remove "META-INF/" directory from collected licenses
                        // - Add .txt if the file doesn't have an extension
                        it.path = "META-INF/licenses/" + it.path.replaceAll("META-INF/", "/")
                        def extIndex = it.name.lastIndexOf(".")
                        if (extIndex == -1) {
                            it.path += ".txt"
                        }
                    }
                    exclude "licenses.csv"
                    exclude "index.html"
                }
            }

            // Merge fsm-resources folders of all projects
            project.rootProject.allprojects.each {
                copyResourceFolderToFsm(it, FSM_RESOURCES_PATH)
            }

            configure {
                metaInf {
                    if (pluginExtension.moduleDirName != null) {
                        // include module.xml's
                        String moduleDirPath = trimPathToDirectory(pluginExtension.moduleDirName)

                        def moduleXmlFileName = "module.xml"
                        def isolatedModuleXmlFileName = "module-isolated.xml"
                        File moduleXml = project.file(moduleDirPath + "/" + moduleXmlFileName)
                        File moduleIsolatedXml = project.file(moduleDirPath + "/" + isolatedModuleXmlFileName)

                        from moduleDirPath
                        if (!moduleXml.exists() && !moduleIsolatedXml.exists()) {
                            throw new IllegalArgumentException("No module.xml or module-isolated.xml found in moduleDir " + pluginExtension.moduleDirName)
                        }
                        if (moduleXml.exists() && moduleIsolatedXml.exists()) {
                            getLogger().info("Both xml files exist in moduleDir " + moduleDirPath)
                            include moduleXmlFileName
                            include isolatedModuleXmlFileName
                        }
                        else if (moduleXml.exists() && !moduleIsolatedXml.exists()) {
                            getLogger().warn("Found only a module.xml in moduleDir " + moduleDirPath +
                                             ". Using the default template-module-isolated.xml to replace the missing module-isolated.xml")
                            include moduleXmlFileName
                        }
                        else if (!moduleXml.exists() && moduleIsolatedXml.exists()) {
                            getLogger().warn("Found only a module-isolated.xml in moduleDir " + moduleDirPath +
                                             ". Using the default template-module.xml to replace the missing module.xml")
                            include isolatedModuleXmlFileName
                        }
                    }
                }
            }

        }

    }

    protected void copyResourceFolderToFsm(Project dep, String relativeResourcesPath) {
        def fsmResourcesPath = dep.projectDir.absolutePath + '/' + relativeResourcesPath
        def fsmResourcesFolder = new File(fsmResourcesPath)
        if (fsmResourcesFolder.exists()) {
            logger.info("Adding folder ${fsmResourcesPath} from project ${dep.name} to fsm")
            // Record duplicates to warn later
            fsmResourcesFolder.eachFileRecurse(FileType.FILES, { file ->
                def relativePath = fsmResourcesFolder.relativePath(file)
                if (!fsmResourceFiles.add(relativePath)) {
                    duplicateFsmResourceFiles.add(relativePath)
                }
            })
            into('/') {
                from(fsmResourcesPath)
            }
        } else {
            logger.debug("Not adding folder ${fsmResourcesPath} from project ${dep.name} to fsm, because it doesn't exist.")
        }
    }

    //checks if a path contains a filename and removes the filename
    static String trimPathToDirectory(String path){
        if (path != null) {
            if (path.lastIndexOf("/") < path.lastIndexOf(".")) {
                return path.substring(0,path.lastIndexOf("/"))
            }
            return path
        }
        return ""
    }

    static class XMLData {
        String moduleTags = ""
        String componentTags = ""
        String resourcesTags = ""
        String licenseTags = ""
        boolean isolated
    }

    /**
     * Helper method for executing Unit tests
     */
    void execute() {
        if (lazyConfiguration instanceof List) {
            lazyConfiguration.each { configure it }
        } else {
            configure lazyConfiguration
        }

        Files.createDirectories(archiveFile.get().asFile.parentFile.toPath())
        Files.createFile(archiveFile.get().asFile.toPath())
        super.copy()
        generateModuleXmls()
    }

    @TaskAction
    protected void generateModuleXmls() {
        getLogger().info("Generating module.xml files")
        File archive = archiveFile.get().asFile
        getLogger().info("Found archive ${archive.getPath()}")
        FileSystems.newFileSystem(archive.toPath(), getClass().getClassLoader()).withCloseable { fs ->
            new ZipFile(archive).withCloseable { zipFile ->

                boolean isolated
                legacy: {
                    isolated = false
                    XMLData moduleXml = getXMLTagsFromAppender(pluginExtension.appendDefaultMinVersion, isolated, moduleBlacklist)
                    writeModuleDescriptorToBuildDirAndZipFile(fs, getUnfilteredModuleXml(zipFile, isolated), moduleXml)
                }
                isolated: {
                    isolated = true
                    XMLData moduleIsolatedXml = getXMLTagsFromAppender(pluginExtension.appendDefaultMinVersion, isolated, moduleBlacklist)
                    writeModuleDescriptorToBuildDirAndZipFile(fs, getUnfilteredModuleXml(zipFile, isolated), moduleIsolatedXml)
                }
			}
		}

        // Test if any duplicate fsm-resources files could overwrite each other
        if (duplicateFsmResourceFiles.any()) {
            def warningMessage = new StringBuilder("Warning: Multiple files in fsm-resources with same path found! Files may be overwritten by each other in the FSM archive!\n")
            duplicateFsmResourceFiles.each {
                warningMessage.append("- ${it}\n")
            }
            getLogger().warn(warningMessage.toString())
        }
    }

    void writeModuleDescriptorToBuildDirAndZipFile(FileSystem fs, String unfilteredModuleXml, XMLData moduleXML) {
        String filteredModuleXml = XmlUtil.serialize(filterModuleXml(unfilteredModuleXml, moduleXML))
        String fileName = "module${moduleXML.isolated ? "-isolated" : ""}.xml"

        Paths.get(destinationDirectory.get().toString(), fileName).toFile() << filteredModuleXml

        Path nf = fs.getPath("/META-INF/" + fileName)
        Files.newBufferedWriter(nf, StandardCharsets.UTF_8, StandardOpenOption.CREATE).withCloseable {
            it.write(filteredModuleXml)
        }
    }

    String filterModuleXml(String unfilteredModuleXml, XMLData xmlData) {
        String filteredModuleXml = unfilteredModuleXml.replace('$name', pluginExtension.moduleName ?: project.name)
        filteredModuleXml = filteredModuleXml.replace('$displayName', pluginExtension.displayName?.toString() ?: project.name.toString())
        filteredModuleXml = filteredModuleXml.replace('$version', project.version.toString())
        filteredModuleXml = filteredModuleXml.replace('$description', project.description?.toString() ?: project.name.toString())
        filteredModuleXml = filteredModuleXml.replace('$vendor', pluginExtension.vendor?.toString() ?: "")
        filteredModuleXml = filteredModuleXml.replace('$artifact', project.jar.archiveName.toString())
        filteredModuleXml = filteredModuleXml.replace('$class', xmlData.moduleTags)
        filteredModuleXml = filteredModuleXml.replace('$dependencies', getFsmDependencyTags(project))
        filteredModuleXml = filteredModuleXml.replace('$resources', xmlData.resourcesTags)
        filteredModuleXml = filteredModuleXml.replace('$components', xmlData.componentTags)
        filteredModuleXml = filteredModuleXml.replace('$licensesFile', xmlData.licenseTags)
        getLogger().info("Generated module.xml: \n$filteredModuleXml")
        filteredModuleXml
    }

    String getUnfilteredModuleXml(ZipFile zipFile, boolean iso) {
        String isolated = iso ? "-isolated" : ""
        def unfilteredModuleXml
        def moduleXmlFile = zipFile.getEntry("META-INF/module${isolated}.xml")
        if (moduleXmlFile == null) {
            getLogger().info("module${isolated}.xml not found in ZipArchive ${zipFile.getName()}, using an empty one")
            unfilteredModuleXml = getClass().getResource("/template-module${isolated}.xml").getText("utf-8")
        } else {
            unfilteredModuleXml = zipFile.getInputStream(moduleXmlFile).getText("utf-8")
        }
        unfilteredModuleXml
    }

    XMLData getXMLTagsFromAppender(boolean appendDefaultMinVersion, boolean isolated, List<String> moduleBlacklist = new ArrayList<String>()) {
        def moduleXml = new XMLData(isolated: isolated)

        new FsmComponentClassLoader(project).withCloseable { classLoader ->
            def classGraph = new ClassGraph()
                    .enableClassInfo()
                    .enableAnnotationInfo()
            configureComponentScan(classGraph)

            def scan = classGraph.scan()
            scan.withCloseable {

                WebXmlPaths webXmlPaths
                components: {
                    StringBuilder result = new StringBuilder()
                    webXmlPaths = appendComponentsTag(project, classLoader, new ClassScannerResultDelegate(scan), appendDefaultMinVersion, result, isolated)
                    moduleXml.componentTags = result.toString()
                }

                moduleAnnotation: {
                    StringBuilder result = new StringBuilder()
                    appendModuleAnnotationTags(classLoader, new ClassScannerResultDelegate(scan), result, moduleBlacklist)
                    moduleXml.moduleTags = result.toString()
                }

                licenses: {
                    String result = "META-INF/licenses.csv"
                    moduleXml.licenseTags = result
                }

                moduleXml.resourcesTags = getResourcesTags(project, webXmlPaths, pluginExtension.resourceMode, pluginExtension.appendDefaultMinVersion, isolated)
            }
        }

        return moduleXml
    }

    /**
     * Simple class scanner interface facade to improve testability
     */
    interface ClassScannerResultProvider {

        List<String> getNamesOfClassesImplementing(final Class<?> implementedInterface)

        List<String> getNamesOfClassesWithAnnotation(final Class<?> annotation)
    }

    /**
     * Delegates to given io.github.lukehutch.fastclasspathscanner.scanner.ScanResult instance
     */
    private final class ClassScannerResultDelegate implements ClassScannerResultProvider {

        private final ScanResult scan

        ClassScannerResultDelegate(ScanResult scan) {
            this.scan = scan
        }

        @Override
        List<String> getNamesOfClassesImplementing(final Class<?> implementedInterface) {
            return scan.getClassesImplementing(implementedInterface.name).getNames()
        }

        @Override
        List<String> getNamesOfClassesWithAnnotation(final Class<?> annotation) {
            return scan.getClassesWithAnnotation(annotation.name).getNames()
        }
    }

    /**
     * Returns the classpath to include in the FSM archive. Any JAR or ZIP files in this classpath are included in the
     * {@code lib} directory.
     *
     * @return The classpath. Returns an empty collection when there is no classpath to include in the FSM.
     */
    @InputFiles
    @Optional
    FileCollection getClasspath() {
        return classpath
    }

    /**
     * Sets the classpath to include in the FSM archive.
     *
     * @param classpath The classpath. Must not be null.
     */
    void setClasspath(Object classpath) {
        this.classpath = project.files(classpath)
    }

    /**
     * Adds files to the classpath to include in the FSM archive.
     *
     * @param classpath The files to add. These are evaluated as for {@link org.gradle.api.Project#files(Object [])}
     */
    void classpath(Object... classpath) {
        FileCollection oldClasspath = getClasspath()
        this.classpath = project.files(oldClasspath ?: [], classpath)
    }
}
