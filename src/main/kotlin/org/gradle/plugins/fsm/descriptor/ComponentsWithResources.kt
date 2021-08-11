package org.gradle.plugins.fsm.descriptor

import groovy.lang.MissingPropertyException
import groovy.text.SimpleTemplateEngine
import org.codehaus.groovy.runtime.DefaultGroovyMethods
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.plugins.JavaPlugin

open class ComponentsWithResources(val project: Project) {

    fun getCompileDependencyForName(nameFromAnnotation: String): ResolvedArtifact? {
        val configuration = project.configurations.getByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME)
        return configuration.resolvedConfiguration.resolvedArtifacts.firstOrNull { dependency ->
            val splitName = dependency.id.componentIdentifier.displayName.split(":")
            val groupId = splitName[0]
            val name = splitName[1]
            nameFromAnnotation == "${groupId}:${name}"
        }
    }

    fun expandVersion(versionFromAnnotation: String, context: Map<String, Any>, nameFromAnnotation: String,
                              componentName: String): String {
        try {
            return expand(versionFromAnnotation, context)
        } catch (e: MissingPropertyException) {
            throw GradleException("No property found for placeholder in version attribute of resource '$nameFromAnnotation' in component ${componentName}.\n" +
                    "Template is '$versionFromAnnotation'.\n" +
                    "Resource not declared as compile dependency in project?\n" +
                    "For project version property, use '\${project.version}'.", e)
        }
    }


    fun expand(template: String, context: Map<String, Any>): String {
        return SimpleTemplateEngine().createTemplate(template).make(context).toString()
    }

    fun getContextForCurrentResource(dependency: ResolvedArtifact?): Map<String, Any> {
        val context = mutableMapOf<String, Any>("project" to project)
        if (dependency != null) {
            DefaultGroovyMethods.getProperties(dependency).forEach {
                if (it.value != null) {
                    context[it.key as String] = it.value as Any
                }
            }
            context["path"] = getPathInFsmForDependency(dependency)
            context["version"] = dependency.moduleVersion.id.version
        }
        return context
    }

    private fun getPathInFsmForDependency(artifact: ResolvedArtifact) =
        "lib/${artifact.name}-${artifact.moduleVersion.id.version}${artifact.classifier ?: ""}.${artifact.extension}"

}