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
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.bundling.Jar
import org.gradle.plugins.fsm.FSMPluginExtension

import java.nio.charset.StandardCharsets
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.util.zip.ZipFile

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

//				expand(name: project.name,
//						version: project.version,
//						description: project.description,
//						artifact: project.jar.archiveName,
//						resources: projectResources)
			}
		}
	}
	@TaskAction
	protected void generateModuleXml() {
		println "#########################################"
		File archive = getArchivePath()
		println "Archive path:" + archive.getPath()

		ZipFile zipFile = new ZipFile(archive)
		def moduleXmlFile = zipFile.getEntry("META-INF/module.xml")

		String unfilteredModuleXml = zipFile.getInputStream(moduleXmlFile).getText("utf-8")
		zipFile.close()
		String filteredModuleXml = unfilteredModuleXml.replaceFirst("\\\$name", project.name)
		filteredModuleXml = filteredModuleXml.replaceFirst("\\\$description", project.description ?: project.name)
		filteredModuleXml = filteredModuleXml.replaceFirst("\\\$artifact", project.jar.archiveName)
		filteredModuleXml = filteredModuleXml.replaceFirst("\\\$resources", getResourcesTags())
		println filteredModuleXml

		Map<String, String> env = new HashMap<>()
		env.put("create", "true")
		URI uri = archive.toURI()
		ZipFileSystem fs = FileSystems.newFileSystem(Paths.get(uri), getClass().getClassLoader())
		Path nf = fs.getPath("/META-INF/module.xml")
		Writer writer = Files.newBufferedWriter(nf, StandardCharsets.UTF_8, StandardOpenOption.CREATE)
		writer.write(filteredModuleXml)

		writer.close()
		fs.close()

	}

	def patchModuleXml() {

		SourceSetContainer sourceSets = (SourceSetContainer) project.getProperties().get("sourceSets")
		def classesDir = sourceSets.getByName("main").output.classesDir
		System.out.println("##################################################")
		System.out.println(classesDir)

		try {
			// Convert File to a URL
			URL url = classesDir.toURL()
			List<URL> urlsList = new ArrayList<URL>()
			list(urlsList, new File(url.path))

			URL[] urls = urlsList.toArray(new URL[urlsList.size()])
			new ArrayList(Arrays.asList(urls)).forEach{
				System.out.println(it)
			}
			ClassLoader cl = new URLClassLoader(urls)

//				Reflections reflections = new Reflections(cl)
//				Set<Class<?>> subTypes = reflections.getSubTypesOf(Class.forName("org.example.Foo"))
//				subTypes.forEach{
//					System.out.println(it)
//				}

//				Class cls = cl.loadClass("org.example.SimpleFoo")
//				println cls
		} catch (MalformedURLException e) {
			System.err.println(e)
		} catch (ClassNotFoundException e) {
			System.err.println(e)
		}

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

}
