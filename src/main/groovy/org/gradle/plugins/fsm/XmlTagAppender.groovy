package org.gradle.plugins.fsm

import com.espirit.moddev.components.annotations.*
import com.espirit.moddev.components.annotations.params.gadget.Scope
import de.espirit.common.tools.Strings
import de.espirit.firstspirit.client.access.editor.ValueEngineerFactory
import de.espirit.firstspirit.generate.FilenameFactory
import de.espirit.firstspirit.generate.UrlFactory
import de.espirit.firstspirit.module.*
import de.espirit.firstspirit.module.descriptor.WebAppDescriptor
import de.espirit.firstspirit.scheduling.ScheduleTaskFormFactory
import de.espirit.firstspirit.server.module.ModuleInfo
import groovy.io.FileType
import groovy.text.SimpleTemplateEngine
import groovy.transform.CompileStatic
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.plugins.JavaPlugin
import org.gradle.plugins.fsm.configurations.FSMConfigurationsPlugin
import org.gradle.plugins.fsm.tasks.bundling.FSM

import java.lang.annotation.Annotation

import static de.espirit.firstspirit.module.GadgetComponent.GadgetFactory
import static org.gradle.plugins.fsm.configurations.FSMConfigurationsPlugin.*

class XmlTagAppender {

    static final List<String> PROJECT_APP_BLACKLIST = ["de.espirit.firstspirit.feature.ContentTransportProjectApp"]

    static final String INDENT_WS_4 = "    "
    static final String INDENT_WS_8 = "        "
    static final String INDENT_WS_12 = "            "
    static final String INDENT_WS_16 = "                "

    /*
     * Append components tag to given result StringBuilder
     */
    static WebXmlPaths appendComponentsTag(Project project, URLClassLoader classLoader, FSM.ClassScannerResultProvider scan, final boolean appendDefaultMinVersion, final StringBuilder result, boolean isolated) {

        appendPublicComponentTags(classLoader, scan.getNamesOfClassesWithAnnotation(PublicComponent), result)

        appendComponentTags(classLoader, scan.getNamesOfClassesWithAnnotation(ScheduleTaskComponent), result, ScheduleTaskComponent)

        appendComponentTags(classLoader, scan.getNamesOfClassesWithAnnotation(GadgetComponent), result, GadgetComponent)

        appendUrlCreatorTags(classLoader, scan.getNamesOfClassesWithAnnotation(UrlFactoryComponent), result)

        appendServiceTags(classLoader, scan.getNamesOfClassesImplementing(Service), result)

        appendProjectAppTags(project, classLoader, scan.getNamesOfClassesImplementing(ProjectApp), result)

        return appendWebAppTags(project, classLoader, scan.getNamesOfClassesImplementing(WebApp), result, appendDefaultMinVersion, project.plugins.getPlugin(FSMConfigurationsPlugin).dependencyConfigurations, isolated)
    }


    @CompileStatic
    static String getFsmDependencyTags(Project project) {
        def result = new StringBuilder()

        FSMPluginExtension fsmPlugin = project.getExtensions().getByType(FSMPluginExtension)
        Collection<String> fsmDependencies = fsmPlugin.getFsmDependencies()

        for (String name : fsmDependencies) {
            result.append("\n").append(INDENT_WS_8).append("<depends>").append(name).append("</depends>")
        }
        return result
    }

