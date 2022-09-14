package org.gradle.plugins.fsm.util

import de.espirit.firstspirit.module.ProjectApp
import de.espirit.firstspirit.module.ProjectEnvironment
import de.espirit.firstspirit.module.descriptor.ProjectAppDescriptor

open class BaseProjectApp: ProjectApp {

    override fun init(projectAppDescriptor: ProjectAppDescriptor?, projectEnvironment: ProjectEnvironment?) {}

    override fun installed() {}

    override fun uninstalling() {}

    override fun updated(s: String?) {}

}