package org.gradle.plugins.fsm

import de.espirit.mavenplugins.fsmchecker.ComplianceLevel
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project

open class FSMPluginExtension(val project: Project) {

    private val fsmWebApps: MutableMap<String, Project> = mutableMapOf()

    val libraries: NamedDomainObjectContainer<LibraryDeclaration> = project.container(LibraryDeclaration::class.java)

    /**
     * Registers a web-app to a given subproject
     *
     * @param webAppName The name of the web-app
     * @param webAppProject The subproject holding the web-app's resources
     */
    fun webAppComponent(webAppName: String, webAppProject: Project) {
        fsmWebApps[webAppName] = webAppProject

        // Register webapp dependency in extra configuration
        val webAppsConfiguration = project.configurations.getByName(FSMPlugin.WEBAPPS_CONFIGURATION_NAME)
        // This is the same as
        //    implementation project("projectName", configuration: "default")
        // and is required because of an error with the variant selection regarding the license report plugin.
        // For more information, see https://github.com/jk1/Gradle-License-Report/issues/170
        val projectDependency = project.dependencies.project(mapOf("path" to webAppProject.path, "configuration" to "default"))
        project.dependencies.add(webAppsConfiguration.name, projectDependency)
    }

    /**
     * Registers a web-app subproject, with the name of the web-app matching the project's name.
     *
     * @param webAppProject The subproject holding the web-app's resources
     */
    fun webAppComponent(webAppProject: Project) {
        webAppComponent(webAppProject.name, webAppProject)
    }

    fun getWebApps(): Map<String, Project> {
        return fsmWebApps.toMap()
    }

    /**
     * The name of the module that should be used in the module-isolated.xml instead of the project name
     */
    var moduleName: String? = null

    /**
     * The name of the directory containing the module-isolated.xml, relative to the project directory.
     */
    var moduleDirName: String? = null

    /**
     * Human-readable display name of the module
     */
    var displayName: String? = null

    /**
     * Responsible vendor of the module
     */
    var vendor: String? = null

    /**
     * If set, the plugin will use this username to connect to the FSM Dependency Detector
     */
    var isolationDetectorUsername: String? = null

    /**
     * If set, the plugin will use this password to connect to the FSM Dependency Detector
     */
    var isolationDetectorPassword: String? = null

    /**
     * If set, this URL is used to connect to the FSM Dependency Detector
     */
    var isolationDetectorUrl: String? = null

    /**
     * Resource identifiers of the form 'groupId:artifactId:version' of resources
     * which should not be scanned for external dependencies
     */
    var isolationDetectorWhitelist: Collection<String> = emptySet()

    /**
     * Names of web components to be deployed as part of a ContentCreator web-app.
     */
    var contentCreatorComponents: Collection<String> = emptySet()

    /**
     * The compliance level to check for if {#link isolationDetectorUrl} is set. Defaults to
     * [ComplianceLevel#DEFAULT]
     */
    var complianceLevel: ComplianceLevel = ComplianceLevel.DEFAULT

    /**
     * The maximum bytecode level allowed for all Java classes
     */
    var maxBytecodeVersion: Int = 61 // JDK 17

    /**
     * The FirstSpirit version to check against with the isolation detector service.
     */
    var firstSpiritVersion: String? = null

    /**
     * Defines the minimal version of the FirstSpirit server that is required
     * to install the module.
     */
    var minimalFirstSpiritVersion: String? = null

    /**
     * Whether to append the artifact version as the minVersion attribute to resources.
     */
    var appendDefaultMinVersion: Boolean = true

    /**
     * The dependencies of this FS-Module (FSM) to other FSMs. Will at least be displayed in the UI,
     * when a user adds this Module.
     */
    var fsmDependencies: Collection<String> = emptySet()

    /**
     * Defines the scope ("module" or "server") of the jar built by the default jar task of the project
     * which is added to the module automatically.
     */
    var projectJarScope = "module"
        set(value) {
            if (value.lowercase() in listOf("module", "server")) {
                field = value
            } else {
                throw IllegalArgumentException("Unknown scope value '$value'.")
            }
        }

    /**
     * Whether to add the default jar task output of the project to web resources of all web-app components.
     */
    var addDefaultJarTaskOutputToWebResources: Boolean = true

    open fun libraries(action: Action<in NamedDomainObjectContainer<LibraryDeclaration>>) {
        action.execute(libraries)
    }

}