package org.gradle.plugins.fsm.descriptor

import io.github.classgraph.ClassGraph
import io.github.classgraph.ClassInfoList
import io.github.classgraph.ScanResult
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.plugins.JavaPlugin
import org.gradle.jvm.tasks.Jar
import org.gradle.plugins.fsm.configurations.FSMConfigurationsPlugin
import java.io.Closeable
import java.io.File
import kotlin.reflect.KClass

/**
 * Allows for finding FSM components in the project from their annotations. Wraps a [ScanResult] containing the
 * compile classpath of the project so that all required class info is present, but will only return project-internal types.
 * The scan works by the following rules:
 *
 * - Annotations are found in the FSM project and all Gradle subprojects included as resources in the `module-isolated.xml`.
 *   In practice, these are all subprojects for which there is a `fsModuleCompile` or `fsServerCompile` dependency in the Gradle build.
 * - For included projects, the project output is included in the class scan (produced by the [`jar`](Jar) task), as well as the entire `compileClasspath`.
 *   This ensures that inheritance information is captured properly.
 *
 * Like [ScanResult], this class is [Closeable] and should be closed after using it.
 *
 * @see ClassGraph
 */
class ComponentScan(private val project: Project) : Closeable {

    private val scanResult: ScanResult = createClassGraph().scan()
    private val projectClasspath: List<File> = determineProjectClasspath()

    override fun close() {
        scanResult.close()
    }

    fun getClassesImplementing(interfaceClass: KClass<*>): ClassInfoList {
        return scanResult.getClassesImplementing(interfaceClass.java).filter {
            projectClasspath.contains(it.classpathElementFile)
        }
    }

    fun getClassesWithAnnotation(annotationClass: KClass<out Annotation>): ClassInfoList {
        return scanResult.getClassesWithAnnotation(annotationClass.java).filter {
            projectClasspath.contains(it.classpathElementFile)
        }
    }

    private fun createClassGraph(): ClassGraph {
        val classpath = allProjects().flatMap {
            val jarTask = it.tasks.getByName(JavaPlugin.JAR_TASK_NAME) as Jar
            val projectClasspath = setOf(jarTask.archiveFile.get().asFile)
            val compileClasspath = it.configurations.getByName("compileClasspath").files
            projectClasspath + compileClasspath
        }.toSet()

        return ClassGraph()
                .enableClassInfo()
                .enableAnnotationInfo()
                .overrideClasspath(classpath)
    }

    private fun determineProjectClasspath(): List<File> {
        return allProjects()
                .map { it.tasks.getByName(JavaPlugin.JAR_TASK_NAME) as Jar }
                .map { it.archiveFile.get().asFile }
    }

    private fun allProjects(): Set<Project> {
        val projectDependencies = FS_CONFIGURATIONS
                .map { project.configurations.getByName(it) }
                .flatMap { it.allDependencies.withType(ProjectDependency::class.java) }
                .map { it.dependencyProject }
                .filter { it.plugins.hasPlugin(JavaPlugin::class.java) }
                .toSet()
        return projectDependencies + project
    }

    companion object {
        private val FS_CONFIGURATIONS = setOf(
                FSMConfigurationsPlugin.FS_MODULE_COMPILE_CONFIGURATION_NAME,
                FSMConfigurationsPlugin.FS_SERVER_COMPILE_CONFIGURATION_NAME
        )
    }

}