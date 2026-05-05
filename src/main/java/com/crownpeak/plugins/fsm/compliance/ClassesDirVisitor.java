package com.crownpeak.plugins.fsm.compliance;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * {@link java.nio.file.FileVisitor} implementation which finds all compiled classes dirs in a given directory tree.
 * A directory is a compiled classes dir exactly when its filepath ends with "./classes/java" or "./classes/kotlin".
 * <p/>
 * Use {@link ClassesDirVisitor#findCompiledClassesDirs(Path) to obtain all classes dirs in a given root directory.
 */
public class ClassesDirVisitor extends SimpleFileVisitor<Path> {

	private final List<URL> classesDirs = new ArrayList<>();

	@Override
	public @NotNull FileVisitResult preVisitDirectory(final @NotNull Path dir, final @NotNull BasicFileAttributes attrs) throws IOException {
		if (isCompiledClassesDir(dir)) {
			classesDirs.add(dir.toUri().toURL());
		}
		return super.preVisitDirectory(dir, attrs);
	}

	/**
	 * Finds all compiled classes dirs in a given directory.
	 *
	 * @param root The root directory to search for compiled classes dirs
	 * @return An unmodifiable list of classes dirs as {@link URL} instances. Never null.
	 */
	public static @NotNull List<URL> findCompiledClassesDirs(final @NotNull Path root) throws IOException {
		final var fileVisitor = new ClassesDirVisitor();
		Files.walkFileTree(root, fileVisitor);
		return fileVisitor.getClassesDirs();
	}

	private static boolean isCompiledClassesDir(final @NotNull Path dir) {
		final var name = dir.getFileName().toString();
		if (!name.equals("java") && !name.equals("kotlin")) {
			return false;
		}
		final var parent = dir.getParent();
		return parent != null && parent.getFileName().toString().equals("classes");
	}

	/**
	 * Retrieves the compiled classes dirs collected by this visitor.
	 *
	 * @return An unmodifiable list of classes dirs as {@link URL} instances. Never null.
	 */
	public @NotNull List<URL> getClassesDirs() {
		return Collections.unmodifiableList(classesDirs);
	}
}
