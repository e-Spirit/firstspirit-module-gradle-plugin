package org.gradle.plugins.fsm.descriptor

import groovy.lang.MissingPropertyException
import groovy.text.SimpleTemplateEngine
import org.codehaus.groovy.runtime.DefaultGroovyMethods
import org.gradle.api.GradleException
import org.gradle.api.provider.Provider
import org.gradle.plugins.fsm.tasks.bundling.ResolvedDependencyInfo

open class ComponentsWithResources(val resolvedArtifacts: Provider<Set<ResolvedDependencyInfo>>) {

    protected fun getCompileDependencyForName(nameFromAnnotation: String): ResolvedDependencyInfo? {
        return resolvedArtifacts.get().firstOrNull { dependency ->
            nameFromAnnotation == "${dependency.groupId}:${dependency.moduleName}"
        }
    }

    protected fun expandVersion(versionFromAnnotation: String, context: MutableMap<String, Any>, nameFromAnnotation: String,
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


    protected fun expand(template: String, context: MutableMap<String, Any>): String {
        return SimpleTemplateEngine().createTemplate(template).make(context).toString()
    }

    protected fun getContextForCurrentResource(dependency: ResolvedDependencyInfo?, projectContext: ProjectContext): MutableMap<String, Any> {
        val context = mutableMapOf<String, Any>("project" to projectContext)
        if (dependency != null) {
            DefaultGroovyMethods.getProperties(dependency).forEach {
                if (it.value != null) {
                    context[it.key as String] = it.value as Any
                }
            }
            context["path"] = "lib/${dependency.file.name}"
            context["version"] = dependency.moduleVersion
        }
        return context
    }

}