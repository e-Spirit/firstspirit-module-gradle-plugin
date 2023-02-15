package org.gradle.plugins.fsm

import org.gradle.api.Named
import org.gradle.api.artifacts.Configuration

class LibraryDeclaration(private val libName: String): Named {

    override fun getName(): String {
        return libName
    }

    /**
     * The user-readable display name. If unset, the name attribute will be used instead.
     */
    var displayName = ""

    /**
     * An optional short description of the purpose of the library component.
     */
    var description = ""

    /**
     * If set to `true`, the library will not be displayed in the ServerManager module configuration.
     */
    var hidden = false

    /**
     * The optional full-qualified name of a class used to configure the library component.
     */
    var configurable = ""

    /**
     * The Gradle configuration that should be used to resolve the resources of this library.
     */
    var configuration: Configuration? = null

}