    @CompileStatic
    static void appendModuleAnnotationTags(URLClassLoader cl, FSM.ClassScannerResultProvider scan, StringBuilder result, List<String> moduleBlacklist = new ArrayList<>()) {

        def moduleAnnotatedClasses = scan.getNamesOfClassesWithAnnotation(ModuleComponent).findAll{!moduleBlacklist.contains(it)}
        def moduleImplClasses = scan.getNamesOfClassesImplementing(Module).findAll{!moduleBlacklist.contains(it)}
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
        final String configurableTag = "\n${INDENT_WS_4}<configurable>${annotation.configurable().name}</configurable>"
        final String configurable = annotation.configurable() == Configuration.class ? "" : configurableTag
        result.append("${INDENT_WS_4}<class>${module.getName()}</class>${configurable}")
    }

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
            <description>${evaluateAnnotation(annotation, "description")}</description>
            <class>${publicComponentClass.getName().toString()}</class>${configurable}
        </public>""")

                }
        }
    }


    @CompileStatic
    static void appendComponentTags(URLClassLoader cl, List<String> classes, StringBuilder result, Class<?> type) {
        def loadedClasses = classes.collect { cl.loadClass(it) }
        switch(type) {
            case ScheduleTaskComponent.class:
                appendScheduleTaskComponentTagsOfClasses(loadedClasses, result)
                break
            case GadgetComponent.class:
                appendGadgetComponentTagsOfClasses(loadedClasses, result)
                break
            default:
                throw new UnsupportedOperationException("Handling of type " + type.getName() + " is not supported yet")
        }
    }

    private static appendScheduleTaskComponentTagsOfClasses(List<Class<?>> loadedClasses, StringBuilder result) {
        loadedClasses.forEach {
            scheduleTaskComponentClass ->
                Arrays.asList(scheduleTaskComponentClass.annotations)
                .findAll { it instanceof ScheduleTaskComponent }
                .forEach { annotation ->
                    def indent = INDENT_WS_12
                    def configurable = annotation.configurable() == Configuration.class ? "" : "\n" + indent + "<configurable>${annotation.configurable().name}</configurable>"
                    def displayName = annotation.displayName().allWhitespace ? "" : "\n" + indent + "<displayname>${annotation.displayName()}</displayname>"

// keep indent (2 tabs / 8 whitespaces) --> 3. level <module><components><public>
                    result.append("""
        <public>
            <name>${evaluateAnnotation(annotation, "taskName")}</name>${displayName}
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


    /**
     * This method appends all the necessary tags for all the gadget components that were found.
     *
     * @param loadedClasses The loaded classes
     * @param resultBuilder The StringBuilder in which the result should be written.
     */
    private static appendGadgetComponentTagsOfClasses(List<Class<?>> loadedClasses, StringBuilder resultBuilder) {
        loadedClasses.forEach {
            gadgetComponent ->
                Arrays.asList(gadgetComponent.annotations)
                        .findAll { it instanceof GadgetComponent }
                        .forEach { annotation ->
                    def indent = INDENT_WS_12
                    final String configurable = annotation.configurable() == Configuration.class ? "" : "\n" + indent + "<configurable>${annotation.configurable().name}</configurable>"

// keep indent (2 tabs / 8 whitespaces) --> 3rd level <module><components><public>
                    resultBuilder.append("""
        <public>
            <name>${evaluateAnnotation(annotation, "name")}</name>
            <description>${evaluateAnnotation(annotation, "description")}</description>
            <class>${GadgetSpecification.getName()}</class>
            <configuration>
                <gom>${gadgetComponent.getName()}</gom>""")

                    Class<GadgetFactory>[] objects = evaluateAnnotation(annotation, "factories") as Class<GadgetFactory>[]
                    /*
                    The default interface GadgetFactory should
                    not be written in the xml so it gets filtered.
                     */
                    for(Class<GadgetFactory> o : objects) {
                        if(o != GadgetFactory) {
                            resultBuilder.append("""
                <factory>${o.getName()}</factory>""")
                        }
                    }

                    Object value = evaluateAnnotation(annotation, "valueEngineerFactory")
                    if(value != null && value != ValueEngineerFactory.class) {
                        resultBuilder.append("""
                <value>${value.getName()}</value>""")
                    }


                    List<Scope> scopes = ((Object[])evaluateAnnotation(annotation, "scopes")).toList() as List<Scope>
                    if(scopes.contains(Scope.UNRESTRICTED)) {
                        resultBuilder.append("""
                <scope ${Scope.UNRESTRICTED.name().toLowerCase()}=\"yes\" />""")
                    } else {
                        resultBuilder.append("""
                <scope """)
                        scopes.forEach {s -> resultBuilder.append(s.name().toLowerCase() + '=\"yes\" ')}
                        resultBuilder.append('/>')
                    }
                    resultBuilder.append("""
            </configuration>$configurable
        </public>""")

                }
        }
    }


