package org.gradle.plugins.fsm.descriptor

import io.github.classgraph.ClassGraph
import io.github.classgraph.ClassInfoList
import io.github.classgraph.ScanResult
import org.gradle.api.Project
import org.gradle.plugins.fsm.compileDependencies
import java.io.Closeable
import kotlin.reflect.KClass

/**
 * Allows for finding FSM components in the project from their annotations. Wraps a [ScanResult] and provides a
 * Kotlin-centric API for interacting with it.
 *
 * Components are found in all project-internal Jar files that are included with the FSM.
 *
 * Like [ScanResult], this class is [Closeable] and should be closed after using it.
 *
 * @see ClassGraph
 */
class ComponentScan(private val project: Project): Closeable {

    private val scanResult: ScanResult = createClassGraph().scan()

    override fun close() {
        scanResult.close()
    }

    fun getClassesImplementing(interfaceClass: KClass<*>): ClassInfoList {
        return scanResult.getClassesImplementing(interfaceClass.java)
    }

    fun getClassesWithAnnotation(annotationClass: KClass<out Annotation>): ClassInfoList {
        return scanResult.getClassesWithAnnotation(annotationClass.java)
    }

    private fun createClassGraph(): ClassGraph {
        val jarFiles = project.compileDependencies().map { it.buildJar() }
        // Must include annotations dependency to get default values for annotations
        val annotationsDependency = project.configurations.getByName("fsmAnnotations").singleFile

        return ClassGraph()
                .enableClassInfo()
                .enableAnnotationInfo()
                .overrideClasspath(jarFiles + annotationsDependency)
    }

}