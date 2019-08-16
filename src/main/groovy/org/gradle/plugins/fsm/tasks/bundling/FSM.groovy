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

import groovy.xml.XmlUtil
import io.github.lukehutch.fastclasspathscanner.FastClasspathScanner
import io.github.lukehutch.fastclasspathscanner.scanner.ScanResult
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.bundling.Jar
import org.gradle.plugins.fsm.FSMPluginExtension
import org.gradle.plugins.fsm.classloader.JarClassLoader
import org.gradle.plugins.fsm.configurations.FSMConfigurationsPlugin
import org.gradle.plugins.fsm.zip.UnzipUtility

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
     * The fsm runtime classpath. All libraries in this
     * classpath will be copied to 'fsm/lib' folder
     */
    private FileCollection classpath

    public FSMPluginExtension pluginExtension
    private final List<String> moduleBlacklist = new ArrayList<>()
    protected List<String> getModuleBlacklist() {
        return moduleBlacklist
    }

    FSM() {

        extension = FSM_EXTENSION
        destinationDir = project.file('build/fsm')
        pluginExtension = project.getExtensions().getByType(FSMPluginExtension)

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

            // include license file
            into('META-INF') {
                from("${project.buildDir}/${LICENSES_DIR_NAME}") {
                    include "licenses.csv"
                }
            }

            FSMConfigurationsPlugin.FS_CONFIGURATIONS.forEach { configName ->
                def config = project.configurations.getByName(configName)
                def projectDependencies = config.getAllDependencies().withType(ProjectDependency)
                projectDependencies.forEach { ProjectDependency dep ->
                    copyResourceFolderToFsm(dep.dependencyProject, FSM_RESOURCES_PATH)
                }
            }
            copyResourceFolderToFsm(project, FSM_RESOURCES_PATH)

            configure {
                metaInf {
                    if(pluginExtension.moduleDirName != null){
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
                        if(moduleXml.exists() && moduleIsolatedXml.exists()){
                            getLogger().info("Both xml files exist in moduleDir " + moduleDirPath)
                            include moduleXmlFileName
                            include isolatedModuleXmlFileName
                        }
                        else if(moduleXml.exists() && !moduleIsolatedXml.exists()){
                            getLogger().warn("Found only a module.xml in moduleDir " + moduleDirPath +
                                             ". Using the default template-module-isolated.xml to replace the missing module-isolated.xml")
                            include moduleXmlFileName
                        }
                        else if(!moduleXml.exists() && moduleIsolatedXml.exists()){
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
            into('/') {
                from(fsmResourcesPath)
            }
        } else {
            logger.debug("Not adding folder ${fsmResourcesPath} from project ${dep.name} to fsm, because it doesn't exist.")
        }
    }

    //checks if a path contains a filename and removes the filename
    static String trimPathToDirectory(String path){
        if(path != null){
            if(path.lastIndexOf("/") < path.lastIndexOf(".")){
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

    @TaskAction
    protected void generateModuleXmls() {
        getLogger().info("Generating module.xml files")
        File archive = getArchivePath()
        getLogger().info("Found archive ${archive.getPath()}")
        (FileSystems.newFileSystem(archive.toPath(), getClass().getClassLoader())).withCloseable { fs ->
            new ZipFile(archive).withCloseable { zipFile ->

                boolean isolated
                legacy: {
                    isolated = false
                    XMLData moduleXml = getXMLTagsFromAppender(archive, pluginExtension.appendDefaultMinVersion, isolated, moduleBlacklist)
                    writeModuleDescriptorToBuildDirAndZipFile(fs, getUnfilteredModuleXml(zipFile, isolated), moduleXml)
                }
                isolated: {
                    isolated = true
                    XMLData moduleIsolatedXml = getXMLTagsFromAppender(archive, pluginExtension.appendDefaultMinVersion, isolated, moduleBlacklist)
                    writeModuleDescriptorToBuildDirAndZipFile(fs, getUnfilteredModuleXml(zipFile, isolated), moduleIsolatedXml)
                }
			}
		}
	}

    void writeModuleDescriptorToBuildDirAndZipFile(FileSystem fs, String unfilteredModuleXml, XMLData moduleXML) {
        String filteredModuleXml = XmlUtil.serialize(filterModuleXml(unfilteredModuleXml, moduleXML))
        String fileName = "module${moduleXML.isolated ? "-isolated" : ""}.xml"

        Paths.get(destinationDir.toString(), fileName).toFile() << filteredModuleXml

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

    XMLData getXMLTagsFromAppender(File archive, boolean appendDefaultMinVersion, boolean isolated, List<String> moduleBlacklist = new ArrayList<String>()) {
        File tempDir = unzipFsmToNewTempDir(archive)
        def moduleXml = new XMLData(isolated: isolated)


        def libDir = new File(Paths.get(tempDir.getPath(), "lib").toString())
        new JarClassLoader(libDir, getClass().getClassLoader()).withCloseable { classLoader ->
            try {
                def scan = new FastClasspathScanner().addClassLoader(classLoader).scan()

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

            } catch (MalformedURLException e) {
                getLogger().error("Passed URL is malformed", e)
            } catch (ClassNotFoundException e) {
                getLogger().error("Cannot find class", e)
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
            return scan.getNamesOfClassesImplementing(implementedInterface)
        }

        @Override
        List<String> getNamesOfClassesWithAnnotation(final Class<?> annotation) {
            return scan.getNamesOfClassesWithAnnotation(annotation)
        }
    }

    private File unzipFsmToNewTempDir(File archive) {
        def tempDir = getTemporaryDirFactory().create()
        getLogger().info("Extracting archive to $tempDir")

        try {
            new UnzipUtility().unzip(archive.getPath(), tempDir.getPath())
        } catch (IOException ex) {
            getLogger().error("Problem with fsm unzipping", ex)
        }
        tempDir
    }

    void list(List<URL> urlsList, File file) {
        if (file.isFile()) {
            urlsList.add(file.toURI().toURL())
        }
        File[] children = file.listFiles()
        for (File child : children) {
            list(urlsList, child)
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
