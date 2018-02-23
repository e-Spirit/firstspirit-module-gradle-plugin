package org.gradle.plugins.fsm

import com.espirit.moddev.components.annotations.ProjectAppComponent
import com.espirit.moddev.components.annotations.PublicComponent
import com.espirit.moddev.components.annotations.Resource
import com.espirit.moddev.components.annotations.WebAppComponent
import groovy.transform.CompileStatic
import org.gradle.api.Project
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ResolvedArtifact

import java.lang.annotation.Annotation

import static org.gradle.plugins.fsm.FSMPlugin.FS_WEB_COMPILE_CONFIGURATION_NAME
import static org.gradle.plugins.fsm.FSMPlugin.PROVIDED_COMPILE_CONFIGURATION_NAME

class XmlTagAppender {

    static final List<String> PROJECTAPP_BLACKLIST = ["de.espirit.firstspirit.feature.ContentTransportProjectApp"]

    @CompileStatic
    static void appendPublicComponentTags(URLClassLoader cl, List<String> publicComponentClasses, StringBuilder result) {
        def loadedClasses = publicComponentClasses.collect { cl.loadClass(it) }

        appendPublicComponentTagsOfClasses(loadedClasses, result)
    }

    private static appendPublicComponentTagsOfClasses(List<Class<?>> loadedClasses, StringBuilder result) {
        loadedClasses.forEach { publicComponentClass ->

            Arrays.asList(publicComponentClass.annotations)
                .findAll { it instanceof PublicComponent }
                .forEach { annotation ->

                    result.append("""
<public>
    <name>${evaluateAnnotation(annotation, "name")}</name>
    <displayname>${evaluateAnnotation(annotation, "displayName")}</displayname>
    <class>${publicComponentClass.getName().toString()}</class>
</public>""")

                }
        }
    }

    @CompileStatic
    static void appendWebAppTags(Project project, URLClassLoader cl, List<String> webAppClasses, StringBuilder result) {
        List<Class<?>> loadedClasses = webAppClasses
            .collect{ cl.loadClass(it) }

        appendWebAppComponentTagsOfClasses(loadedClasses, project, result)
    }

    private static appendWebAppComponentTagsOfClasses(List<Class<?>> loadedClasses, Project project, result) {
        Set<ResolvedArtifact> webCompileDependencies = project.configurations.getByName(FS_WEB_COMPILE_CONFIGURATION_NAME).getResolvedConfiguration().getResolvedArtifacts()
        Set<ResolvedArtifact> providedCompileDependencies = project.configurations.getByName(PROVIDED_COMPILE_CONFIGURATION_NAME).getResolvedConfiguration().getResolvedArtifacts()

        loadedClasses.forEach { webAppClass ->
            Arrays.asList(webAppClass.annotations)
                .findAll { it instanceof WebAppComponent }
                .forEach { annotation ->

                    StringBuilder webResources = new StringBuilder()
                    addResourceTagsForDependencies(webCompileDependencies, providedCompileDependencies, webResources, "")

                    def webResourcesFromAnnotations = (Resource[]) evaluateAnnotation(annotation, "webResources")
                    result.append("""
<web-app>
    <name>${evaluateAnnotation(annotation, "name")}</name>
    <displayname>${evaluateAnnotation(annotation, "displayName")}</displayname>
    <description>${evaluateAnnotation(annotation, "description")}</description>
    <class>${webAppClass.getName().toString()}</class>
    <configurable>${evaluateAnnotation(annotation, "configurable").getName().toString()}</configurable>
    <web-xml>${evaluateAnnotation(annotation, "webXml").toString()}</web-xml>
    <web-resources>
        <resource>lib/${project.jar.archiveName.toString()}</resource>
        <resource>${evaluateAnnotation(annotation, "webXml").toString()}</resource>
        ${evaluateResources(annotation)}
        ${webResources.toString()}
    </web-resources>
</web-app>
""")
                }
        }
    }

