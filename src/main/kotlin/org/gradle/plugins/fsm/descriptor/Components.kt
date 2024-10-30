package org.gradle.plugins.fsm.descriptor

import com.espirit.moddev.components.annotations.*
import de.espirit.firstspirit.client.access.editor.ValueEngineerFactory
import de.espirit.firstspirit.generate.FilenameFactory
import de.espirit.firstspirit.generate.UrlCreatorSpecification
import de.espirit.firstspirit.module.Configuration
import de.espirit.firstspirit.module.GadgetFactory
import de.espirit.firstspirit.module.GadgetSpecification
import de.espirit.firstspirit.scheduling.ScheduleTaskFormFactory
import io.github.classgraph.ClassInfo
import org.gradle.api.Project
import org.redundent.kotlin.xml.Node
import org.redundent.kotlin.xml.PrintOptions
import org.redundent.kotlin.xml.xml
import kotlin.reflect.KClass

class Components(private val project: Project, private val scanResult: ComponentScan) {

    lateinit var webXmlPaths: List<String>
    val node: Node

    init {
        node = xml("components") {
            components(PublicComponent::class, ::nodesForPublicComponent, scanResult).forEach(this::addElement)
            components(ScheduleTaskComponent::class, ::nodesForScheduleTaskComponent, scanResult).forEach(this::addElement)
            components(GadgetComponent::class, ::nodesForGadgetComponent, scanResult).forEach(this::addElement)
            components(UrlFactoryComponent::class, ::nodesForUrlFactoryComponent, scanResult).forEach(this::addElement)
            components(ServiceComponent::class, ::nodesForServiceComponent, scanResult).forEach(this::addElement)
            components(WebServerComponent::class, ::nodesForWebServerComponent, scanResult).forEach(this::addElement)
            ProjectAppComponents(project, scanResult).nodes.forEach(this::addElement)
            LibraryComponents(project).nodes.forEach(this::addElement)

            val webAppComponents = WebAppComponents(project, scanResult)
            webAppComponents.nodes.forEach(this::addElement)
            webXmlPaths = webAppComponents.webXmlPaths
        }
    }

    override fun toString(): String {
        return node.toString(PRINT_OPTIONS)
    }

    fun innerComponentsToString(): String {
        return node.filter { true }.joinToString("\n\n") { it.toString(PRINT_OPTIONS) }
    }

    private fun components(type: KClass<out Annotation>, transform: (ClassInfo) -> List<Node>, scanResult: ComponentScan): List<Node> {
        return scanResult
            .getClassesWithAnnotation(type)
            .flatMap(transform)
    }

    @Suppress("DuplicatedCode") // No refactoring possible because of incompatible annotations
    private fun nodesForPublicComponent(publicComponent: ClassInfo): List<Node> {
        return publicComponent.annotationInfo
            .filter { it.isClass(PublicComponent::class) }
            .map { annotation ->
                xml("public") {
                    "name" { -annotation.getString("name") }
                    "displayname" { -annotation.getString("displayName")  }
                    "description" { -annotation.getString("description") }
                    "class" { -publicComponent.name }
                    annotation.getClassNameOrNull("configurable", Configuration::class)?.let { "configurable" { -it } }
                    if (annotation.getString("hidden").toBoolean()) {
                        "hidden" { -"true" }
                    }
                }
            }
    }

    private fun nodesForScheduleTaskComponent(scheduleTaskComponent: ClassInfo): List<Node> {
        return scheduleTaskComponent.annotationInfo
            .filter { it.isClass(ScheduleTaskComponent::class) }
            .map { annotation ->
                xml("public") {
                    "name" { -annotation.getString("taskName") }
                    annotation.getStringOrNull("displayName", "")?.let { "displayname" { -it } }
                    "description" { -annotation.getString("description") }
                    "class" { -"de.espirit.firstspirit.module.ScheduleTaskSpecification" }
                    "configuration" {
                        "application" { -scheduleTaskComponent.name }
                        annotation.getClassNameOrNull("formClass", ScheduleTaskFormFactory::class)?.let { "form" { -it } }
                    }
                    annotation.getClassNameOrNull("configurable", Configuration::class)?.let { "configurable" { -it } }
                }
            }
    }