    /**
     * Tests if two {@link org.gradle.api.artifacts.ResolvedConfiguration} objects are the same, ignoring the module version. Used for dependency version resolution
     */
    private static boolean moduleMatches(ResolvedArtifact a, ResolvedArtifact b) {
        a.moduleVersion.id.name == b.moduleVersion.id.name &&
                a.moduleVersion.id.group == b.moduleVersion.id.group &&
                a.extension == b.extension &&
                a.classifier == b.classifier &&
                a.type == b.type
    }


    /**
     * Finds all dependencies of a given configuration and finds the global version of each dependency
     *
     * @param project           The project
     * @param configurationName The configuration to fetch the dependencies for
     * @param allDependencies   All dependencies of the project, with the correct version
     * @return The dependencies of {@code configurationName}, with the correct version
     */
    private static Set<ResolvedArtifact> getResolvedDependencies(Project project, String configurationName, Set<ResolvedArtifact> allDependencies) {
        def configuration = project.configurations.findByName(configurationName)
        if (configuration == null) {
            return Collections.emptySet()
        }
        def resolvedArtifacts = configuration.resolvedConfiguration.resolvedArtifacts
        allDependencies.findAll { resource ->
            resolvedArtifacts.any { moduleMatches(resource, it) }
        }
    }


    static class WebXmlPaths extends ArrayList<String> { }

    @CompileStatic
    static WebXmlPaths appendWebAppTags(Project project, URLClassLoader cl, List<String> webAppClassNames, StringBuilder result, boolean appendDefaultMinVersion, Set<FSMConfigurationsPlugin.MinMaxVersion> minMaxVersionConfigurations = new HashSet<>(), boolean isolated) {
        def logger = Logging.getLogger(XmlTagAppender)
        def webAppClasses = webAppClassNames.collect{ cl.loadClass(it) }

        def webXmlPaths = appendWebAppComponentTagsOfClasses(webAppClasses, project, result, appendDefaultMinVersion, minMaxVersionConfigurations, isolated)

        def webAppChecker = new DeclaredWebAppChecker(project, webAppClasses)
        def declaredWebApps = project.extensions.getByType(FSMPluginExtension).webApps

        // Check if web-apps are complete
        // Warn if there is a @WebAppComponent annotation not defined in the `firstSpiritModule` block
        def undeclaredWebAppComponents = webAppChecker.webAppAnnotationsWithoutDeclaration
        if (declaredWebApps.any() && undeclaredWebAppComponents.any()) {
            def warningStringBuilder = new StringBuilder()
            warningStringBuilder.append("@WebAppComponent annotations found that are not registered in the ${FSMPlugin.FSM_EXTENSION_NAME} configuration block:\n")
            undeclaredWebAppComponents.each {
                warningStringBuilder.append("- ${it.name()}${!it.displayName().isEmpty() ?  " (" + it.displayName() + ")" : ""}\n")
            }
            logger.log(LogLevel.WARN, warningStringBuilder.toString())
        }
        // ... or if there is a web-app defined in the `firstSpiritModule` block we cannot find a @WebAppComponent annotation for, throw an error
        def declaredWebAppNames = webAppChecker.declaredProjectsWithoutAnnotation
        if (declaredWebAppNames.any()) {
            def errorStringBuilder = new StringBuilder()
            errorStringBuilder.append("No @WebAppComponent annotation found for the following web-apps registered in the ${FSMPlugin.FSM_EXTENSION_NAME} configuration block:\n")
            declaredWebAppNames.each {
                errorStringBuilder.append("- ${it}\n")
            }
            throw new GradleException(errorStringBuilder.toString())
        }

        webXmlPaths
    }

    private static WebXmlPaths appendWebAppComponentTagsOfClasses(List<Class<?>> webAppClasses, Project project, result, boolean appendDefaultMinVersion, Set<FSMConfigurationsPlugin.MinMaxVersion> minMaxVersionConfigurations = new HashSet<>(), boolean isolated) {
        // We might find the same dependencies in different subprojects / configurations, but with different versions
        // Because only one version ends up in the FSM archive, we need to make sure we always use the correct version
        def allCompileDependencies = project.configurations.getByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME).resolvedConfiguration.resolvedArtifacts
        def sharedWebCompileDependencies = getResolvedDependencies(project, FS_WEB_COMPILE_CONFIGURATION_NAME, allCompileDependencies)
        def providedCompileDependencies = getResolvedDependencies(project, PROVIDED_COMPILE_CONFIGURATION_NAME, allCompileDependencies)
        def skippedInLegacyDependencies = getAllSkippedInLegacyDependencies(project, allCompileDependencies)
        if (!isolated) {
            sharedWebCompileDependencies.removeAll(skippedInLegacyDependencies)
            providedCompileDependencies.removeAll(skippedInLegacyDependencies)
        }
        def webResourceIndent = INDENT_WS_16

