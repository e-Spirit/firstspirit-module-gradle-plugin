package org.gradle.plugins.fsm

import com.espirit.moddev.components.annotations.*
import de.espirit.firstspirit.generate.UrlFactory
import de.espirit.firstspirit.module.Configuration
import de.espirit.firstspirit.module.ProjectApp
import de.espirit.firstspirit.module.ScheduleTaskSpecification
import de.espirit.firstspirit.module.Service
import de.espirit.firstspirit.module.WebApp
import de.espirit.firstspirit.module.descriptor.WebAppDescriptor
import de.espirit.firstspirit.scheduling.ScheduleTaskFormFactory
import de.espirit.firstspirit.server.module.ModuleInfo
import groovy.transform.CompileStatic
import org.gradle.api.Project
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.logging.Logger
import org.gradle.plugins.fsm.tasks.bundling.FSM

import java.lang.annotation.Annotation

import static org.gradle.plugins.fsm.FSMPlugin.FS_WEB_COMPILE_CONFIGURATION_NAME
import static org.gradle.plugins.fsm.FSMPlugin.PROVIDED_COMPILE_CONFIGURATION_NAME

class XmlTagAppender {

    static final List<String> PROJECT_APP_BLACKLIST = ["de.espirit.firstspirit.feature.ContentTransportProjectApp"]

    static final String INDENT_WS_8 = "        "
    static final String INDENT_WS_12 = "            "
    static final String INDENT_WS_16 = "                "

    /*
     * Append components tag to given result StringBuilder
     */
    @CompileStatic
    static void appendComponentsTag(Project project, URLClassLoader classLoader, FSM.ClassScannerResultProvider scan, final boolean appendDefaultMinVersion, final StringBuilder result, boolean isolated) {

        appendPublicComponentTags(classLoader, scan.getNamesOfClassesWithAnnotation(PublicComponent), result)

        appendScheduleTaskComponentTags(classLoader, scan.getNamesOfClassesWithAnnotation(ScheduleTaskComponent), result)

        appendUrlCreatorTags(classLoader, scan.getNamesOfClassesWithAnnotation(UrlFactoryComponent), result)

        appendServiceTags(classLoader, scan.getNamesOfClassesImplementing(Service), result)

        appendProjectAppTags(classLoader, scan.getNamesOfClassesImplementing(ProjectApp), result)

        appendWebAppTags(project, classLoader, scan.getNamesOfClassesImplementing(WebApp), result, appendDefaultMinVersion, project.plugins.getPlugin(FSMPlugin.class).dependencyConfigurations, isolated)
    }


    @CompileStatic
    static String getFsmDependencyTags(Project project) {
        def result = new StringBuilder()

        FSMPluginExtension fsmPlugin = project.getExtensions().getByType(FSMPluginExtension)
        Collection<String> fsmDependencies = fsmPlugin.getFsmDependencies()

        for (String name : fsmDependencies) {
            result.append("\n").append(INDENT_WS_8).append("<dependency>").append(name).append("</dependency>")
        }
        return result
    }


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

// keep indent (2 tabs / 8 whitespaces) --> 3. level <module><components><public>
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
                    def indent = INDENT_WS_12
                    final String configurable = annotation.configurable() == Configuration.class ? "" : "\n" + indent + "<configurable>${annotation.configurable().name}</configurable>"

// keep indent (2 tabs / 8 whitespaces) --> 3. level <module><components><public>
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
    static void appendWebAppTags(Project project, URLClassLoader cl, List<String> webAppClasses, StringBuilder result, boolean appendDefaultMinVersion, Set<FSMPlugin.MinMaxVersion> minMaxVersionConfigurations = new HashSet<>(), boolean isolated) {
        List<Class<?>> loadedClasses = webAppClasses
            .collect{ cl.loadClass(it) }

        appendWebAppComponentTagsOfClasses(loadedClasses, project, result, appendDefaultMinVersion, minMaxVersionConfigurations, isolated)
    }

