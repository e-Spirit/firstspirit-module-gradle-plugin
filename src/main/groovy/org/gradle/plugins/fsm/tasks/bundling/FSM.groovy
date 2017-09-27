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

import io.github.lukehutch.fastclasspathscanner.FastClasspathScanner
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

		into('lib') {
			from {
				def classpath = getClasspath()
				classpath ? classpath.filter { File file -> file.isFile() } : []
			}
		}

		configure {
			metaInf {
				from project.file(pluginExtension.moduleDirName)
				include 'module.xml'
			}
		}
	}
	@TaskAction
	protected void generateModuleXml() {
		getLogger().info("Generating module.xml")
		File archive = getArchivePath()
		getLogger().info("Found archive ${archive.getPath()}")

		def resourcesTags = getResourcesTags(project.configurations)

		URI uri = archive.toURI()

		(FileSystems.newFileSystem(Paths.get(uri), getClass().getClassLoader())).withCloseable { fs ->
			new ZipFile(archive).withCloseable { zipFile ->
				def moduleXmlFile = zipFile.getEntry("META-INF/module.xml")
				def unfilteredModuleXml = zipFile.getInputStream(moduleXmlFile).getText("utf-8")

				//TODO Add support for non-existent module.xml
				if(unfilteredModuleXml == null || unfilteredModuleXml.isEmpty()) {
					throw new IllegalStateException("No module.xml file found or it is empty!")
				}

				def componentTags = getComponentTags(archive)

				String filteredModuleXml = filterModuleXml(unfilteredModuleXml, resourcesTags, componentTags)

				Path nf = fs.getPath("/META-INF/module.xml")
				Files.newBufferedWriter(nf, StandardCharsets.UTF_8, StandardOpenOption.CREATE).withCloseable {
					it.write(filteredModuleXml)
				}
			}
		}
	}

	protected String filterModuleXml(String unfilteredModuleXml, String resourcesTags, String componentTags) {
		String filteredModuleXml = unfilteredModuleXml.replace('$name', project.name.toString())
		filteredModuleXml = filteredModuleXml.replace('$version', project.version.toString())
		filteredModuleXml = filteredModuleXml.replace('$description', project.description?.toString() ?: project.name.toString())
		filteredModuleXml = filteredModuleXml.replace('$artifact', project.jar.archiveName.toString())
		filteredModuleXml = filteredModuleXml.replace('$resources', resourcesTags)
		filteredModuleXml = filteredModuleXml.replace('$components', componentTags)
		getLogger().info("Generated module.xml: \n$filteredModuleXml")
		filteredModuleXml
	}

	String getComponentTags(File archive) {
		StringBuilder result = new StringBuilder()
		File tempDir = unzipFsmToNewTempDir(archive)

		def libDir = new File(Paths.get(tempDir.getPath(), "lib").toString())
		new JarClassLoader(libDir, getClass().getClassLoader()).withCloseable { classLoader ->
			try {
				def scan = new FastClasspathScanner().addClassLoader(classLoader).scan()

				appendProjectAppTags(scan, classLoader, result)

				appendWebAppTags(project, scan, classLoader, result)

				appendPublicComponentTags(scan, classLoader, result)

			} catch (MalformedURLException e) {
				getLogger().error("Passed URL is malformed", e)
			} catch (ClassNotFoundException e) {
				getLogger().error("Cannot find class", e)
			}
		}

		return result.toString()
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
		if(file.isFile()) {
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
	@InputFiles @Optional
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
