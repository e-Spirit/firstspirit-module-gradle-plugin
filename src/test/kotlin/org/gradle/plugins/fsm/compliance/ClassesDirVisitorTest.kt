package org.gradle.plugins.fsm.compliance

import com.crownpeak.plugins.fsm.compliance.ClassesDirVisitor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class ClassesDirVisitorTest {

    @Test
    fun findsJavaCompiledClassesDir(@TempDir root: Path) {
        val javaClassesDir = root.resolve("build/classes/java")
        Files.createDirectories(javaClassesDir.resolve("main"))

        val result = ClassesDirVisitor.findCompiledClassesDirs(root)
        assertThat(result).containsExactly(javaClassesDir.toUri().toURL())
    }

    @Test
    fun findsSubprojectCompiledClassesDir(@TempDir root: Path) {
        val javaClassesDir = root.resolve("my-subproject/build/classes/java")
        Files.createDirectories(javaClassesDir.resolve("main"))

        val result = ClassesDirVisitor.findCompiledClassesDirs(root)
        assertThat(result).containsExactly(javaClassesDir.toUri().toURL())
    }

    @Test
    fun findsKotlinCompiledClassesDir(@TempDir root: Path) {
        val kotlinClassesDir = root.resolve("build/classes/kotlin")
        Files.createDirectories(kotlinClassesDir.resolve("main"))

        val result = ClassesDirVisitor.findCompiledClassesDirs(root)
        assertThat(result).containsExactly(kotlinClassesDir.toUri().toURL())
    }

    @Test
    fun findsBothJavaAndKotlinCompiledClassesDirs(@TempDir root: Path) {
        val javaClassesDir = root.resolve("build/classes/java")
        Files.createDirectories(javaClassesDir.resolve("main"))
        val kotlinClassesDir = root.resolve("build/classes/kotlin")
        Files.createDirectories(kotlinClassesDir.resolve("main"))

        val result = ClassesDirVisitor.findCompiledClassesDirs(root)
        assertThat(result).containsExactlyInAnyOrder(
            javaClassesDir.toUri().toURL(),
            kotlinClassesDir.toUri().toURL()
        )
    }

    @Test
    fun ignoresUnrelatedDirectories(@TempDir root: Path) {
        Files.createDirectories(root.resolve("src/main/java"))
        Files.createDirectories(root.resolve("src/main/kotlin"))
        Files.createDirectories(root.resolve("build/resources/main"))

        val result = ClassesDirVisitor.findCompiledClassesDirs(root)
        assertThat(result).isEmpty()
    }
}