    private fun nodesForGadgetComponent(gadgetComponent: ClassInfo): List<Node> {
        return gadgetComponent.annotationInfo
            .filter { it.isClass(GadgetComponent::class) }
            .map { annotation ->
                xml("public") {
                    "name" { -annotation.getString("name") }
                    "description" { -annotation.getString("description") }
                    "class" { -GadgetSpecification::class.java.name }
                    "configuration" {
                        "gom" { -gadgetComponent.name }

                        annotation.getClassNames("factories")
                                .filter { it.name != GadgetFactory::class.qualifiedName }
                                .forEach { "factory" { -it.name } }

                        annotation.getClassNameOrNull("valueEngineerFactory", ValueEngineerFactory::class)?.let { "value" { -it } }

                        val scopes = annotation.getEnumValues("scopes")
                        if (scopes.any { it.valueName == "UNRESTRICTED" }) {
                            "scope" { attribute("unrestricted", "yes") }
                        } else {
                            "scope" {
                                scopes.forEach { attribute(it.valueName.lowercase(), "yes") }
                            }
                        }
                    }

                    annotation.getClassNameOrNull("configurable", Configuration::class)?.let { "configurable" { -it } }
                }
            }
    }

    private fun nodesForUrlFactoryComponent(urlFactoryComponent: ClassInfo): List<Node> {
        return urlFactoryComponent.annotationInfo
            .filter { it.isClass(UrlFactoryComponent::class) }
            .map { annotation ->
                xml("public") {
                    "name" { -annotation.getString("name") }
                    "displayname" { -annotation.getString("displayName") }
                    "description" { -annotation.getString("description") }
                    "class" { -UrlCreatorSpecification::class.java.name }
                    "configuration" {
                        "UrlFactory" { -urlFactoryComponent.name }
                        "UseRegistry" { -annotation.getString("useRegistry") }
                        annotation.getClassNameOrNull("filenameFactory", FilenameFactory::class)?.let { "FilenameFactory" { -it } }
                        annotation.getAnnotationValues("parameters").forEach {
                            (it.getString("name")) { -it.getString("value") }
                        }
                    }
                }
            }
    }

    @Suppress("DuplicatedCode") // No refactoring possible because of incompatible annotations
    private fun nodesForServiceComponent(serviceComponent: ClassInfo): List<Node> {
        return serviceComponent.annotationInfo
            .filter { it.isClass(ServiceComponent::class) }
            .map { annotation ->
                xml("service") {
                    "name" { -annotation.getString("name") }
                    "displayname" { -annotation.getString("displayName") }
                    "description" { -annotation.getString("description") }
                    "class" { -serviceComponent.name }
                    annotation.getClassNameOrNull("configurable", Configuration::class)?.let { "configurable" { -it } }
                    if (annotation.getString("hidden").toBoolean()) {
                        "hidden" { -"true" }
                    }
                }
            }
    }


    @Suppress("DuplicatedCode")
    private fun nodesForWebServerComponent(webServerComponent: ClassInfo): List<Node> {
        return webServerComponent.annotationInfo
            .filter { it.isClass(WebServerComponent::class) }
            .map { annotation ->
                xml("web-server") {
                    "name" { -annotation.getString("name") }
                    "displayname" { -annotation.getString("displayName") }
                    "description" { -annotation.getString("description") }
                    "class" { -webServerComponent.name }
                    annotation.getClassNameOrNull("configurable", Configuration::class)?.let { "configurable" { -it } }
                    if (annotation.getString("hidden").toBoolean()) {
                        "hidden" { -"true" }
                    }
                }
            }
    }



    companion object {
        private val PRINT_OPTIONS = PrintOptions(singleLineTextElements = true)
    }

}