        def webXmlPaths = new WebXmlPaths()

        def declaredWebApps = project.extensions.getByType(FSMPluginExtension).webApps

        webAppClasses.forEach { webAppClass ->
            def annotation = Arrays.asList(webAppClass.annotations).find { it instanceof WebAppComponent } as WebAppComponent
            if (annotation != null) {

                def webResources = new StringBuilder()

                // fsm-resources directory of root project and fsWebCompile subprojects (shared between all webapps)
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

                def webAppName = evaluateAnnotation(annotation, "name") as String
                if (declaredWebApps.containsKey(webAppName)) {
                    def webAppProject = declaredWebApps[webAppName]

                    // fsm-resources directory of current web-app
                    // - safety check to avoid duplicates
                    if (!projectDependencies.collect { it.dependencyProject }.contains(webAppProject)) {
                        appendWebResources(webAppProject)
                    }

                    // compile dependencies of web-app subproject -
                    // If we registered a subproject for a given web-app, evaluate its compile dependencies
                    def webAppProjectDependencies = getResolvedDependencies(webAppProject, JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME, allCompileDependencies)
                    if (!isolated) {
                        webAppProjectDependencies.removeAll(skippedInLegacyDependencies)
                    }

                    // Don't want duplicate resources
                    webAppProjectDependencies.removeAll(sharedWebCompileDependencies)

                    // Add dependencies
                    webResources.append("""${webResourceIndent}<resource name="${webAppProject.group}:${webAppProject.name}" version="${webAppProject.version}">lib/${getJarFilename(webAppProject)}</resource>\n""")
                    addResourceTagsForDependencies(webResourceIndent, webAppProjectDependencies, providedCompileDependencies, webResources, "", null, appendDefaultMinVersion, minMaxVersionConfigurations)
                }

                // fsWebCompile for all subprojects
                addResourceTagsForDependencies(webResourceIndent, sharedWebCompileDependencies, providedCompileDependencies, webResources, "", null, appendDefaultMinVersion, minMaxVersionConfigurations)

                final String scopes = scopes(annotation.scope())
                def indent = INDENT_WS_12
                final String configurable = annotation.configurable() == Configuration.class ? "" : "\n" + indent + "<configurable>${annotation.configurable().name}</configurable>"

                def webXmlPath = evaluateAnnotation(annotation, "webXml").toString()
                webXmlPaths.add(webXmlPath)

                def webResourcesString = webResources.toString()
                def resourcesString = evaluateResources(annotation, webResourceIndent, project)
                if (!resourcesString.isEmpty()) {
                    resourcesString = "\n" + resourcesString
                }
                if (!webResourcesString.isEmpty()) {
                    webResourcesString = "\n" + webResourcesString
                }
// keep indent (2 tabs / 8 whitespaces) --> 3. level <module><components><web-app>
                result.append("""
        <web-app${scopes}>
            <name>${webAppName}</name>
            <displayname>${evaluateAnnotation(annotation, "displayName")}</displayname>
            <description>${evaluateAnnotation(annotation, "description")}</description>
            <class>${webAppClass.getName().toString()}</class>${configurable}
            <web-xml>${webXmlPath}</web-xml>
            <web-resources>
                <resource name="${project.group}:${project.name}" version="${project.version}">lib/${getJarFilename(project)}</resource>
${resourcesString}${webResourcesString}
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
    static void appendProjectAppTags(Project project, URLClassLoader cl, List<String> projectAppClasses, StringBuilder result) {
        def loadedClasses = projectAppClasses.findAll { !PROJECT_APP_BLACKLIST.contains(it) }.collect { cl.loadClass(it) }

        appendProjectAppTagsOfClasses(loadedClasses, result, project)
    }

    static appendProjectAppTagsOfClasses(List<Class<?>> loadedClasses, result, Project project) {
        def resourceIndent = INDENT_WS_16
        loadedClasses.forEach { projectAppClass ->
            Arrays.asList(projectAppClass.annotations)
                .findAll { it.annotationType() == ProjectAppComponent }
                .forEach { annotation ->
                    final String resources = evaluateResources(annotation, resourceIndent, project)
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
                        final String fileNameFactory = annotation.filenameFactory() == FilenameFactory.class ? "" : "\n" + INDENT_WS_16 + "<FilenameFactory>${annotation.filenameFactory().name}</FilenameFactory>"
                        result.append("""
        <public>
            <name>${evaluateAnnotation(annotation, "name")}</name>
            <displayname>${evaluateAnnotation(annotation, "displayName")}</displayname>
            <description>${evaluateAnnotation(annotation, "description")}</description>
            <class>de.espirit.firstspirit.generate.UrlCreatorSpecification</class>
            <configuration>
                <UrlFactory>${urlFactoryClass.getName().toString()}</UrlFactory>
                <UseRegistry>${evaluateAnnotation(annotation, "useRegistry")}</UseRegistry>${fileNameFactory}
            </configuration>
        </public>
""")
            }
        }
    }

    private static Object evaluateAnnotation(Annotation annotation, String methodName) {
        annotation.annotationType().getDeclaredMethod(methodName, null).invoke(annotation, null)
    }

    protected static String evaluateResources(final Annotation annotation, final String indent, Project project) {
        final StringBuilder sb = new StringBuilder()

        Object[] resources

        if(annotation instanceof ProjectAppComponent) {
            resources = annotation.resources()
        } else if(annotation instanceof WebAppComponent) {
            resources = annotation.webResources()
        } else throw new IllegalArgumentException("Cannot process annotation of type ${annotation.class}!")

        def count = resources.length
        resources.eachWithIndex { Object resource, int index ->
            final String end = (index == count-1) ? "" : "\n"
            final String minVersion = resource.minVersion().isEmpty() ? "" : """ minVersion="${resource.minVersion()}\""""
            final String maxVersion = resource.maxVersion().isEmpty() ? "" : """ maxVersion="${resource.maxVersion()}\""""


            def nameFromAnnotation = expand(resource.name(), [project:project])

            ResolvedArtifact dependencyForNameOrNull = getCompileDependencyForNameOrNull(project, nameFromAnnotation)
            Map<String, Project> context = getContextForCurrentResource(project, dependencyForNameOrNull)

            def versionFromAnnotation = expandVersion(resource.version(), context, nameFromAnnotation, (annotation as ProjectAppComponent).name())

            def pathFromAnnotation = expand(resource.path(), context)

            if (resource instanceof Resource) {
                sb.append("""${indent}<resource name="${nameFromAnnotation}" version="${versionFromAnnotation}"${minVersion}${maxVersion} scope="${resource.scope().toString().toLowerCase()}" mode="${resource.mode().toString().toLowerCase()}">${pathFromAnnotation}</resource>${end}""")
            } else if (resource instanceof WebResource) {
                final String target = resource.targetPath().isEmpty() ? "" : """ target="${resource.targetPath()}\""""
                sb.append("""${indent}<resource name="${nameFromAnnotation}" version="${versionFromAnnotation}"${minVersion}${maxVersion}${target}>${pathFromAnnotation}</resource>${end}""")
            } else throw new IllegalArgumentException("Cannot process resource of type ${resource.class}!")
        }

        sb.toString()
    }

    private static String expandVersion(String versionFromAnnotation, Map<String, Project> context, String nameFromAnnotation, String componentName) {
        try {
            versionFromAnnotation = expand(versionFromAnnotation, context)
        } catch (MissingPropertyException e) {
            throw new RuntimeException("No property found for placeholder in version attribute of resource '$nameFromAnnotation' in component ${componentName}.\n" +
                    "Template is '$versionFromAnnotation'.\n" +
                    "Resource not declared as compile dependency in project?\n" +
                    "For project version property, use '\${project.version}'.", e)
        }
        versionFromAnnotation
    }

    private static Map<String, Project> getContextForCurrentResource(Project project, ResolvedArtifact dependencyForNameOrNull) {
        def context = [project: project]
        if (dependencyForNameOrNull != null) {
            dependencyForNameOrNull?.properties?.each { prop ->
                context.put(prop.key, prop.value)
            }
            context.put("path", getPathInFsmForDependency(dependencyForNameOrNull))
            context.put("version", dependencyForNameOrNull.moduleVersion.id.version)
        }
        context
    }

    private static ResolvedArtifact getCompileDependencyForNameOrNull(Project project, String nameFromAnnotation) {
        def configuration = project.configurations.getByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME)
        configuration.resolvedConfiguration.resolvedArtifacts.find { ResolvedArtifact dependency ->
            def splitName = dependency.id.componentIdentifier.displayName.split(":")
            def groupId = splitName[0]
            def name = splitName[1]
            nameFromAnnotation == "${groupId}:${name}"
        }
    }

