package org.gradle.plugins.fsm.util

import de.espirit.firstspirit.module.Configuration
import de.espirit.firstspirit.module.ServerEnvironment
import java.awt.Frame
import javax.swing.JComponent

open class BaseConfiguration: Configuration<ServerEnvironment> {

    override fun hasGui(): Boolean {
        return false
    }

    override fun getGui(frame: Frame?): JComponent? {
        return null
    }

    override fun load() {}

    override fun store() {}

    override fun getParameterNames(): Set<String?>? {
        return null
    }

    override fun getParameter(s: String?): String? {
        return null
    }

    override fun init(s: String?, s1: String?, t: ServerEnvironment?) {}

    override fun getEnvironment(): ServerEnvironment? {
        return null
    }

}