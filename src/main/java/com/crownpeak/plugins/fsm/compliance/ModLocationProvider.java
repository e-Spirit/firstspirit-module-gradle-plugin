package com.crownpeak.plugins.fsm.compliance;

import com.tngtech.archunit.core.importer.Location;
import com.tngtech.archunit.core.importer.Locations;
import com.tngtech.archunit.junit.LocationProvider;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Set;

class ModLocationProvider implements LocationProvider {

    @Override
    public Set<Location> get(Class<?> testClass) {
        final var classesDirs = new ArrayList<URL>();
        final var fileVisitor = new SimpleFileVisitor<Path>() {

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (dir.getFileName().toString().equals("java")
                        && dir.getParent() != null && dir.getParent().getFileName().toString().equals("classes")
                ) {
                    classesDirs.add(dir.toUri().toURL());
                }

                return super.preVisitDirectory(dir, attrs);
            }

        };

        try {
            Files.walkFileTree(projectRoot(), fileVisitor);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        return Locations.of(classesDirs);
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