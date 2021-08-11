package org.gradle.plugins.fsm.descriptor

import de.espirit.firstspirit.module.Configuration
import de.espirit.firstspirit.module.Module
import io.github.classgraph.ScanResult
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.redundent.kotlin.xml.Node
import org.redundent.kotlin.xml.PrintOptions
import org.redundent.kotlin.xml.xml

class ModuleComponent(scanResult: ScanResult, private val classLoader: ClassLoader) {

    val nodes: List<Node>

    init {
        val moduleAnnotatedClasses = scanResult
            .getClassesWithAnnotation(com.espirit.moddev.components.annotations.ModuleComponent::class.java.name).names

        val moduleImplClasses = scanResult.getClassesImplementing(Module::class.java.name).names

        val moduleClass = moduleClass(moduleAnnotatedClasses, moduleImplClasses)
        if (moduleClass != null) {
            val annotation = moduleClass.annotations
                .filterIsInstance(com.espirit.moddev.components.annotations.ModuleComponent::class.java)
                .first()

            nodes = mutableListOf()

            nodes.add(xml("class") {
                -moduleClass.name
            })
            if (annotation.configurable != Configuration::class) {
                nodes.add(xml("configurable") {
                    -annotation.configurable.java.name
                })
            }
        } else {
            nodes = emptyList()
        }
    }

    override fun toString(): String {
        return nodes.joinToString("\n") { it.toString(PrintOptions(singleLineTextElements = true)) }
    }

    private fun moduleClass(annotated: List<String>, impl: List<String>): Class<*>? {
        val multipleModuleImplementations = impl.size > 1

        if (multipleModuleImplementations) {
            throw IllegalStateException(
                """The following classes implementing ${Module::class.java.name} were found in your project:
                   $impl
                   You cannot have more than one class implementing the module interface in your project.""")
        }

        val noModuleAnnotatedClasses = annotated.isEmpty()
        val singleModuleAnnotatedClass = annotated.size == 1

        val singleModuleImplementation = impl.size == 1

        if (noModuleAnnotatedClasses) {
            LOGGER.info("No class with an @ModuleComponent annotation could be found in your project.")
            if (singleModuleImplementation) {
                LOGGER.info("Looks like you forgot to add the @ModuleComponent annotation to " + impl[0])
            }
        } else if (singleModuleAnnotatedClass) {
            return classLoader.loadClass(annotated[0])
        } else {
            throw IllegalStateException(
                """The following classes annotated with @ModuleComponent were found in your project:
                   $annotated
                   You cannot have more than one class annotated with @ModuleComponent in your project.""")
        }

        return null
    }

    companion object {
        val LOGGER: Logger = Logging.getLogger(ModuleComponent::class.java)
    }

}