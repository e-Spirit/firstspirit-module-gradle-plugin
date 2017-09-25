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

import com.sun.nio.zipfs.ZipFileSystem
import de.espirit.firstspirit.module.ProjectApp
import io.github.lukehutch.fastclasspathscanner.FastClasspathScanner
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.bundling.Jar
import org.gradle.plugins.fsm.FSMPluginExtension

import java.nio.charset.StandardCharsets
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

/**
 * Bundles the FSM using libraries from the internal {@link FileCollection} classpath
 * and the configured module.xml.
 *
 */
class FSM extends Jar {
	static final String FSM_EXTENSION = 'fsm'

	//TODO: We could determine classes by scanning access.jar - worth it?
	static final List<String> projectAppBlacklist = ["de.espirit.firstspirit.feature.ContentTransportProjectApp"]

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
		println "#########################################"

		File archive = getArchivePath()
		println "Archive path:" + archive.getPath()

		def resourcesTags = getResourcesTags()

		ZipFile zipFile = null
		ZipFileSystem fs = null
		Writer writer = null
		try {

			zipFile = new ZipFile(archive)

			def moduleXmlFile = zipFile.getEntry("META-INF/module.xml")
			def unfilteredModuleXml = zipFile.getInputStream(moduleXmlFile).getText("utf-8")

			if(unfilteredModuleXml == null || unfilteredModuleXml.isEmpty()) {
				throw new IllegalStateException("No module.xml file found or it is empty!")
			}

			def componentTags = getComponentTags(archive)

			String filteredModuleXml = unfilteredModuleXml.replaceFirst("\\\$name", project.name)
			filteredModuleXml = filteredModuleXml.replaceFirst("\\\$version", project.version)
			filteredModuleXml = filteredModuleXml.replaceFirst("\\\$description", project.description ?: project.name)
			filteredModuleXml = filteredModuleXml.replaceFirst("\\\$artifact", project.jar.archiveName)
			filteredModuleXml = filteredModuleXml.replaceFirst("\\\$resources", resourcesTags)
			filteredModuleXml = filteredModuleXml.replaceFirst("\\\$components", componentTags)
			println filteredModuleXml

			Map<String, String> env = new HashMap<>()
			env.put("create", "true")
			URI uri = archive.toURI()
			fs = FileSystems.newFileSystem(Paths.get(uri), getClass().getClassLoader()) as ZipFileSystem
			Path nf = fs.getPath("/META-INF/module.xml")
			writer = Files.newBufferedWriter(nf, StandardCharsets.UTF_8, StandardOpenOption.CREATE)
			writer.write(filteredModuleXml)
		} finally {
			zipFile?.close()
			writer?.close()
			fs?.close()
		}
	}

	String getComponentTags(File archive) {
		String result = ""
		System.out.println("################## Extracting archive")
		def tempDir = getTemporaryDirFactory().create()

		UnzipUtility unzipper = new UnzipUtility()
		try {
			unzipper.unzip(archive.getPath(), tempDir.getPath())
		} catch (Exception ex) {
			ex.printStackTrace()
		}

		List<URL> jarFilesUrls = new ArrayList()

		def libDir = new File(Paths.get(tempDir.getPath(), "lib").toString())
		Arrays.asList(libDir.listFiles()).forEach { jarFile ->
			try {
				URL url = new File(jarFile.path).toURL()
				jarFilesUrls.add(url)
			} catch (Exception e) {
				e.printStackTrace()
			}
		}
		try {
			def jarFilesArray = jarFilesUrls.toArray(new URL[0])
			URLClassLoader cl = new URLClassLoader(jarFilesArray, getClass().getClassLoader())

			"This is a compiletime dependency to " + ProjectApp.class.toString() + " that does nothing."

			new FastClasspathScanner().addClassLoader(cl).scan().getNamesOfClassesImplementing(ProjectApp).forEach {

				if(!projectAppBlacklist.contains(it)) {
					def projectAppClass = cl.loadClass(it)
					println "Found ProjectAppClass " + projectAppClass

					result += """
						<project-app>
							<name>AB-Testing_ProjectApp</name>
							<displayname>A/B-Testing Project Configuration</displayname>
							<description>Project configuration implementation</description>
							<class>com.espirit.moddev.abtesting.projectapp.AbTestingProjectApp</class>
							<configurable>com.espirit.moddev.abtesting.projectapp.AbTestingProjectAppConfigurator</configurable>
						</project-app>
						"""
				}
			}

		} catch (MalformedURLException e) {
			System.err.println(e)
		} catch (ClassNotFoundException e) {
			System.err.println(e)
		}

		return result
	}

	public void list(List<URL> urlsList, File file) {
		if(file.isFile()) {
			urlsList.add(file.toURI().toURL())
		}
		File[] children = file.listFiles()
		for (File child : children) {
			list(urlsList, child)
		}
	}


	String getResourcesTags() {
		String projectResources = ""
		Set<ResolvedArtifact> compileDependenciesServerScoped = project.configurations.fsServerCompile.getResolvedConfiguration().getResolvedArtifacts()
		Set<ResolvedArtifact> compileDependenciesModuleScoped = project.configurations.fsModuleCompile.getResolvedConfiguration().getResolvedArtifacts()
		Set<ResolvedArtifact> providedCompileDependencies = project.configurations.fsProvidedCompile.getResolvedConfiguration().getResolvedArtifacts()

		projectResources = addResourceTagsForDependencies(compileDependenciesServerScoped, providedCompileDependencies, projectResources, "server")
		projectResources += "\n"
		projectResources = addResourceTagsForDependencies(compileDependenciesModuleScoped, providedCompileDependencies, projectResources, "module")
		return projectResources
	}

	private String addResourceTagsForDependencies(Set<ResolvedArtifact> compileDependenciesServerScoped, providedCompileDependencies, String projectResources, String scope) {
		compileDependenciesServerScoped.forEach {
			if (!providedCompileDependencies.contains(it)) {
				ModuleVersionIdentifier dependencyId = it.moduleVersion.id
				projectResources += getResourceTagForDependency(dependencyId, it, scope)
			}
		}
		return projectResources
	}

	protected GString getResourceTagForDependency(ModuleVersionIdentifier dependencyId, ResolvedArtifact it, String scope) {
		"""<resource name="${dependencyId.group}.${dependencyId.name}" scope="${scope}" version="${dependencyId.version}">lib/${dependencyId.name}-${dependencyId.version}.${it.extension}</resource>\n"""
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


	/**
	* This utility extracts files and directories of a standard zip file to
	* a destination directory.
	* @author www.codejava.net
	*
	*/
	public class UnzipUtility {
		/**
		 * Size of the buffer to read/write data
		 */
		private static final int BUFFER_SIZE = 4096;
		/**
		 * Extracts a zip file specified by the zipFilePath to a directory specified by
		 * destDirectory (will be created if does not exists)
		 * @param zipFilePath
		 * @param destDirectory
		 * @throws IOException
		 */
		public void unzip(String zipFilePath, String destDirectory) throws IOException {
			File destDir = new File(destDirectory);
			if (!destDir.exists()) {
				destDir.mkdir();
			}
			ZipInputStream zipIn = new ZipInputStream(new FileInputStream(zipFilePath));
			ZipEntry entry = zipIn.getNextEntry();
			// iterates over entries in the zip file
			while (entry != null) {
				String filePath = destDirectory + File.separator + entry.getName();
				if (!entry.isDirectory()) {
					// if the entry is a file, extracts it
					extractFile(zipIn, filePath);
				} else {
					// if the entry is a directory, make the directory
					File dir = new File(filePath);
					dir.mkdir();
				}
				zipIn.closeEntry();
				entry = zipIn.getNextEntry();
			}
			zipIn.close();
		}
		/**
		 * Extracts a zip entry (file entry)
		 * @param zipIn
		 * @param filePath
		 * @throws IOException
		 */
		private void extractFile(ZipInputStream zipIn, String filePath) throws IOException {
			BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(filePath));
			byte[] bytesIn = new byte[BUFFER_SIZE];
			int read = 0;
			while ((read = zipIn.read(bytesIn)) != -1) {
				bos.write(bytesIn, 0, read);
			}
			bos.close();
		}
	}

}
