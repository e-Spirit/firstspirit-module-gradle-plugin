package org.gradle.plugins.fsm

import com.espirit.moddev.components.annotations.ProjectAppComponent
import com.espirit.moddev.components.annotations.PublicComponent
import com.espirit.moddev.components.annotations.WebAppComponent
import de.espirit.firstspirit.module.Configuration
import de.espirit.firstspirit.module.descriptor.WebAppDescriptor
import de.espirit.firstspirit.server.module.ModuleInfo
import groovy.transform.CompileStatic
import org.gradle.api.Project
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ResolvedArtifact

import java.lang.annotation.Annotation

import static org.gradle.plugins.fsm.FSMPlugin.FS_WEB_COMPILE_CONFIGURATION_NAME
import static org.gradle.plugins.fsm.FSMPlugin.PROVIDED_COMPILE_CONFIGURATION_NAME

class XmlTagAppender {

    static final List<String> PROJECT_APP_BLACKLIST = ["de.espirit.firstspirit.feature.ContentTransportProjectApp"]

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
            (Arrays.asList(webAppClass.annotations)
                    .findAll { it instanceof WebAppComponent } as List<WebAppComponent>)
                    .forEach { annotation ->

                    StringBuilder webResources = new StringBuilder()
                    if (project.file('src/main/files').exists()) {
                        webResources.append("""<resource name="${project.group}:${project.name}-files" """ +
                                            """version="${project.version}">files/</resource>\n""")
                    }
                    addResourceTagsForDependencies(webCompileDependencies, providedCompileDependencies, webResources, "", null)

                    final String scopes = scopes(annotation.scope())
                    final String configurable = annotation.configurable() == Configuration.class ? "" : "<configurable>${annotation.configurable().name}</configurable>"

                    result.append("""
<web-app${scopes}>
    <name>${evaluateAnnotation(annotation, "name")}</name>
    <displayname>${evaluateAnnotation(annotation, "displayName")}</displayname>
    <description>${evaluateAnnotation(annotation, "description")}</description>
    <class>${webAppClass.getName().toString()}</class>
    ${configurable}
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

    private static String scopes(WebAppDescriptor.WebAppScope[] scopes) {
        if (scopes.length == 0) {
            ""
        } else {
            " scopes=\"" + scopes.collect({ scope -> scope.name().toLowerCase(Locale.ROOT)}).join(",") + '"'
        }
    }

    @CompileStatic
    static void appendProjectAppTags(URLClassLoader cl, List<String> projectAppClasses, StringBuilder result) {
        def loadedClasses = projectAppClasses.findAll { !PROJECT_APP_BLACKLIST.contains(it) }.collect { cl.loadClass(it) }

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

    private static void addResourceTagsForDependencies(Set<ResolvedArtifact> dependencies, Set<ResolvedArtifact> providedCompileDependencies, StringBuilder projectResources, String scope, ModuleInfo.Mode mode) {
        dependencies.
            findAll{ !providedCompileDependencies.contains(it) }
            .forEach {
                ModuleVersionIdentifier dependencyId = it.moduleVersion.id
                projectResources.append(getResourceTagForDependency(dependencyId, it, scope, mode))
            }
    }

    static String getResourceTagForDependency(ModuleVersionIdentifier dependencyId, ResolvedArtifact artifact, String scope, ModuleInfo.Mode mode) {
        def scopeAttribute = scope == null || scope.isEmpty() ? "" : """ scope="${scope}\""""
        def modeAttribute = mode == null ? "" : """ mode="${mode.name().toLowerCase(Locale.ROOT)}\""""
        """<resource name="${dependencyId.group}.${dependencyId.name}"$scopeAttribute$modeAttribute """ +
            """version="${dependencyId.version}">lib/${dependencyId.name}-${dependencyId.version}.${artifact.extension}</resource>"""
    }

    static String getResourcesTags(Project project, ModuleInfo.Mode globalResourcesMode) {

        def projectResources = new StringBuilder()
        def modeAttribute = globalResourcesMode == null ? "" : """ mode="${globalResourcesMode.name().toLowerCase(Locale.ROOT)}\""""
        projectResources.append("""<resource name="${project.group}:${project.name}-lib" version="${project.version}" scope="module" """ +
                                """mode="${modeAttribute}">lib/${project.name}-${project.version}.jar</resource>"""
        )
        if (project.file('src/main/files').exists()) {
            projectResources.append("""<resource name="${project.group}:${project.name}-files" """ +
                                    """version="${project.version}">files/</resource>\n""")
        }
        ConfigurationContainer configurations = project.configurations
        Set<ResolvedArtifact> compileDependenciesServerScoped = configurations.fsServerCompile.getResolvedConfiguration().getResolvedArtifacts()
        Set<ResolvedArtifact> compileDependenciesModuleScoped = configurations.fsModuleCompile.getResolvedConfiguration().getResolvedArtifacts()
        Set<ResolvedArtifact> providedCompileDependencies = configurations.fsProvidedCompile.getResolvedConfiguration().getResolvedArtifacts()

        addResourceTagsForDependencies(compileDependenciesServerScoped, providedCompileDependencies, projectResources, "server", globalResourcesMode)
        projectResources + "\n"
        addResourceTagsForDependencies(compileDependenciesModuleScoped, providedCompileDependencies, projectResources, "module", globalResourcesMode)
        return projectResources.toString()
    }
}
