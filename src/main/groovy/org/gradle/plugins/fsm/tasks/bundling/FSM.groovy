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
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.bundling.Jar
import org.gradle.plugins.fsm.FSMPluginExtension
import org.gradle.plugins.fsm.classloader.JarClassLoader
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

    /**
     * The fsm runtime classpath. All libraries in this
     * classpath will be copied to 'fsm/lib' folder
     */
    private FileCollection classpath

    public FSMPluginExtension pluginExtension

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

            into('/') {
                from("src/main/resources") {
                    include("**/*")
                }
            }

            into('/files') {
                from('src/main/files')
            }


            configure {
                metaInf {
                    from project.file(trimPathToDirectory(pluginExtension.moduleDirName))
                    if(checkModuleDir()) {
                        include 'module.xml'
                        include 'module-isolated.xml'
                    }
                    else{
                        throw new IllegalArgumentException("No module.xml or module-isolated.xml found in moduleDir " + pluginExtension.moduleDirName)
                    }
                }
            }
        }

    }

    //checks if a path contains a filename and removes the filename
    String trimPathToDirectory(String path){

        if(path.lastIndexOf("/") < path.lastIndexOf(".")){
            return path.substring(0,path.lastIndexOf("/"))
        }
        return path
    }
    boolean checkModuleDir(){
        String moduleDirName = trimPathToDirectory(pluginExtension.moduleDirName)
        if(moduleDirName != "src/main/resources") {
            File moduleXml = project.file(moduleDirName + "/module.xml")
            File moduleIsolatedXml = project.file(moduleDirName + "/module-isolated.xml")
            if(!moduleXml.exists() && !moduleIsolatedXml.exists()){
                return false
            }
            if(moduleXml.exists() && !moduleIsolatedXml.exists()){
                getLogger().warn("Found only a module.xml in moduleDir " + moduleDirName +
                                 ". Using the standard template-module-isolated.xml instead.")
            }
            if(!moduleXml.exists() && moduleIsolatedXml.exists()){
                getLogger().warn("Found only a module-isolated.xml in moduleDir " + moduleDirName +
                                 ". Using the standard template-module.xml instead.")
            }
        }
        return true
    }

    @TaskAction
    protected void generateModuleXmls() {
        getLogger().info("Generating module.xml files")
        File archive = getArchivePath()
        getLogger().info("Found archive ${archive.getPath()}")

        (FileSystems.newFileSystem(archive.toPath(), getClass().getClassLoader())).withCloseable { fs ->
            new ZipFile(archive).withCloseable { zipFile ->
                def unfilteredModuleXml = getUnfilteredModuleXml(zipFile)

                def componentTags = getModuleXmlComponentTags(archive, pluginExtension.appendDefaultMinVersion)

                legacy: {
                    def resourcesTagsLegacy = getResourcesTags(project, pluginExtension.resourceMode, pluginExtension.appendDefaultMinVersion, true)
                    writeModuleDescriptorToBuildDirAndZipFile(fs, unfilteredModuleXml, componentTags, resourcesTagsLegacy, "module.xml")
                }
                isolated: {
                    def resourcesTagsIsolated = getResourcesTags(project, pluginExtension.resourceMode, pluginExtension.appendDefaultMinVersion)
                    writeModuleDescriptorToBuildDirAndZipFile(fs, unfilteredModuleXml, componentTags, resourcesTagsIsolated, "module-isolated.xml")
                }
			}
		}
	}

    void writeModuleDescriptorToBuildDirAndZipFile(FileSystem fs, String unfilteredModuleXml, String componentTags, String resourcesTags, String fileName) {
        String filteredModuleXml = XmlUtil.serialize(filterModuleXml(unfilteredModuleXml, resourcesTags, componentTags))

        Paths.get(destinationDir.toString(), fileName).toFile() << filteredModuleXml

        Path nf = fs.getPath("/META-INF/" + fileName)
        Files.newBufferedWriter(nf, StandardCharsets.UTF_8, StandardOpenOption.CREATE).withCloseable {
            it.write(filteredModuleXml)
        }
    }

    String getUnfilteredModuleXml(ZipFile zipFile) {
        def unfilteredModuleXml
        def moduleXmlFile = zipFile.getEntry("META-INF/module.xml")
        if (moduleXmlFile == null) {
            getLogger().info("module.xml not found in ZipArchive ${zipFile.getName()}, using an empty one")
            unfilteredModuleXml = getClass().getResource("/template-module.xml").getText("utf-8")
        } else {
            unfilteredModuleXml = zipFile.getInputStream(moduleXmlFile).getText("utf-8")
        }
        unfilteredModuleXml
    }

    protected String filterModuleXml(String unfilteredModuleXml, String resourcesTags, String componentTags) {
        String filteredModuleXml = unfilteredModuleXml.replace('$name', pluginExtension.moduleName ?: project.name)
        filteredModuleXml = filteredModuleXml.replace('$displayName', pluginExtension.displayName?.toString() ?: project.name.toString())
        filteredModuleXml = filteredModuleXml.replace('$version', project.version.toString())
        filteredModuleXml = filteredModuleXml.replace('$description', project.description?.toString() ?: project.name.toString())
        filteredModuleXml = filteredModuleXml.replace('$vendor', pluginExtension.vendor?.toString() ?: "")
        filteredModuleXml = filteredModuleXml.replace('$artifact', project.jar.archiveName.toString())
        filteredModuleXml = filteredModuleXml.replace('$resources', resourcesTags)
        filteredModuleXml = filteredModuleXml.replace('$components', componentTags)
        getLogger().info("Generated module.xml: \n$filteredModuleXml")
        filteredModuleXml
    }

    String getModuleXmlComponentTags(File archive, boolean appendDefaultMinVersion) {
        StringBuilder result = new StringBuilder()
        File tempDir = unzipFsmToNewTempDir(archive)

        def libDir = new File(Paths.get(tempDir.getPath(), "lib").toString())
        new JarClassLoader(libDir, getClass().getClassLoader()).withCloseable { classLoader ->
            try {
                def scan = new FastClasspathScanner().addClassLoader(classLoader).scan()

                appendComponentsTag(project, classLoader, new ClassScannerResultDelegate(scan), appendDefaultMinVersion, result)

            } catch (MalformedURLException e) {
                getLogger().error("Passed URL is malformed", e)
            } catch (ClassNotFoundException e) {
                getLogger().error("Cannot find class", e)
            }
        }

        return result.toString()
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
