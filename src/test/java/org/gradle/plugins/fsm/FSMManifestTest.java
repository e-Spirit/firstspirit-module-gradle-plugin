package org.gradle.plugins.fsm;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.assertj.core.api.Assertions.assertThat;

public class FSMManifestTest {

	private static final String BUILD_TOOL = "Gradle " + System.getProperty("gradle.version");
	private static final String BUILD_JDK = System.getProperty("java.runtime.version") + " (" + System.getProperty("java.vendor") + ')';

	private Path testDir;

	@BeforeEach
	public void setUp(@TempDir final Path tempDir) {
		testDir = tempDir;
	}

	@Test
	public void testManifest() throws Exception {
		// Copy test project files from resources folder to temp dir
		final URL resourcesUrl = FSMManifestTest.class.getClassLoader().getResource("manifest");
		final Path resourcesPath = Paths.get(resourcesUrl.toURI());
		Files.walkFileTree(resourcesPath, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
				final Path targetPath = testDir.resolve(resourcesPath.relativize(file));
				Files.createDirectories(targetPath.getParent());
				Files.copy(file, targetPath);
				return FileVisitResult.CONTINUE;
			}
		});

		// Execute a gradle build
		final BuildResult result = GradleRunner.create()
				.withProjectDir(testDir.toFile())
				.withArguments(FSMPlugin.FSM_TASK_NAME)
				.withPluginClasspath()
				.build();
		assertThat(result.task(":" + FSMPlugin.FSM_TASK_NAME).getOutcome()).isEqualTo(TaskOutcome.SUCCESS);

		// Now examine the manifests of the resulting ZIP files
		final File fsmFile = testDir.resolve("build/fsm/test-manifest-2.7.5.fsm").toFile();
		assertThat(fsmFile).exists();

		try (final ZipFile zipFile = new ZipFile(fsmFile)) {
			// Get all manifests

			// Get manifest located in FSM's META-INF/ directory...
			final ZipEntry fsmManifestEntry = zipFile.getEntry("META-INF/MANIFEST.MF");
			final Manifest fsmManifest;
			try (final InputStream is = zipFile.getInputStream(fsmManifestEntry)) {
				fsmManifest = new Manifest(is);
			}

			// Get FSMs located in JAR libs
			final Manifest projectManifest = getManifestFromJar(zipFile, "lib/test-manifest-2.7.5.jar");
			final Manifest subprojectManifest = getManifestFromJar(zipFile, "lib/subproject-3.0.0.jar");

			// Finally, verify manifests for...
			// ... the FSM File
			{
				final Map<Attributes.Name, String> attributes = new HashMap<>();
				attributes.put(new Attributes.Name("Created-By"), "FirstSpirit Module Gradle Plugin " + System.getProperty("version"));
				attributes.put(new Attributes.Name("Build-Tool"), BUILD_TOOL);
				attributes.put(new Attributes.Name("Build-Jdk"), "Custom-Jdk"); 	// Overridden in build.gradle
				attributes.put(new Attributes.Name("Custom-Key"), "Custom-Value"); 	// Set in build.gradle
				assertThat(fsmManifest.getMainAttributes()).containsAllEntriesOf(attributes);
			}

			// ... the main Jar
			{
				final Map<Attributes.Name, String> attributes = new HashMap<>();
				attributes.put(new Attributes.Name("Created-By"), BUILD_TOOL);
				attributes.put(new Attributes.Name("Build-Jdk"), BUILD_JDK);
				assertThat(projectManifest.getMainAttributes()).containsAllEntriesOf(attributes);
			}

			// ... and the JAR for the subproject
			{
				final Map<Attributes.Name, String> attributes = new HashMap<>();
				attributes.put(new Attributes.Name("Created-By"), BUILD_TOOL);
				attributes.put(new Attributes.Name("Build-Jdk"), "Custom-Jdk"); 	// Overridden in build.gradle
				attributes.put(new Attributes.Name("Custom-Key"), "Custom-Value"); 	// Set in build.gradle
				assertThat(subprojectManifest.getMainAttributes()).containsAllEntriesOf(attributes);
			}
		}
	}

	/**
	 * Reads the META-INF/MANIFEST.MF file from a jar file located in the given zip file.
	 *
	 * @param zipFile      The {@link ZipFile} to search
	 * @param jarEntryName The path to the jar inside zip-file
	 * @return The {@link Manifest} of the jar file
	 * @throws IllegalStateException If the jar file could not be found, or no META-INF/MANIFEST.MF file could be found within it
	 * @throws IOException           if an I/O error occurs
	 */
	@NotNull
	private Manifest getManifestFromJar(@NotNull final ZipFile zipFile, @NotNull final String jarEntryName) throws IOException {
		// Get JAR entry in ZIP
		final ZipEntry jarEntry = zipFile.getEntry(jarEntryName);
		if (jarEntry == null) {
			throw new IllegalStateException(jarEntryName + "could not be found in zip file " + zipFile.getName());
		}

		// Extract JAR file into temp directory
		final Path jarPath = Paths.get(jarEntryName);
		final Path outputPath = testDir.resolve("build").resolve("tmp").resolve("zip").resolve(jarPath);
		Files.createDirectories(outputPath.getParent());
		try (final InputStream is = zipFile.getInputStream(jarEntry)) {
			writeFile(outputPath, is);
		}

		// Open JAR file and get manifest
		try (final JarFile jarFile = new JarFile(outputPath.toFile())) {
			final Manifest manifest = jarFile.getManifest();
			if (manifest == null) {
				throw new IllegalStateException("Manifest is missing in JAR file " + outputPath + '!');
			}
			return manifest;
		}
	}

	/**
	 * Writes a file onto disk. Used for extracting jars from the .fsm archive to open them
	 *
	 * @param outputPath The output path
	 * @param is         The {@link InputStream} to read
	 * @throws IOException If an i/o error occurs
	 */
	private void writeFile(@NotNull final Path outputPath, @NotNull final InputStream is) throws IOException {
		try (final OutputStream os = new FileOutputStream(outputPath.toFile())) {
			byte[] bytes = new byte[1024];
			int read;
			while ((read = is.read(bytes)) != -1) {
				os.write(bytes, 0, read);
			}
		}
	}
}