    private static String getPathInFsmForDependency(ResolvedArtifact artifact) {
        return "lib/${artifact.name}-${artifact.moduleVersion.id.version}${artifact.classifier ?: ""}.${artifact.extension}"
    }

    static String expand(String template, Map<String, Object> context) {
        return new SimpleTemplateEngine().createTemplate(template).make(context).toString()
    }

    private static void addResourceTagsForDependencies(String indent, Set<ResolvedArtifact> dependencies, Set<ResolvedArtifact> providedCompileDependencies, StringBuilder projectResources, String scope, ModuleInfo.Mode mode, boolean appendDefaultMinVersion, Set<FSMConfigurationsPlugin.MinMaxVersion> minMaxVersionDefinitions = new HashSet<>()) {
        dependencies.findAll {
            !providedCompileDependencies.contains(it)
        }.forEach {
            def dependencyId = it.moduleVersion.id
            def resourceTag = getResourceTagForDependency(dependencyId, it, scope, mode, appendDefaultMinVersion, minMaxVersionDefinitions)
            projectResources.append("\n${indent}${resourceTag}")
        }
    }

    static String getResourceTagForDependency(ModuleVersionIdentifier dependencyId, ResolvedArtifact artifact, String scope, ModuleInfo.Mode mode, boolean appendDefaultMinVersion = true, Set<MinMaxVersion> minMaxVersionDefinitions = new HashSet<>()) {
        def dependencyAsString = "${dependencyId.group}:${dependencyId.name}"

        // Construct file name in FSM
        def fileClassifier = Strings.isEmpty(artifact.classifier) ? "" : "-${artifact.classifier}"
        def filename = "${dependencyId.name}-${dependencyId.version}$fileClassifier.${artifact.extension}"

        // Construct resource identifier
        def resourceClassifier = Strings.isEmpty(artifact.classifier) ? "" : ":${artifact.classifier}"
        def resourceExtension = Strings.isEmpty(artifact.extension) || artifact.extension == "jar" ? "" : "@${artifact.extension}" // Special case for "jar", as the "default" extension we do not put it here
        def resourceIdentifier = "${dependencyAsString}${resourceClassifier}${resourceExtension}"

        // Get attributes
        def scopeAttribute = Strings.isEmpty(scope) ? "" : " scope=\"${scope}\""
        def modeAttribute = mode == null ? "" : " mode=\"${mode.name().toLowerCase(Locale.ROOT)}\""

        def optionalMinMaxVersion = minMaxVersionDefinitions.find { it.dependency.startsWith(dependencyAsString) }
        def minVersionAttribute = appendDefaultMinVersion ? dependencyId.version : null
        minVersionAttribute = optionalMinMaxVersion?.minVersion ? optionalMinMaxVersion.minVersion : minVersionAttribute
        def minVersionString = minVersionAttribute ? " minVersion=\"${minVersionAttribute}\"" : ""
        def maxVersionString = optionalMinMaxVersion ? " maxVersion=\"${optionalMinMaxVersion.maxVersion}\"" : ""

        """<resource name="${resourceIdentifier}"$scopeAttribute$modeAttribute version="${dependencyId.version}"${minVersionString}${maxVersionString}>lib/${filename}</resource>"""
    }

