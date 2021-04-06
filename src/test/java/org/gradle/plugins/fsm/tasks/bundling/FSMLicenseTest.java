package org.gradle.plugins.fsm.tasks.bundling;

import org.apache.commons.io.IOUtils;
import org.gradle.plugins.fsm.FSMPlugin;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class FSMLicenseTest {

	private static final String LICENSE_HEADER = "\"artifact\",\"moduleUrl\",\"moduleLicense\",\"moduleLicenseUrl\",\n";
	private Path testDir;
	private Path settingsFile;
	private Path buildFile;

	@BeforeEach
	public void setUp(@TempDir final Path tempDir) throws Exception {
		testDir = tempDir;

		buildFile = Files.createFile(testDir.resolve("build.gradle"));
		settingsFile = Files.createFile(testDir.resolve("settings.gradle"));
	}

	@Test
	public void testFsmWithNoDependencies() throws Exception {
		writeFile(settingsFile, "rootProject.name = 'testFsmNoLicenses'");
		try (final InputStream is = getClass().getClassLoader().getResourceAsStream("licenses/fsmnodependency.gradle")) {
			writeFile(buildFile, IOUtils.toString(is, StandardCharsets.UTF_8));
		}

		// Execute the gradle build
		final BuildResult result = GradleRunner.create()
				.withProjectDir(testDir.toFile())
				.withArguments(FSMPlugin.FSM_TASK_NAME)
				.withPluginClasspath()
				.build();
		assertThat(result.task(":" + FSMPlugin.FSM_TASK_NAME).getOutcome()).isEqualTo(TaskOutcome.SUCCESS);

		// examine result
		final Path fsmFile = testDir.resolve("build/fsm/testFsmNoLicenses-1.0-SNAPSHOT.fsm");
		assertThat(fsmFile.toFile()).exists();
		try (final ZipFile zipFile = new ZipFile(fsmFile.toFile())) {
			final ZipEntry licenseReport = zipFile.getEntry("META-INF/licenses.csv");
			try (final InputStream is = zipFile.getInputStream(licenseReport)) {
				// no licenses
				final String licenses = IOUtils.toString(is, StandardCharsets.UTF_8);
				assertThat(licenses).isEqualToNormalizingNewlines(LICENSE_HEADER);
			}
		}
	}

	@Test
	public void testFsmWithLicenses() throws Exception {
		writeFile(settingsFile, "rootProject.name = 'testFsmNoLicenses'");
		try (final InputStream is = getClass().getClassLoader().getResourceAsStream("licenses/fsmdependency.gradle")) {
			writeFile(buildFile, IOUtils.toString(is, StandardCharsets.UTF_8));
		}

		// Execute the gradle build
		final BuildResult result = GradleRunner.create()
				.withProjectDir(testDir.toFile())
				.withArguments(FSMPlugin.FSM_TASK_NAME)
				.withPluginClasspath()
				.build();
		assertThat(result.task(":" + FSMPlugin.FSM_TASK_NAME).getOutcome()).isEqualTo(TaskOutcome.SUCCESS);

		// get result
		final Path fsmFile = testDir.resolve("build/fsm/testFsmNoLicenses-1.0-SNAPSHOT.fsm");
		assertThat(fsmFile.toFile()).exists();
		try (final ZipFile zipFile = new ZipFile(fsmFile.toFile())) {
			// test license file
			// first, get the expected licenses from a resource file
			String expectedLicenses;
			try (final InputStream is = getClass().getClassLoader().getResourceAsStream("licenses/fsmdependency_licenes.csv")) {
				expectedLicenses = IOUtils.toString(is, StandardCharsets.UTF_8);
			}

			final ZipEntry licenseReport = zipFile.getEntry("META-INF/licenses.csv");
			try (final InputStream is = zipFile.getInputStream(licenseReport)) {
				final String licenses = IOUtils.toString(is, StandardCharsets.UTF_8);
				assertThat(licenses).isEqualToNormalizingNewlines(expectedLicenses);
			}

			// check presence of license files
			assertThat(zipFile.getEntry("META-INF/licenses/jackson-databind-2.10.0.jar/LICENSE.txt")).isNotNull();
			assertThat(zipFile.getEntry("META-INF/licenses/jackson-core-2.10.0.jar/LICENSE.txt")).isNotNull();
			assertThat(zipFile.getEntry("META-INF/licenses/jackson-annotations-2.10.0.jar/LICENSE.txt")).isNotNull();
		}
	}

	private void writeFile(@NotNull final Path file, @NotNull final String text) throws IOException {
		try (final Writer output = Files.newBufferedWriter(file)) {
			output.write(text);
		}
	}
}
