package com.crownpeak.plugins.fsm.compliance;

import com.tngtech.archunit.core.importer.Location;
import com.tngtech.archunit.core.importer.Locations;
import com.tngtech.archunit.junit.LocationProvider;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

class ModLocationProvider implements LocationProvider {

    @Override
    public Set<Location> get(final Class<?> testClass) {
        try {
            return Locations.of(ClassesDirVisitor.findCompiledClassesDirs(projectRoot()));
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private @NotNull Path projectRoot() {
        var currentPath = Paths.get("").toAbsolutePath();
        while (!isRepositoryRoot(currentPath)) {
            if (currentPath.getParent() == null) {
                throw new IllegalStateException("Project root could not be found");
            } else {
                currentPath = currentPath.getParent();
            }
        }

        return currentPath;
    }

    private boolean isRepositoryRoot(final @NotNull Path path) {
        return Files.isRegularFile(path.resolve("settings.gradle"))
                || Files.isRegularFile(path.resolve("settings.gradle.kts"));
    }

}
