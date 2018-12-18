package org.gradle.plugins.fsm

import com.espirit.moddev.components.annotations.ModuleComponent
import com.espirit.moddev.components.annotations.ProjectAppComponent
import com.espirit.moddev.components.annotations.PublicComponent
import com.espirit.moddev.components.annotations.ScheduleTaskComponent
import com.espirit.moddev.components.annotations.ServiceComponent
import com.espirit.moddev.components.annotations.UrlFactoryComponent
import com.espirit.moddev.components.annotations.WebAppComponent
import com.espirit.moddev.components.annotations.WebResource
import de.espirit.firstspirit.generate.UrlFactory
import de.espirit.firstspirit.module.Configuration
import de.espirit.firstspirit.module.Module
import de.espirit.firstspirit.module.ProjectApp
import de.espirit.firstspirit.module.ScheduleTaskSpecification
import de.espirit.firstspirit.module.Service
import de.espirit.firstspirit.module.WebApp
import de.espirit.firstspirit.module.descriptor.WebAppDescriptor
import de.espirit.firstspirit.scheduling.ScheduleTaskFormFactory
import de.espirit.firstspirit.server.module.ModuleInfo
import groovy.io.FileType
import groovy.transform.CompileStatic
import org.gradle.api.Project
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.plugins.fsm.annotations.FSMAnnotationsPlugin
import org.gradle.plugins.fsm.configurations.FSMConfigurationsPlugin
import org.gradle.plugins.fsm.tasks.bundling.FSM

import java.lang.annotation.Annotation

import static org.gradle.plugins.fsm.configurations.FSMConfigurationsPlugin.FS_WEB_COMPILE_CONFIGURATION_NAME
import static org.gradle.plugins.fsm.configurations.FSMConfigurationsPlugin.PROVIDED_COMPILE_CONFIGURATION_NAME

class XmlTagAppender {

    static final List<String> PROJECT_APP_BLACKLIST = ["de.espirit.firstspirit.feature.ContentTransportProjectApp"]

    //blacklist contains test classes because of the restriction of allowing only 1 class to implement module
    static final List<String> MODULE_BLACKLIST = ["org.gradle.plugins.fsm.XmlTagAppenderTest\$TestModuleImplWithConfiguration",
                                                  "org.gradle.plugins.fsm.XmlTagAppenderTest\$TestModuleImpl"]

    static final String INDENT_WS_4 = "    "
    static final String INDENT_WS_8 = "        "
    static final String INDENT_WS_12 = "            "
    static final String INDENT_WS_16 = "                "

