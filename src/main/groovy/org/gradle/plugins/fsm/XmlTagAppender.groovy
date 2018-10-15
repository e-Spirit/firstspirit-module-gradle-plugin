package org.gradle.plugins.fsm

import com.espirit.moddev.components.annotations.*
import de.espirit.firstspirit.generate.UrlFactory
import com.espirit.moddev.components.annotations.ProjectAppComponent
import com.espirit.moddev.components.annotations.PublicComponent
import com.espirit.moddev.components.annotations.Resource
import com.espirit.moddev.components.annotations.ServiceComponent
import com.espirit.moddev.components.annotations.ScheduleTaskComponent
import com.espirit.moddev.components.annotations.WebAppComponent
import de.espirit.firstspirit.module.Configuration
import de.espirit.firstspirit.module.ScheduleTaskSpecification
import de.espirit.firstspirit.module.descriptor.WebAppDescriptor
import de.espirit.firstspirit.scheduling.ScheduleTaskFormFactory
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
                    final String configurable = annotation.configurable() == Configuration.class ? "" : "\n            <configurable>${annotation.configurable().name}</configurable>"

                    result.append("""
        <public>
            <name>${evaluateAnnotation(annotation, "name")}</name>
            <displayname>${evaluateAnnotation(annotation, "displayName")}</displayname>
            <class>${publicComponentClass.getName().toString()}</class>${configurable}
        </public>""")

                }
        }
    }


    @CompileStatic
    static void appendScheduleTaskComponentTags(URLClassLoader cl, List<String> scheduleTaskComponentClasses, StringBuilder result) {
        def loadedClasses = scheduleTaskComponentClasses.collect { cl.loadClass(it) }
        appendScheduleTaskComponentTagsOfClasses(loadedClasses, result)
    }

    private static appendScheduleTaskComponentTagsOfClasses(List<Class<?>> loadedClasses, StringBuilder result) {
        loadedClasses.forEach {
            scheduleTaskComponentClass ->
                Arrays.asList(scheduleTaskComponentClass.annotations)
                .findAll { it instanceof ScheduleTaskComponent }
                .forEach { annotation ->
                    def indent = "            "
                    final String configurable = annotation.configurable() == Configuration.class ? "" : "\n" + indent + "<configurable>${annotation.configurable().name}</configurable>"

                    result.append("""
        <public>
            <name>${evaluateAnnotation(annotation, "taskName")}</name>
            <description>${evaluateAnnotation(annotation, "description")}</description>
            <class>${ScheduleTaskSpecification.getName()}</class>
            <configuration>
                <application>${scheduleTaskComponentClass.getName()}</application>""")

                            Object o = evaluateAnnotation(annotation, "formClass")
                            /*
                            * The default interface ScheduleTaskFormFactory should
                            * not be written in the xml so it gets filtered.
                            */
                            if( o != ScheduleTaskFormFactory) {
                                result.append("""
                <form>${o.getName()}</form>""")
                            }
                            result.append("""
            </configuration>$configurable
        </public>""")

                }
        }
    }

    @CompileStatic
    static void appendWebAppTags(Project project, URLClassLoader cl, List<String> webAppClasses, StringBuilder result, boolean appendDefaultMinVersion) {
        List<Class<?>> loadedClasses = webAppClasses
            .collect{ cl.loadClass(it) }

        appendWebAppComponentTagsOfClasses(loadedClasses, project, result, appendDefaultMinVersion)
    }

    private static appendWebAppComponentTagsOfClasses(List<Class<?>> loadedClasses, Project project, result, boolean appendDefaultMinVersion) {
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
                    addResourceTagsForDependencies(webCompileDependencies, providedCompileDependencies, webResources, "", null, appendDefaultMinVersion)

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
        <resource name="${project.group}:${project.name}" version="${project.version}">lib/${project.jar.archiveName.toString()}</resource>
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
                    final String resources = evaluateResources(annotation)
                    final String resourcesTag = resources.isEmpty() ? "" : """<resources>
        ${resources}
    </resources>"""
                    result.append("""
<project-app>
    <name>${evaluateAnnotation(annotation, "name")}</name>
    <displayname>${evaluateAnnotation(annotation, "displayName")}</displayname>
    <description>${evaluateAnnotation(annotation, "description")}</description>
    <class>${projectAppClass.getName().toString()}</class>
    <configurable>${evaluateAnnotation(annotation, "configurable").getName().toString()}</configurable>
    ${resourcesTag}
</project-app>
""")
            }
        }
    }

    @CompileStatic
    static void appendServiceTags(URLClassLoader cl, List<String> serviceClasses, StringBuilder result) {
        def loadedClasses = serviceClasses.collect { cl.loadClass(it) }

        appendServiceTagsOfClasses(loadedClasses, result)
    }

    static appendServiceTagsOfClasses(List<Class<?>> loadedClasses, result) {
        loadedClasses.forEach { serviceClass ->
            Arrays.asList(serviceClass.annotations)
                    .findAll { it.annotationType() == ServiceComponent }
                    .forEach { annotation ->
                result.append("""
<service>
    <name>${evaluateAnnotation(annotation, "name")}</name>
    <displayname>${evaluateAnnotation(annotation, "displayName")}</displayname>
    <description>${evaluateAnnotation(annotation, "description")}</description>
    <class>${serviceClass.getName().toString()}</class>
    <configurable>${evaluateAnnotation(annotation, "configurable").getName().toString()}</configurable>
</service>
""")
            }
        }
    }

    /*
        This method is named appendUrlCreatorTags and not appendUrlFactoryTags because
        the component that is defined for FirstSpirit is a UrlCreator that has a UrlFactory.
        For the given classes, we need classes that implement the UrlFactory interface,
        because this class is used as attribute for the urlFactory tag in the module.xml.
     */
    @CompileStatic
    static void appendUrlCreatorTags(URLClassLoader cl, List<String> urlFactoryClasses, StringBuilder result) {
        def loadedClasses = urlFactoryClasses.collect { cl.loadClass(it) }

        appendUrlCreatorTagsOfClasses(loadedClasses, result)
    }

    static appendUrlCreatorTagsOfClasses(List<Class<? extends UrlFactory>> loadedClasses, result) {
        loadedClasses.forEach { urlFactoryClass ->
            Arrays.asList(urlFactoryClass.annotations)
                    .findAll { it.annotationType() == UrlFactoryComponent }
                    .forEach { annotation ->
                result.append("""
<public>
    <name>${evaluateAnnotation(annotation, "name")}</name>
    <displayname>${evaluateAnnotation(annotation, "displayName")}</displayname>
    <description>${evaluateAnnotation(annotation, "description")}</description>
    <class>de.espirit.firstspirit.generate.UrlCreatorSpecification</class>
    <configuration>
        <UrlFactory>${urlFactoryClass.getName().toString()}</UrlFactory>
        <UseRegistry>${evaluateAnnotation(annotation, "useRegistry")}</UseRegistry>
    </configuration>
</public>
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
                final String minVersion = it.minVersion().isEmpty() ? "" : """ minVersion="${it.minVersion()}\""""
                final String maxVersion = it.maxVersion().isEmpty() ? "" : """ maxVersion="${it.maxVersion()}\""""

                sb.append("""<resource name="${it.name()}" version="${it.version()}"${minVersion}${maxVersion} scope="${it.scope()}" mode="${it.mode()}">${it.path()}</resource>""")
            }
        } else if (annotation instanceof WebAppComponent) {
            annotation.webResources().each {
                final String minVersion = it.minVersion().isEmpty() ? "" : """ minVersion="${it.minVersion()}\""""
                final String maxVersion = it.maxVersion().isEmpty() ? "" : """ maxVersion="${it.maxVersion()}\""""

                sb.append("""<resource name="${it.name()}" version="${it.version()}"${minVersion}${maxVersion}>${it.path()}</resource>""")
            }
        }

        sb.toString()
    }

    private static void addResourceTagsForDependencies(Set<ResolvedArtifact> dependencies, Set<ResolvedArtifact> providedCompileDependencies, StringBuilder projectResources, String scope, ModuleInfo.Mode mode, boolean appendDefaultMinVersion) {
        dependencies.
            findAll{ !providedCompileDependencies.contains(it) }
            .forEach {
                ModuleVersionIdentifier dependencyId = it.moduleVersion.id
                projectResources.append(getResourceTagForDependency(dependencyId, it, scope, mode, appendDefaultMinVersion))
            }
    }

    static String getResourceTagForDependency(ModuleVersionIdentifier dependencyId, ResolvedArtifact artifact, String scope, ModuleInfo.Mode mode, boolean appendDefaultMinVersion) {
        def scopeAttribute = scope == null || scope.isEmpty() ? "" : """ scope="${scope}\""""
        def classifier = artifact.classifier != null && !artifact.classifier.isEmpty() ? "-${artifact.classifier}" : ""
        def modeAttribute = mode == null ? "" : """ mode="${mode.name().toLowerCase(Locale.ROOT)}\""""
        def minVersionAttribute = !appendDefaultMinVersion ? "" : """ minVersion="${dependencyId.version}\""""
        """<resource name="${dependencyId.group}:${dependencyId.name}"$scopeAttribute$modeAttribute version="${dependencyId.version}"$minVersionAttribute>lib/${dependencyId.name}-${dependencyId.version}$classifier.${artifact.extension}</resource>"""
    }

    static String getResourcesTags(Project project, ModuleInfo.Mode globalResourcesMode, boolean appendDefaultMinVersion) {

        def projectResources = new StringBuilder()
        def modeAttribute = globalResourcesMode == null ? "" : """ mode="${globalResourcesMode.name().toLowerCase(Locale.ROOT)}\""""
        projectResources.append("""<resource name="${project.group}:${project.name}" version="${project.version}" scope="module\"""" +
                                """${modeAttribute}>lib/${project.name}-${project.version}.jar</resource>"""
        )
        if (project.file('src/main/files').exists()) {
            projectResources.append("""<resource name="${project.group}:${project.name}-files" version="${project.version}" scope="module\"""" +
                                    """${modeAttribute}>files/</resource>\n""")
        }
        ConfigurationContainer configurations = project.configurations
        Set<ResolvedArtifact> compileDependenciesServerScoped = configurations.fsServerCompile.getResolvedConfiguration().getResolvedArtifacts()
        Set<ResolvedArtifact> compileDependenciesModuleScoped = configurations.fsModuleCompile.getResolvedConfiguration().getResolvedArtifacts()
        Set<ResolvedArtifact> providedCompileDependencies = configurations.fsProvidedCompile.getResolvedConfiguration().getResolvedArtifacts()

        addResourceTagsForDependencies(compileDependenciesServerScoped, providedCompileDependencies, projectResources, "server", globalResourcesMode, appendDefaultMinVersion)
        projectResources + "\n"
        addResourceTagsForDependencies(compileDependenciesModuleScoped, providedCompileDependencies, projectResources, "module", globalResourcesMode, appendDefaultMinVersion)
        return projectResources.toString()
    }
}
