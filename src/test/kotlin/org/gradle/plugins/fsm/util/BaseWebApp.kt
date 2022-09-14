package org.gradle.plugins.fsm.util

import de.espirit.firstspirit.module.WebApp
import de.espirit.firstspirit.module.WebEnvironment
import de.espirit.firstspirit.module.descriptor.WebAppDescriptor

open class BaseWebApp: WebApp {

    override fun createWar() {}

    override fun init(webAppDescriptor: WebAppDescriptor?, webEnvironment: WebEnvironment?) {}

    override fun installed() {}

    override fun uninstalling() {}

    override fun updated(s: String?) {}

}