    /*
     * Append components tag to given result StringBuilder
     */
    @CompileStatic
    static WebXmlPaths appendComponentsTag(Project project, URLClassLoader classLoader, FSM.ClassScannerResultProvider scan, final boolean appendDefaultMinVersion, final StringBuilder result, boolean isolated) {

        appendPublicComponentTags(classLoader, scan.getNamesOfClassesWithAnnotation(PublicComponent), result)

        appendScheduleTaskComponentTags(classLoader, scan.getNamesOfClassesWithAnnotation(ScheduleTaskComponent), result)

        appendUrlCreatorTags(classLoader, scan.getNamesOfClassesWithAnnotation(UrlFactoryComponent), result)

        appendServiceTags(classLoader, scan.getNamesOfClassesImplementing(Service), result)

        appendProjectAppTags(classLoader, scan.getNamesOfClassesImplementing(ProjectApp), result)

        return appendWebAppTags(project, classLoader, scan.getNamesOfClassesImplementing(WebApp), result, appendDefaultMinVersion, project.plugins.getPlugin(FSMConfigurationsPlugin).dependencyConfigurations, isolated)
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
    static void appendModuleAnnotationTags(URLClassLoader cl, FSM.ClassScannerResultProvider scan, StringBuilder result) {
        def moduleAnnotatedClasses = scan.getNamesOfClassesWithAnnotation(ModuleComponent).findAll{!MODULE_BLACKLIST.contains(it)}
        def moduleImplClasses = scan.getNamesOfClassesImplementing(Module).findAll{!MODULE_BLACKLIST.contains(it)}
        def logger = Logging.getLogger(XmlTagAppender.class)

        handleModuleComponentAnnotations(moduleAnnotatedClasses, moduleImplClasses, logger, cl, result)

    }

    private static void handleModuleComponentAnnotations(List<String> moduleAnnotatedClasses, List<String> moduleImplClasses, Logger logger, URLClassLoader cl, StringBuilder result) {

        failIfMultipleModuleImplementations(moduleImplClasses)

        def noModuleAnnotatedClasses = moduleAnnotatedClasses.size() == 0
        def singleModuleAnnotatedClass = moduleAnnotatedClasses.size() == 1
        def multipleModuleAnnotatedClasses = moduleAnnotatedClasses.size() > 1

        boolean noModuleImplementations = moduleImplClasses.size() == 0
        boolean singleModuleImplementation = moduleImplClasses.size() == 1

        if (noModuleAnnotatedClasses) {
            logger.info("No class with an @ModuleComponent annotation could be found in your project.")
            if (noModuleImplementations) {
                logger.warn("No class implementing " + Module.getName() + " was found in your project. Are you sure you want to create an fsm without a module?")
            } else if (singleModuleImplementation) {
                logger.info("Looks like you forgot to add the @ModuleComponent annotation to " + moduleImplClasses[0])
            }
        } else if (multipleModuleAnnotatedClasses) {
            throw new IllegalStateException("The following classes annotated with @ModuleComponent were found in your project:\n" +
                    moduleAnnotatedClasses.toString() +
                    "\nYou cannot have more than one class annotated with @ModuleComponent in your project.")
        } else if (singleModuleAnnotatedClass) {
            appendModuleClassAndConfigTags(cl.loadClass(moduleAnnotatedClasses[0]), result)
        }
    }

    private static void failIfMultipleModuleImplementations(List<String> moduleImplClasses) {
        def multipleModuleImplementations = moduleImplClasses.size() > 1

        if (multipleModuleImplementations) {
            throw new IllegalStateException("The following classes implementing ${Module.getName()} were found in your project:\n" +
                    moduleImplClasses.toString() +
                    "\nYou cannot have more than one class implementing the module interface in your project.")
        }
    }

    static appendModuleClassAndConfigTags(Class<?> module, StringBuilder result){
        def annotation = module.annotations.find{ it instanceof ModuleComponent }
        final String configurable = annotation.configurable() == Configuration.class ? "" : "\n${INDENT_WS_4}<configurable>${annotation.configurable().name}</configurable>"
        result.append("${INDENT_WS_4}<class>${module.getName()}</class>${configurable}")
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
                    final String configurable = annotation.configurable() == Configuration.class ? "" : "\n${INDENT_WS_12}<configurable>${annotation.configurable().name}</configurable>"

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

    static class WebXmlPaths extends ArrayList<String> { }

    @CompileStatic
    static WebXmlPaths appendWebAppTags(Project project, URLClassLoader cl, List<String> webAppClasses, StringBuilder result, boolean appendDefaultMinVersion, Set<FSMConfigurationsPlugin.MinMaxVersion> minMaxVersionConfigurations = new HashSet<>(), boolean isolated) {
        List<Class<?>> loadedClasses = webAppClasses
            .collect{ cl.loadClass(it) }

        return appendWebAppComponentTagsOfClasses(loadedClasses, project, result, appendDefaultMinVersion, minMaxVersionConfigurations, isolated)
    }

    private static WebXmlPaths appendWebAppComponentTagsOfClasses(List<Class<?>> loadedClasses, Project project, result, boolean appendDefaultMinVersion, Set<FSMConfigurationsPlugin.MinMaxVersion> minMaxVersionConfigurations = new HashSet<>(), boolean isolated) {

        Set<ResolvedArtifact> webCompileDependencies = project.configurations.getByName(FS_WEB_COMPILE_CONFIGURATION_NAME).getResolvedConfiguration().getResolvedArtifacts()
        Set<ResolvedArtifact> providedCompileDependencies = project.configurations.getByName(PROVIDED_COMPILE_CONFIGURATION_NAME).getResolvedConfiguration().getResolvedArtifacts()
        if(!isolated){
            Set<ResolvedArtifact> skippedInLegacyDependencies = project.configurations.skippedInLegacy.getResolvedConfiguration().getResolvedArtifacts()
            webCompileDependencies.removeAll(skippedInLegacyDependencies)
            providedCompileDependencies.removeAll(skippedInLegacyDependencies)
        }
        def webResourceIndent = INDENT_WS_16

        def webXmlPaths = new WebXmlPaths()

        loadedClasses.forEach { webAppClass ->
            WebAppComponent annotation = Arrays.asList(webAppClass.annotations).find { it instanceof WebAppComponent }
            if(annotation != null) {
                StringBuilder webResources = new StringBuilder("\n")

                def appendWebResources = { Project currentProject ->

                    def fsmWebResourcesPath = currentProject.projectDir.absolutePath + '/' + FSM.FSM_RESOURCES_PATH
                    def fsmWebResourcesFolder = new File(fsmWebResourcesPath)
                    if (fsmWebResourcesFolder.exists()) {
                        fsmWebResourcesFolder.eachFile(FileType.ANY) { file ->
                            def relPath = fsmWebResourcesFolder.toPath().relativize(file.toPath()).toFile()
                            webResources.append("""${webResourceIndent}<resource name="${relPath.toPath()}" version="${project.version}">${relPath.toPath()}</resource>\n""")
                        }
                    }
                }

                def webCompileConfiguration = project.configurations.getByName(FS_WEB_COMPILE_CONFIGURATION_NAME)
                def projectDependencies = webCompileConfiguration.getAllDependencies().withType(ProjectDependency)
                projectDependencies.forEach { ProjectDependency dep ->
                    appendWebResources(dep.dependencyProject)
                }

                addResourceTagsForDependencies(webResourceIndent, webCompileDependencies, providedCompileDependencies, webResources, "", null, appendDefaultMinVersion, minMaxVersionConfigurations)

                final String scopes = scopes(annotation.scope())
                def indent = INDENT_WS_12
                final String configurable = annotation.configurable() == Configuration.class ? "" : "\n" + indent + "<configurable>${annotation.configurable().name}</configurable>"

                def webXmlPath = evaluateAnnotation(annotation, "webXml").toString()
                webXmlPaths.add(webXmlPath)
// keep indent (2 tabs / 8 whitespaces) --> 3. level <module><components><web-app>
                result.append("""
        <web-app${scopes}>
            <name>${evaluateAnnotation(annotation, "name")}</name>
            <displayname>${evaluateAnnotation(annotation, "displayName")}</displayname>
            <description>${evaluateAnnotation(annotation, "description")}</description>
            <class>${webAppClass.getName().toString()}</class>${configurable}
            <web-xml>${ webXmlPath}</web-xml>
            <web-resources>
                <resource name="${getJarFilename(project)}" version="${project.version}">lib/${getJarFilename(project)}</resource>
                ${evaluateResources(annotation, webResourceIndent)}${webResources.toString()}
            </web-resources>
        </web-app>
""")
            }

        }
        return webXmlPaths
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
                    final String configurable = annotation.configurable() == Configuration.class ? "" : "\n" + INDENT_WS_12 + "<configurable>${annotation.configurable().name}</configurable>"
                    final String resourcesTag = resources.isEmpty() ? "" : """\n${INDENT_WS_12}<resources>
${resources}
            </resources>"""
                    result.append("""
        <project-app>
            <name>${evaluateAnnotation(annotation, "name")}</name>
            <displayname>${evaluateAnnotation(annotation, "displayName")}</displayname>
            <description>${evaluateAnnotation(annotation, "description")}</description>
            <class>${projectAppClass.getName().toString()}</class>${configurable}${resourcesTag}
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
                final String configurable = annotation.configurable() == Configuration.class ? "" : "\n" + INDENT_WS_12 + "<configurable>${annotation.configurable().name}</configurable>"
                result.append("""
        <service>
            <name>${evaluateAnnotation(annotation, "name")}</name>
            <displayname>${evaluateAnnotation(annotation, "displayName")}</displayname>
            <description>${evaluateAnnotation(annotation, "description")}</description>
            <class>${serviceClass.getName().toString()}</class>${configurable}
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

    private static void addResourceTagsForDependencies(String indent, Set<ResolvedArtifact> dependencies, Set<ResolvedArtifact> providedCompileDependencies, StringBuilder projectResources, String scope, ModuleInfo.Mode mode, boolean appendDefaultMinVersion, Set<FSMConfigurationsPlugin.MinMaxVersion> minMaxVersionDefinitions = new HashSet<>()) {
        dependencies.
            findAll{ !providedCompileDependencies.contains(it) }
            .forEach {
                ModuleVersionIdentifier dependencyId = it.moduleVersion.id
                projectResources.append("\n" + getResourceTagForDependency(indent, dependencyId, it, scope, mode, appendDefaultMinVersion, minMaxVersionDefinitions))
            }
    }

    static String getResourceTagForDependency(String indent, ModuleVersionIdentifier dependencyId, ResolvedArtifact artifact, String scope, ModuleInfo.Mode mode, boolean appendDefaultMinVersion = true, Set<FSMConfigurationsPlugin.MinMaxVersion> minMaxVersionDefinitions = new HashSet<>()) {
        def scopeAttribute = scope == null || scope.isEmpty() ? "" : """ scope="${scope}\""""
        def classifier = artifact.classifier != null && !artifact.classifier.isEmpty() ? "-${artifact.classifier}" : ""
        def modeAttribute = mode == null ? "" : """ mode="${mode.name().toLowerCase(Locale.ROOT)}\""""

        String dependencyAsString = "${dependencyId.group}:${dependencyId.name}"
        FSMConfigurationsPlugin.MinMaxVersion optionalMinMaxVersion = minMaxVersionDefinitions.find { it.dependency.startsWith(dependencyAsString) }
        def minVersionAttribute = appendDefaultMinVersion ? dependencyId.version : null
        minVersionAttribute = optionalMinMaxVersion?.minVersion ? optionalMinMaxVersion.minVersion : minVersionAttribute

        String minVersionString = minVersionAttribute ? """ minVersion="${minVersionAttribute}\"""" : ""
        String maxVersionString = optionalMinMaxVersion ? """ maxVersion="${optionalMinMaxVersion.maxVersion}\"""" : ""

        """${indent}<resource name="${dependencyId.group}:${dependencyId.name}"$scopeAttribute$modeAttribute version="${dependencyId.version}"${minVersionString}${maxVersionString}>lib/${dependencyId.name}-${dependencyId.version}$classifier.${artifact.extension}</resource>"""
    }

    static String getJarFilename(Project project) {
        return project.jar.archiveName
    }

    static String getResourcesTags(Project project, WebXmlPaths webXmlPaths, ModuleInfo.Mode globalResourcesMode, boolean appendDefaultMinVersion, boolean isolationMode = false, Logger logger = null) {

        def indent = INDENT_WS_8
        def projectResources = new StringBuilder()
        def modeAttribute = globalResourcesMode == null ? "" : """ mode="${globalResourcesMode.name().toLowerCase(Locale.ROOT)}\""""
        projectResources.append("""${indent}<resource name="${getJarFilename(project)}" version="${project.version}" scope="module\"""" +
                                """${modeAttribute}>lib/${getJarFilename(project)}</resource>"""
        )


        Map<String, String> tempResourceTags = new HashMap<>()
        def addResourceTagsToBuffer = { Project currentProject, String scope ->
            def fsmResourcesPath = currentProject.projectDir.absolutePath + '/' + FSM.FSM_RESOURCES_PATH
            def fsmResourcesFolder = new File(fsmResourcesPath)
            if (fsmResourcesFolder.exists()) {
                fsmResourcesFolder.eachFile(FileType.ANY) { file ->
                    def relPath = fsmResourcesFolder.toPath().relativize(file.toPath()).toFile()
                    def resourceTag = """${indent}<resource name="${project.group}:${project.name}-${relPath}" version="${project.version}" scope="${scope}\"${modeAttribute}>${relPath}</resource>\n"""

                    if(!tempResourceTags.containsKey(relPath)) {
                        tempResourceTags.put(relPath.toString(), resourceTag.toString())
                    } else {
                        boolean overrideModuleScopedResourceWithServerScopedOne = tempResourceTags.get(relPath).contains("scope=\"module\"") && "server" == scope
                        if(overrideModuleScopedResourceWithServerScopedOne) {
                            tempResourceTags.put(relPath.toString(), resourceTag.toString())
                        }
                    }
                }
            }
        }

        FSMConfigurationsPlugin.FS_CONFIGURATIONS.forEach { configName ->
            def config = project.configurations.getByName(configName)
            def projectDependencies = config.getAllDependencies().withType(ProjectDependency)
            projectDependencies.collect { it.dependencyProject }.forEach {
                addResourceTagsToBuffer(it, configName == FSMConfigurationsPlugin.FS_MODULE_COMPILE_CONFIGURATION_NAME ? "module" : "server")
            }
        }

        addResourceTagsToBuffer(project, "module")

        removeWebXmlEntriesFromResources(tempResourceTags, webXmlPaths)

        tempResourceTags.values().forEach { String tag ->
            projectResources.append(tag)
        }

        addResourceTagsToBuffer(project, "module")

        ConfigurationContainer configurations = project.configurations

        def fsServerCompileConfiguration = configurations.getByName("fsModuleCompile")
        fsServerCompileConfiguration.attributes.keySet().forEach { println it.toString() }

        Set<ResolvedArtifact> compileDependenciesServerScoped = configurations.fsServerCompile.getResolvedConfiguration().getResolvedArtifacts()
        Set<ResolvedArtifact> uncleanedDependenciesModuleScoped = configurations.fsModuleCompile.getResolvedConfiguration().getResolvedArtifacts()
        Set<ResolvedArtifact> cleanedCompileDependenciesModuleScoped = uncleanedDependenciesModuleScoped - compileDependenciesServerScoped
        logIgnoredModuleScopeDependencies(logger, uncleanedDependenciesModuleScoped, cleanedCompileDependenciesModuleScoped)
        Set<ResolvedArtifact> providedCompileDependencies = configurations.fsProvidedCompile.getResolvedConfiguration().getResolvedArtifacts()

        boolean legacyMode = !isolationMode
        if(legacyMode) {
            Set<ResolvedArtifact> skippedInLegacyDependencies = configurations.skippedInLegacy.getResolvedConfiguration().getResolvedArtifacts()
            compileDependenciesServerScoped.removeAll(skippedInLegacyDependencies)
            cleanedCompileDependenciesModuleScoped.removeAll(skippedInLegacyDependencies)
            providedCompileDependencies.removeAll(skippedInLegacyDependencies)
        }
        def minMaxVersionConfigurations = project.getPlugins().getPlugin(FSMConfigurationsPlugin.class).getDependencyConfigurations()

        addResourceTagsForDependencies(indent, compileDependenciesServerScoped, providedCompileDependencies, projectResources, "server", globalResourcesMode, appendDefaultMinVersion, minMaxVersionConfigurations)
        projectResources + "\n"
        addResourceTagsForDependencies(indent, cleanedCompileDependenciesModuleScoped, providedCompileDependencies, projectResources, "module", globalResourcesMode, appendDefaultMinVersion, minMaxVersionConfigurations)
        return projectResources.toString()
    }

    static def removeWebXmlEntriesFromResources(Map<String, String> resourceTags, WebXmlPaths webXmlPaths) {

        def removeIfPresent = { String webXmlPath ->
            if(resourceTags.containsKey(webXmlPath)) {
                Logging.getLogger(XmlTagAppender).info("Removing resource ${webXmlPath} from resources, because it is used as web.xml file!")
                resourceTags.remove(webXmlPath)
            }
        }

        webXmlPaths.forEach { String webXmlPath ->
            removeIfPresent(webXmlPath)

            def simpleFileHasSlashPrefix = webXmlPath.count("/") == 1 && webXmlPath.startsWith("/")
            if(simpleFileHasSlashPrefix) {
                def trimmedWebXmlPath = webXmlPath.replaceFirst("/", "")
                removeIfPresent(trimmedWebXmlPath)
            }
        }
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