    private static appendWebAppComponentTagsOfClasses(List<Class<?>> loadedClasses, Project project, result, boolean appendDefaultMinVersion, Set<FSMPlugin.MinMaxVersion> minMaxVersionConfigurations = new HashSet<>(), boolean isolated) {
        //TODO: check if dependency needs to be skipped here?
/*        Set<ResolvedArtifact> compileDependenciesServerScoped = configurations.fsServerCompile.getResolvedConfiguration().getResolvedArtifacts()
        Set<ResolvedArtifact> uncleanedDependenciesModuleScoped = configurations.fsModuleCompile.getResolvedConfiguration().getResolvedArtifacts()
        Set<ResolvedArtifact> cleanedCompileDependenciesModuleScoped = uncleanedDependenciesModuleScoped - compileDependenciesServerScoped
        logIgnoredModuleScopeDependencies(logger, uncleanedDependenciesModuleScoped, cleanedCompileDependenciesModuleScoped)
        Set<ResolvedArtifact> providedCompileDependencies = configurations.fsProvidedCompile.getResolvedConfiguration().getResolvedArtifacts()

        if(skipIsolationOnlyDependencies) {
            Set<ResolvedArtifact> skippedInLegacyDependencies = configurations.skippedInLegacy.getResolvedConfiguration().getResolvedArtifacts()
            compileDependenciesServerScoped.removeAll(skippedInLegacyDependencies)
            cleanedCompileDependenciesModuleScoped.removeAll(skippedInLegacyDependencies)
            providedCompileDependencies.removeAll(skippedInLegacyDependencies)
        }*/


        Set<ResolvedArtifact> webCompileDependencies = project.configurations.getByName(FS_WEB_COMPILE_CONFIGURATION_NAME).getResolvedConfiguration().getResolvedArtifacts()
        Set<ResolvedArtifact> providedCompileDependencies = project.configurations.getByName(PROVIDED_COMPILE_CONFIGURATION_NAME).getResolvedConfiguration().getResolvedArtifacts()
        if(!isolated){
            Set<ResolvedArtifact> skippedInLegacyDependencies = project.configurations.skippedInLegacy.getResolvedConfiguration().getResolvedArtifacts()
            webCompileDependencies.removeAll(skippedInLegacyDependencies)
            providedCompileDependencies.removeAll(skippedInLegacyDependencies)
        }
        def webResourceIndent = INDENT_WS_16

        loadedClasses.forEach { webAppClass ->
            (Arrays.asList(webAppClass.annotations)
                    .findAll { it instanceof WebAppComponent } as List<WebAppComponent>)
                    .forEach { annotation ->

                    StringBuilder webResources = new StringBuilder()

                    if (project.file('src/main/files').exists()) {
                        webResources.append("""${webResourceIndent}<resource name="${project.group}:${project.name}-files" """ +
                                            """version="${project.version}">files/</resource>\n""")
                    }
                    addResourceTagsForDependencies(webResourceIndent, webCompileDependencies, providedCompileDependencies, webResources, "", null, appendDefaultMinVersion, minMaxVersionConfigurations)

                    final String scopes = scopes(annotation.scope())
                    def indent = INDENT_WS_12
                    final String configurable = annotation.configurable() == Configuration.class ? "" : "\n" + indent + "<configurable>${annotation.configurable().name}</configurable>"

// keep indent (2 tabs / 8 whitespaces) --> 3. level <module><components><web-app>
                    result.append("""
        <web-app${scopes}>
            <name>${evaluateAnnotation(annotation, "name")}</name>
            <displayname>${evaluateAnnotation(annotation, "displayName")}</displayname>
            <description>${evaluateAnnotation(annotation, "description")}</description>
            <class>${webAppClass.getName().toString()}</class>${configurable}
            <web-xml>${evaluateAnnotation(annotation, "webXml").toString()}</web-xml>
            <web-resources>
                <resource name="${project.group}:${project.name}" version="${project.version}">lib/${project.jar.archiveName.toString()}</resource>
                <resource>${evaluateAnnotation(annotation, "webXml").toString()}</resource>${evaluateResources(annotation, webResourceIndent)}${webResources.toString()}
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
        def resourceIndent = INDENT_WS_16
        loadedClasses.forEach { projectAppClass ->
            Arrays.asList(projectAppClass.annotations)
                .findAll { it.annotationType() == ProjectAppComponent }
                .forEach { annotation ->
                    final String resources = evaluateResources(annotation, resourceIndent)
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

    private static String evaluateResources(final Annotation annotation, final String indent) {
        final StringBuilder sb = new StringBuilder()

        if (annotation instanceof ProjectAppComponent) {
            annotation.resources().each {
                final String minVersion = it.minVersion().isEmpty() ? "" : """ minVersion="${it.minVersion()}\""""
                final String maxVersion = it.maxVersion().isEmpty() ? "" : """ maxVersion="${it.maxVersion()}\""""

                sb.append("""${indent}<resource name="${it.name()}" version="${it.version()}"${minVersion}${maxVersion} scope="${it.scope()}" mode="${it.mode()}">${it.path()}</resource>""")
            }
        } else if (annotation instanceof WebAppComponent) {
            def resources = annotation.webResources()
            def count = resources.length
            resources.eachWithIndex { WebResource it, int index ->
                final String start = (index == 0) ? "\n" : ""
                final String minVersion = it.minVersion().isEmpty() ? "" : """ minVersion="${it.minVersion()}\""""
                final String maxVersion = it.maxVersion().isEmpty() ? "" : """ maxVersion="${it.maxVersion()}\""""
                final String end = (index == count-1) ? "" : "\n"
                final String target = it.targetPath().isEmpty() ? "" : """ target="${it.targetPath()}\""""
                sb.append("""${start}${indent}<resource name="${it.name()}" version="${it.version()}"${minVersion}${maxVersion}${target}>${it.path()}</resource>${end}""")
            }
        }

        sb.toString()
    }

    private static void addResourceTagsForDependencies(String indent, Set<ResolvedArtifact> dependencies, Set<ResolvedArtifact> providedCompileDependencies, StringBuilder projectResources, String scope, ModuleInfo.Mode mode, boolean appendDefaultMinVersion, Set<FSMPlugin.MinMaxVersion> minMaxVersionDefinitions = new HashSet<>()) {
        dependencies.
            findAll{ !providedCompileDependencies.contains(it) }
            .forEach {
                ModuleVersionIdentifier dependencyId = it.moduleVersion.id
                projectResources.append("\n" + getResourceTagForDependency(indent, dependencyId, it, scope, mode, appendDefaultMinVersion, minMaxVersionDefinitions))
            }
    }

    static String getResourceTagForDependency(String indent, ModuleVersionIdentifier dependencyId, ResolvedArtifact artifact, String scope, ModuleInfo.Mode mode, boolean appendDefaultMinVersion = true, Set<FSMPlugin.MinMaxVersion> minMaxVersionDefinitions = new HashSet<>()) {
        def scopeAttribute = scope == null || scope.isEmpty() ? "" : """ scope="${scope}\""""
        def classifier = artifact.classifier != null && !artifact.classifier.isEmpty() ? "-${artifact.classifier}" : ""
        def modeAttribute = mode == null ? "" : """ mode="${mode.name().toLowerCase(Locale.ROOT)}\""""

        String dependencyAsString = "${dependencyId.group}:${dependencyId.name}"
        FSMPlugin.MinMaxVersion optionalMinMaxVersion = minMaxVersionDefinitions.find { it.dependency.startsWith(dependencyAsString) }
        def minVersionAttribute = appendDefaultMinVersion ? dependencyId.version : null
        minVersionAttribute = optionalMinMaxVersion?.minVersion ? optionalMinMaxVersion.minVersion : minVersionAttribute

        String minVersionString = minVersionAttribute ? """ minVersion="${minVersionAttribute}\"""" : ""
        String maxVersionString = optionalMinMaxVersion ? """ maxVersion="${optionalMinMaxVersion.maxVersion}\"""" : ""

        """${indent}<resource name="${dependencyId.group}:${dependencyId.name}"$scopeAttribute$modeAttribute version="${dependencyId.version}"${minVersionString}${maxVersionString}>lib/${dependencyId.name}-${dependencyId.version}$classifier.${artifact.extension}</resource>"""
    }

    static String getResourcesTags(Project project, ModuleInfo.Mode globalResourcesMode, boolean appendDefaultMinVersion, boolean skipIsolationOnlyDependencies = false, Logger logger = null) {

        def indent = INDENT_WS_8
        def projectResources = new StringBuilder()
        def modeAttribute = globalResourcesMode == null ? "" : """ mode="${globalResourcesMode.name().toLowerCase(Locale.ROOT)}\""""
        projectResources.append("""${indent}<resource name="${project.group}:${project.name}" version="${project.version}" scope="module\"""" +
                                """${modeAttribute}>lib/${project.name}-${project.version}.jar</resource>"""
        )
        if (project.file('src/main/files').exists()) {
            projectResources.append("""${indent}<resource name="${project.group}:${project.name}-files" version="${project.version}" scope="module\"""" +
                                    """${modeAttribute}>files/</resource>\n""")
        }
        ConfigurationContainer configurations = project.configurations

        def fsServerCompileConfiguration = configurations.getByName("fsModuleCompile")
        fsServerCompileConfiguration.attributes.keySet().forEach { println it.toString() }

        Set<ResolvedArtifact> compileDependenciesServerScoped = configurations.fsServerCompile.getResolvedConfiguration().getResolvedArtifacts()
        Set<ResolvedArtifact> uncleanedDependenciesModuleScoped = configurations.fsModuleCompile.getResolvedConfiguration().getResolvedArtifacts()
        Set<ResolvedArtifact> cleanedCompileDependenciesModuleScoped = uncleanedDependenciesModuleScoped - compileDependenciesServerScoped
        logIgnoredModuleScopeDependencies(logger, uncleanedDependenciesModuleScoped, cleanedCompileDependenciesModuleScoped)
        Set<ResolvedArtifact> providedCompileDependencies = configurations.fsProvidedCompile.getResolvedConfiguration().getResolvedArtifacts()

        if(skipIsolationOnlyDependencies) {
            Set<ResolvedArtifact> skippedInLegacyDependencies = configurations.skippedInLegacy.getResolvedConfiguration().getResolvedArtifacts()
            compileDependenciesServerScoped.removeAll(skippedInLegacyDependencies)
            cleanedCompileDependenciesModuleScoped.removeAll(skippedInLegacyDependencies)
            providedCompileDependencies.removeAll(skippedInLegacyDependencies)
        }
        def minMaxVersionConfigurations = project.getPlugins().getPlugin(FSMPlugin.class).getDependencyConfigurations()

        addResourceTagsForDependencies(indent, compileDependenciesServerScoped, providedCompileDependencies, projectResources, "server", globalResourcesMode, appendDefaultMinVersion, minMaxVersionConfigurations)
        projectResources + "\n"
        addResourceTagsForDependencies(indent, cleanedCompileDependenciesModuleScoped, providedCompileDependencies, projectResources, "module", globalResourcesMode, appendDefaultMinVersion, minMaxVersionConfigurations)
        return projectResources.toString()
    }

    private static void logIgnoredModuleScopeDependencies(Logger logger, Set<ResolvedArtifact> uncleanedDependenciesModuleScoped, Set<ResolvedArtifact> compileDependenciesModuleScoped) {
        if (logger != null) {
            Set<ResolvedArtifact> ignoredDependenciesModuleScoped = uncleanedDependenciesModuleScoped - compileDependenciesModuleScoped
            logger.debug("The following dependencies are found on both module and server scope. The scope will be resolved to server.")
            ignoredDependenciesModuleScoped.forEach {
                logger.debug(it.toString())
            }
        }
    }
}
