package org.gradle.plugins.fsm.descriptor

import com.espirit.moddev.components.annotations.*
import com.espirit.moddev.components.annotations.params.gadget.Scope
import de.espirit.firstspirit.client.access.editor.ValueEngineerFactory
import de.espirit.firstspirit.generate.FilenameFactory
import de.espirit.firstspirit.generate.UrlCreatorSpecification
import de.espirit.firstspirit.module.Configuration
import de.espirit.firstspirit.module.GadgetSpecification
import de.espirit.firstspirit.module.ScheduleTaskSpecification
import de.espirit.firstspirit.scheduling.ScheduleTaskFormFactory
import io.github.classgraph.ScanResult
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.redundent.kotlin.xml.Node
import org.redundent.kotlin.xml.PrintOptions
import org.redundent.kotlin.xml.xml
import kotlin.reflect.KClass

class Components(private val project: Project, private val scanResult: ScanResult, private val classLoader: ClassLoader,
                 private val isolatedModuleXml: Boolean) {

    lateinit var webXmlPaths: List<String>
    val node: Node

    init {
        node = xml("components") {
            components(PublicComponent::class, ::nodesForPublicComponent, scanResult, classLoader).forEach(this::addNode)
            components(ScheduleTaskComponent::class, ::nodesForScheduleTaskComponent, scanResult, classLoader).forEach(this::addNode)
            components(GadgetComponent::class, ::nodesForGadgetComponent, scanResult, classLoader).forEach(this::addNode)
            components(UrlFactoryComponent::class, ::nodesForUrlFactoryComponent, scanResult, classLoader).forEach(this::addNode)
            components(ServiceComponent::class, ::nodesForServiceComponent, scanResult, classLoader).forEach(this::addNode)
            ProjectAppComponents(project, scanResult, classLoader).nodes.forEach(this::addNode)

            val webAppComponents = WebAppComponents(project, scanResult, classLoader, isolatedModuleXml)
            webAppComponents.nodes.forEach(this::addNode)
            webXmlPaths = webAppComponents.webXmlPaths
        }
    }

    override fun toString(): String {
        return node.toString(PRINT_OPTIONS)
    }

    fun innerComponentsToString(): String {
        return node.filter{ true }.joinToString("\n\n") { it.toString(PRINT_OPTIONS) }
    }

    private fun components(
        type: KClass<*>, transform: (Class<*>) -> List<Node>, scanResult: ScanResult,
        classLoader: ClassLoader
    ): List<Node> {
        return scanResult
            .getClassesWithAnnotation(type.java.name).names
            .map(classLoader::loadClass)
            .flatMap(transform)
    }

    @Suppress("DuplicatedCode") // No refactoring possible because of incompatible annotations
    private fun nodesForPublicComponent(publicComponentClass: Class<*>): List<Node> {
        return publicComponentClass.annotations
            .filterIsInstance<PublicComponent>()
            .map { annotation ->
                xml("public") {
                    "name" { -annotation.name }
                    "displayname" { -annotation.displayName }
                    "description" { -annotation.description }
                    "class" { -publicComponentClass.name }
                    if (annotation.configurable != Configuration::class) {
                        "configurable" { -annotation.configurable.java.name }
                    }
                }
            }
    }

    private fun nodesForScheduleTaskComponent(scheduleTaskComponent: Class<*>): List<Node> {
        return scheduleTaskComponent.annotations
            .filterIsInstance<ScheduleTaskComponent>()
            .map { annotation ->
                xml("public") {
                    "name" { -annotation.taskName }
                    if (annotation.displayName.isNotEmpty()) {
                        "displayname" { -annotation.displayName }
                    }
                    "description" { -annotation.description }
                    "class" { -ScheduleTaskSpecification::class.java.name }
                    "configuration" {
                        "application" { -scheduleTaskComponent.name }
                        if (annotation.formClass != ScheduleTaskFormFactory::class) {
                            "form" { -annotation.formClass.java.name }
                        }
                    }
                    if (annotation.configurable != Configuration::class) {
                        "configurable" { -annotation.configurable.java.name }
                    }
                }
            }
    }

    private fun nodesForGadgetComponent(gadgetComponent: Class<*>): List<Node> {
        return gadgetComponent.annotations
            .filterIsInstance<GadgetComponent>()
            .map { annotation ->
                xml("public") {
                    "name" { -annotation.name }
                    "description" { -annotation.description }
                    "class" { -GadgetSpecification::class.java.name }
                    "configuration" {
                        "gom" { -gadgetComponent.name }

                        annotation.factories
                            .filter { it != de.espirit.firstspirit.module.GadgetComponent.GadgetFactory::class }
                            .forEach { "factory" { -it.java.name } }

                        if (annotation.valueEngineerFactory != ValueEngineerFactory::class) {
                            "value" { -annotation.valueEngineerFactory.java.name }
                        }

                        if (annotation.scopes.contains(Scope.UNRESTRICTED)) {
                            "scope" { attribute(Scope.UNRESTRICTED.name.lowercase(), "yes") }
                        } else {
                            "scope" {
                                annotation.scopes.forEach { attribute(it.name.lowercase(), "yes") }
                            }
                        }
                    }

                    if (annotation.configurable != Configuration::class) {
                        "configurable" { -annotation.configurable.java.name }
                    }
                }
            }
    }

    private fun nodesForUrlFactoryComponent(urlFactoryComponent: Class<*>): List<Node> {
        return urlFactoryComponent.annotations
            .filterIsInstance<UrlFactoryComponent>()
            .map { annotation ->
                xml("public") {
                    "name" { -annotation.name }
                    "displayname" { -annotation.displayName }
                    "description" { -annotation.description }
                    "class" { -UrlCreatorSpecification::class.java.name }
                    "configuration" {
                        "UrlFactory" { -urlFactoryComponent.name }
                        "UseRegistry" { -annotation.useRegistry.toString() }
                        if (annotation.filenameFactory != FilenameFactory::class) {
                            "FilenameFactory" { -annotation.filenameFactory.java.name }
                        }
                    }
                }
            }
    }

    @Suppress("DuplicatedCode") // No refactoring possible because of incompatible annotations
    private fun nodesForServiceComponent(serviceComponent: Class<*>): List<Node> {
        return serviceComponent.annotations
            .filterIsInstance<ServiceComponent>()
            .map { annotation ->
                xml("service") {
                    "name" { -annotation.name }
                    "displayname" { -annotation.displayName }
                    "description" { -annotation.description }
                    "class" { -serviceComponent.name }
                    if (annotation.configurable != Configuration::class) {
                        "configurable" { -annotation.configurable.java.name }
                    }
                }
            }
    }

    companion object {
        val LOGGER: Logger = Logging.getLogger(Components::class.java)
        private val PRINT_OPTIONS = PrintOptions(singleLineTextElements = true)
    }

}