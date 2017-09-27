package org.gradle.plugins.fsm

import com.espirit.moddev.components.annotations.ProjectAppComponent
import com.espirit.moddev.components.annotations.PublicComponent
import de.espirit.firstspirit.module.ProjectApp
import de.espirit.firstspirit.module.WebApp
import io.github.lukehutch.fastclasspathscanner.scanner.ScanResult
import org.gradle.api.Project
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.plugins.fsm.tasks.bundling.FSM

import java.lang.annotation.Annotation

class XmlTagAppender {

    //TODO: We could determine classes by scanning access.jar - worth it?
    static final List<String> PROJECTAPP_BLACKLIST = ["de.espirit.firstspirit.feature.ContentTransportProjectApp"]

    public static void appendPublicComponentTags(ScanResult scan, URLClassLoader cl, StringBuilder result) {
        def publicComponentClasses = scan.getNamesOfClassesWithAnnotation(PublicComponent)
        println publicComponentClasses
        publicComponentClasses.forEach {
            def publicComponentClass = cl.loadClass(it)

            Arrays.asList(publicComponentClass.annotations).forEach { annotation ->

                Class<? extends Annotation> type = annotation.annotationType()
                result.append("""
<public>
	<name>${evaluateAnnotation(type, annotation, "name")}</name>
	<class>${publicComponentClass.getName().toString()}</class>
</public>""")

            }
        }
    }

    public static void appendWebAppTags(Project project, ScanResult scan, URLClassLoader cl, StringBuilder result) {
        def webAppClasses = scan.getNamesOfClassesImplementing(WebApp)
        webAppClasses.forEach {
            def webAppClass = cl.loadClass(it)

            Arrays.asList(webAppClass.annotations).forEach { annotation ->

                Set<ResolvedArtifact> webCompileDependencies = project.configurations.fsWebCompile.getResolvedConfiguration().getResolvedArtifacts()
                Set<ResolvedArtifact> providedCompileDependencies = project.configurations.fsProvidedCompile.getResolvedConfiguration().getResolvedArtifacts()
                StringBuilder webResources = new StringBuilder()
                addResourceTagsForDependencies(webCompileDependencies, providedCompileDependencies, webResources, "")

                Class<? extends Annotation> type = annotation.annotationType()
                result.append("""
<web-app>
	<name>${evaluateAnnotation(type, annotation, "name")}</name>
	<displayname>${evaluateAnnotation(type, annotation, "displayName")}</displayname>
	<description>${evaluateAnnotation(type, annotation, "description")}</description>
	<class>${webAppClass.getName().toString()}</class>
	<configurable>${evaluateAnnotation(type, annotation, "configurable").toString()}</configurable>
	<web-xml>${evaluateAnnotation(type, annotation, "webXml").toString()}</web-xml>
	<web-resources>
		<resource>lib/${project.jar.archiveName.toString()}</resource>
		<resource>${evaluateAnnotation(type, annotation, "webXml").toString()}</resource>
		<resource>web/abtesting.tld</resource>
		${evaluateAnnotation(type, annotation, "webResourcesTags").toString()}
		${webResources.toString()}
	</web-resources>
</web-app>
""")
            }
        }
    }

    public static void appendProjectAppTags(ScanResult scan, URLClassLoader cl, StringBuilder result) {
        def projectAppClasses = scan.getNamesOfClassesImplementing(ProjectApp)
        projectAppClasses.forEach {
            if (!PROJECTAPP_BLACKLIST.contains(it)) {
                def projectAppClass = cl.loadClass(it)
                Arrays.asList(projectAppClass.annotations).forEach { annotation ->
                    Class<? extends Annotation> type = annotation.annotationType()
                    if (type == ProjectAppComponent) {
                        result.append("""
<project-app>
    <name>${evaluateAnnotation(type, annotation, "name")}</name>
    <displayname>${evaluateAnnotation(type, annotation, "displayName")}</displayname>
    <description>${evaluateAnnotation(type, annotation, "description")}</description>
    <class>${projectAppClass.getName().toString()}</class>
    <configurable>${evaluateAnnotation(type, annotation, "configurable").toString()}</configurable>
</project-app>
""")
                    }
                }
            }
        }
    }

    private static Object evaluateAnnotation(Class<? extends Annotation> type, Annotation annotation, String methodName) {
        type.getDeclaredMethod(methodName, null).invoke(annotation, null)
    }

    private static void addResourceTagsForDependencies(Set<ResolvedArtifact> dependencies, Set<ResolvedArtifact> providedCompileDependencies, StringBuilder projectResources, String scope) {
        dependencies.forEach {
            if (!providedCompileDependencies.contains(it)) {
                ModuleVersionIdentifier dependencyId = it.moduleVersion.id
                projectResources + getResourceTagForDependency(dependencyId, it, scope)
            }
        }
    }

    static String getResourceTagForDependency(ModuleVersionIdentifier dependencyId, ResolvedArtifact it, String scope) {
        def scopeAttribute = scope == null || scope.isEmpty() ? "" : """scope="${scope}"""
        """<resource name="${dependencyId.group}.${dependencyId.name}" $scopeAttribute version="${dependencyId.version}">lib/${dependencyId.name}-${dependencyId.version}.${it.extension}</resource>"""
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
