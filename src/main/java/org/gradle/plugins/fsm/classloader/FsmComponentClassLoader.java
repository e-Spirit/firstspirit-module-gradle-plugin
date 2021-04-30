package org.gradle.plugins.fsm.classloader;

import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.bundling.Jar;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

/**
 * Class Loader used for instantiating components located in an
 * FSM file. For a given FSM project, it will use the project jar
 * and its runtime and compile classpaths
 */
public class FsmComponentClassLoader extends URLClassLoader {

	public FsmComponentClassLoader(@NotNull final Project project) {
		super(getClasspathFiles(project), FsmComponentClassLoader.class.getClassLoader());
	}

	@NotNull
	private static URL[] getClasspathFiles(@NotNull final Project project) {
		// Get project jar
		final Jar projectJarTask = (Jar) project.getTasks().getByName(JavaPlugin.JAR_TASK_NAME);
		final File projectJar = projectJarTask.getArchiveFile().get().getAsFile();

		// Get runtime and compile classpath jars
		final Configuration runtimeClasspathConfiguration = project.getConfigurations().getByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME);
		final List<File> runtimeClasspathJars = new ArrayList<>(runtimeClasspathConfiguration.getFiles());

		final Configuration compileClasspathConfiguration = project.getConfigurations().getByName(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME);
		final List<File> compileClasspathJars = new ArrayList<>(compileClasspathConfiguration.getFiles());

		// Combine and get URLs
		final List<File> jars = new ArrayList<>();
		jars.addAll(compileClasspathJars);
		jars.addAll(runtimeClasspathJars);
		jars.add(projectJar);
		return jars.stream()
				.map(FsmComponentClassLoader::getUrlFromJarFile)
				.toArray(URL[]::new);
	}

	@NotNull
	private static URL getUrlFromJarFile(@NotNull final File file) {
		try {
			return file.toURI().toURL();
		} catch (final MalformedURLException e) {
			throw new GradleException("Invalid URL for dependency " + file.getName() + ", URI: " + file.toURI(), e);
		}
	}
}
