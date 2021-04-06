package org.gradle.plugins.fsm;

import com.github.jk1.license.LicenseReportExtension;
import com.github.jk1.license.ModuleData;
import com.github.jk1.license.ProjectData;
import com.github.jk1.license.render.CsvReportRenderer;
import com.github.jk1.license.render.LicenseDataCollector;
import com.github.jk1.license.render.ReportRenderer;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;

/**
 * Drop-in replacement for the {@link CsvReportRenderer}
 */
public class LicenseCsvRenderer implements ReportRenderer {

	private static final String FILENAME = "licenses.csv";
	private static final String HEADER_LINE = "\"artifact\",\"moduleUrl\",\"moduleLicense\",\"moduleLicenseUrl\",\n";

	@Override
	public void render(final ProjectData data) {
		final Project project = data.getProject();
		final LicenseReportExtension licenseReport = project.getExtensions().getByType(LicenseReportExtension.class);
		final Path outputPath = Paths.get(licenseReport.outputDir, FILENAME);
		try (final BufferedWriter bw = Files.newBufferedWriter(outputPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
			bw.write(HEADER_LINE);
			data.getAllDependencies().stream()
					.sorted()
					.forEach(dependency -> writeDependencyLicenseLine(bw, dependency));
		} catch (final IOException ioe) {
			throw new GradleException("Failure building license report", ioe);
		}
	}

	private static void writeDependencyLicenseLine(@NotNull final Writer writer, @NotNull final ModuleData dependency) {
		try {
			final String artifact = dependency.getGroup() + ':' + dependency.getName() + ':' + dependency.getVersion();
			final List<String> moduleInfo = LicenseDataCollector.singleModuleLicenseInfo(dependency);
			final String moduleUrl = moduleInfo.get(0);
			final String moduleLicense = moduleInfo.get(1);
			final String moduleLicenseUrl = moduleInfo.get(2);
			writer.write(quote(artifact) + ',' + quote(moduleUrl) + ',' + quote(moduleLicense) + ',' + quote(moduleLicenseUrl) + ",\n");
		} catch (final IOException ioe) {
			throw new GradleException("Failure writing license line!", ioe);
		}
	}

	@NotNull
	private static String quote(@Nullable final String content) {
		if (content == null || content.trim().isEmpty()) {
			return "";
		}
		return '\"' + content.trim().replaceAll("\"", "\"\"") + '\"';
	}
}