    @CompileStatic
    static void appendProjectAppTags(URLClassLoader cl, List<String> projectAppClasses, StringBuilder result) {
        def loadedClasses = projectAppClasses.findAll { !PROJECTAPP_BLACKLIST.contains(it) }.collect { cl.loadClass(it) }

        appendProjectAppTagsOfClasses(loadedClasses, result)
    }

    static appendProjectAppTagsOfClasses(List<Class<?>> loadedClasses, result) {
        loadedClasses.forEach { projectAppClass ->
            Arrays.asList(projectAppClass.annotations)
                .findAll { it.annotationType() == ProjectAppComponent }
                .forEach { annotation ->
                    result.append("""
<project-app>
    <name>${evaluateAnnotation(annotation, "name")}</name>
    <displayname>${evaluateAnnotation(annotation, "displayName")}</displayname>
    <description>${evaluateAnnotation(annotation, "description")}</description>
    <class>${projectAppClass.getName().toString()}</class>
    <configurable>${evaluateAnnotation(annotation, "configurable").getName().toString()}</configurable>
    <resources>
        ${evaluateResources(annotation)}
    </resources>
</project-app>
""")
            }
        }
    }

    private static Object evaluateAnnotation(Annotation annotation, String methodName) {
        annotation.annotationType().getDeclaredMethod(methodName, null).invoke(annotation, null)
    }

    private static String evaluateResources(final Annotation annotation) {
        final StringBuilder sb = new StringBuilder()

        if (annotation instanceof ProjectAppComponent) {
            annotation.resources().each {
                final String minVersion = it.minVersion().isEmpty() ? "" : " ${it.minVersion()}"
                final String maxVersion = it.maxVersion().isEmpty() ? "" : " ${it.maxVersion()}"

                sb.append("""<resource name="${it.name()}" version="${it.version()}"${minVersion}${maxVersion} scope="${it.scope()}" mode="${it.mode()}">${it.path()}</resource>""")
            }
        } else if (annotation instanceof WebAppComponent) {
            annotation.webResources().each {
                final String minVersion = it.minVersion().isEmpty() ? "" : " ${it.minVersion()}"
                final String maxVersion = it.maxVersion().isEmpty() ? "" : " ${it.maxVersion()}"

                sb.append("""<resource name="${it.name()}" version="${it.version()}"${minVersion}${maxVersion}>${it.path()}</resource>""")
            }
        }

        sb.toString()
    }

    private static void addResourceTagsForDependencies(Set<ResolvedArtifact> dependencies, Set<ResolvedArtifact> providedCompileDependencies, StringBuilder projectResources, String scope) {
        dependencies.
            findAll{ !providedCompileDependencies.contains(it) }
            .forEach {
                ModuleVersionIdentifier dependencyId = it.moduleVersion.id
                projectResources.append(getResourceTagForDependency(dependencyId, it, scope))
            }
    }

    static String getResourceTagForDependency(ModuleVersionIdentifier dependencyId, ResolvedArtifact artifact, String scope) {
        def scopeAttribute = scope == null || scope.isEmpty() ? "" : """ scope="${scope}\""""
        """<resource name="${dependencyId.group}.${dependencyId.name}"$scopeAttribute version="${dependencyId.version}">lib/${dependencyId.name}-${dependencyId.version}.${artifact.extension}</resource>"""
    }

    static String getResourcesTags(ConfigurationContainer configurations) {
        def projectResources = new StringBuilder()
        Set<ResolvedArtifact> compileDependenciesServerScoped = configurations.fsServerCompile.getResolvedConfiguration().getResolvedArtifacts()
        Set<ResolvedArtifact> compileDependenciesModuleScoped = configurations.fsModuleCompile.getResolvedConfiguration().getResolvedArtifacts()
        Set<ResolvedArtifact> providedCompileDependencies = configurations.fsProvidedCompile.getResolvedConfiguration().getResolvedArtifacts()

        addResourceTagsForDependencies(compileDependenciesServerScoped, providedCompileDependencies, projectResources, "server")
        projectResources + "\n"
        addResourceTagsForDependencies(compileDependenciesModuleScoped, providedCompileDependencies, projectResources, "module")
        return projectResources.toString()
    }
}
