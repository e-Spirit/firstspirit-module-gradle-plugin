package org.gradle.plugins.fsm.descriptor

import de.espirit.firstspirit.module.Module
import io.github.classgraph.ScanResult
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.redundent.kotlin.xml.Node
import org.redundent.kotlin.xml.PrintOptions
import org.redundent.kotlin.xml.xml
import com.espirit.moddev.components.annotations.ModuleComponent
import de.espirit.firstspirit.module.Configuration
import io.github.classgraph.ClassInfo

class ModuleComponent(scanResult: ScanResult) {

    val nodes: List<Node>

    init {
        val moduleAnnotatedClasses = scanResult.getClassesWithAnnotation(ModuleComponent::class.qualifiedName)
        val moduleImplClasses = scanResult.getClassesImplementing(Module::class.qualifiedName)

        val moduleClass = moduleClass(moduleAnnotatedClasses, moduleImplClasses)
        if (moduleClass != null) {
            val annotation = moduleClass.annotationInfo.first { it.isClass(ModuleComponent::class) }
            nodes = mutableListOf()
            nodes.add(xml("class") { -moduleClass.name })
            annotation.getClassNameOrNull("configurable", Configuration::class)?.let {
                nodes.add(xml("configurable") { -it })
            }
        } else {
            nodes = emptyList()
        }
    }

    override fun toString(): String {
        return nodes.joinToString("\n") { it.toString(PrintOptions(singleLineTextElements = true)) }
    }

    private fun moduleClass(annotated: List<ClassInfo>, impl: List<ClassInfo>): ClassInfo? {
        val multipleModuleImplementations = impl.size > 1
        if (multipleModuleImplementations) {
            throw IllegalStateException(
                """The following classes implementing ${Module::class.qualifiedName} were found in your project:
                   ${impl.map { it.name }}
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
            return annotated.single()
        } else {
            throw IllegalStateException(
                """The following classes annotated with @ModuleComponent were found in your project:
                   ${annotated.map { it.name }}
                   You cannot have more than one class annotated with @ModuleComponent in your project.""")
        }

        return null
    }

    companion object {
        val LOGGER: Logger = Logging.getLogger(ModuleComponent::class.java)
    }

}