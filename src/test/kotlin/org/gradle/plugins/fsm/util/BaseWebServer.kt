package org.gradle.plugins.fsm.util

import de.espirit.firstspirit.module.ServerEnvironment
import de.espirit.firstspirit.module.WebServer
import de.espirit.firstspirit.module.descriptor.WebServerDescriptor

open class BaseWebServer: WebServer {

    override fun init(descriptor: WebServerDescriptor?, env: ServerEnvironment?) {
    }

    override fun installed() {
    }

    override fun uninstalling() {
    }

    override fun updated(oldVersionString: String?) {
    }

    override fun deploy(webAppName: String, contextName: String, targetPath: String, warFilePath: String) {
    }

    override fun undeploy(webAppName: String, contextName: String, targetPath: String) {
    }

    override fun isDeployed(webAppName: String, contextName: String, targetPath: String): Boolean {
        return false
    }

    override fun supportsDeployState(): Boolean {
        return true
    }

    override fun supportsDeploy(): Boolean {
        return true
    }

    override fun supportsUndeploy(): Boolean {
        return true
    }

    override fun isHidden(): Boolean {
        return false
    }

    override fun getURL(): String {
        return ""
    }

    override fun getInternalURL(): String? {
        return null
    }

    override fun getWebAppDir(): String? {
        return null
    }

    override fun getContextPath(contextName: String?, targetPath: String?): String {
        return ""
    }

}