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

import com.google.common.base.Charsets
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.bundling.Jar
import org.gradle.plugins.fsm.FSMPluginExtension

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
	
	FSM() {
		extension = FSM_EXTENSION
		destinationDir = project.file('build/fsm')
		
		into('lib') {
			from {
				def classpath = getClasspath()
				classpath ? classpath.filter {File file -> file.isFile()} : []
			}
		}

		configure {
			metaInf {
				from project.file(moduleDirName)
				include 'module.xml'

//				expand(name: project.name,
//						version: project.version,
//						description: project.description,
//						artifact: project.jar.archiveName,
//						resources: projectResources)
			}
		}
	}
	
	@Override
	@TaskAction
	protected void copy() {
		super.copy();
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

		// TODO: Make this pretty
		compileDependenciesServerScoped.forEach {
			if (!providedCompileDependencies.contains(it)) {
				ModuleVersionIdentifier dependencyId = it.moduleVersion.id
				projectResources += """<resource name="${dependencyId.group}.${dependencyId.name}" scope="server" version="${dependencyId.version}">lib/${dependencyId.name}-${dependencyId.version}.${it.extension}</resource>\n"""
			}
		}
		projectResources += "\n"
		compileDependenciesModuleScoped.forEach {
			if (!providedCompileDependencies.contains(it)) {
				ModuleVersionIdentifier dependencyId = it.moduleVersion.id
				projectResources += """<resource name="${dependencyId.group}.${dependencyId.name}" scope="module" version="${dependencyId.version}">lib/${dependencyId.name}-${dependencyId.version}.${it.extension}</resource>\n"""
			}
		}
		projectResources
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