    static String getJarFilename(Project project) {
        return project.jar.archiveName
    }

    static String getResourcesTags(Project project, WebXmlPaths webXmlPaths, ModuleInfo.Mode globalResourcesMode, boolean appendDefaultMinVersion, boolean isolationMode = false, Logger logger = null) {

        def indent = INDENT_WS_8
        def projectResources = new StringBuilder()
        def modeAttribute = globalResourcesMode == null ? "" : """ mode="${globalResourcesMode.name().toLowerCase(Locale.ROOT)}\""""
        projectResources.append("""${indent}<resource name="${project.group}:${project.name}" version="${project.version}" scope="module\"""" +
                                """${modeAttribute}>lib/${getJarFilename(project)}</resource>"""
        )


        Map<String, String> tempResourceTags = new HashMap<>()
        def addResourceTagsToBuffer = { Project currentProject, String scope ->
            def fsmResourcesPath = currentProject.projectDir.absolutePath + '/' + FSM.FSM_RESOURCES_PATH
            def fsmResourcesFolder = new File(fsmResourcesPath)
            if (fsmResourcesFolder.exists()) {
                fsmResourcesFolder.eachFile(FileType.ANY) { file ->
                    def relPath = fsmResourcesFolder.toPath().relativize(file.toPath()).toFile()
                    def resourceTag = """\n${indent}<resource name="${project.group}:${project.name}-${relPath}" version="${project.version}" scope="${scope}\"${modeAttribute}>${relPath}</resource>"""
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

        Set<ResolvedArtifact> uncleanedDependenciesModuleScoped = configurations.fsModuleCompile.getResolvedConfiguration().getResolvedArtifacts()
        Set<ResolvedArtifact> resolvedServerScopeArtifacts = configurations.fsServerCompile.getResolvedConfiguration().getResolvedArtifacts()
        Set<ResolvedArtifact> compileDependenciesServerScoped = uncleanedDependenciesModuleScoped.findAll { moduleScoped ->
            resolvedServerScopeArtifacts.any { moduleMatches(it, moduleScoped) }
        }
        Set<ResolvedArtifact> cleanedCompileDependenciesModuleScoped = uncleanedDependenciesModuleScoped - compileDependenciesServerScoped
        logIgnoredModuleScopeDependencies(logger, uncleanedDependenciesModuleScoped, cleanedCompileDependenciesModuleScoped)
        Set<ResolvedArtifact> providedCompileDependencies = configurations.fsProvidedCompile.getResolvedConfiguration().getResolvedArtifacts()

        boolean legacyMode = !isolationMode
        if (legacyMode) {
            def allCompileDependencies = project.configurations.getByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME).resolvedConfiguration.resolvedArtifacts
            def skippedInLegacyDependencies = getAllSkippedInLegacyDependencies(project, allCompileDependencies)
            compileDependenciesServerScoped.removeAll(skippedInLegacyDependencies)
            cleanedCompileDependenciesModuleScoped.removeAll(skippedInLegacyDependencies)
            providedCompileDependencies.removeAll(skippedInLegacyDependencies)
            Logging.getLogger(XmlTagAppender).debug("Dependencies skipped for (legacy) module.xml:")
            skippedInLegacyDependencies.each {
                Logging.getLogger(XmlTagAppender).debug(it.toString())
            }
        }
        def minMaxVersionConfigurations = project.getPlugins().getPlugin(FSMConfigurationsPlugin.class).getDependencyConfigurations()

        addResourceTagsForDependencies(indent, compileDependenciesServerScoped, providedCompileDependencies, projectResources, "server", globalResourcesMode, appendDefaultMinVersion, minMaxVersionConfigurations)
        projectResources + "\n"
        addResourceTagsForDependencies(indent, cleanedCompileDependenciesModuleScoped, providedCompileDependencies, projectResources, "module", globalResourcesMode, appendDefaultMinVersion, minMaxVersionConfigurations)
        return projectResources.toString()
    }

    private static List<ResolvedArtifact> getAllSkippedInLegacyDependencies(Project project, Set<ResolvedArtifact> allCompileDependencies) {
        project.rootProject.allprojects.collectMany {
            Set<ResolvedArtifact> result = new HashSet<>()
            try {
                result = getResolvedDependencies(it, FS_SKIPPED_IN_LEGACY_CONFIGURATION_NAME, allCompileDependencies)
            } catch (MissingPropertyException e) {
                Logging.getLogger(XmlTagAppender).trace("No skipInLegacy configuration found for project $it (probably not using fsm plugins at all)", e)
            }
            result
        }
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
