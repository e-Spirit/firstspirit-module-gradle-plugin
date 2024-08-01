package org.gradle.plugins.fsm.util

import de.espirit.firstspirit.module.ServerEnvironment
import de.espirit.firstspirit.module.Service
import de.espirit.firstspirit.module.ServiceProxy
import de.espirit.firstspirit.module.descriptor.ServiceDescriptor

open class BaseService: BaseServiceItf, Service<BaseServiceItf> {

    override fun start() {}

    override fun stop() {}

    override fun isRunning(): Boolean {
        return false
    }

    override fun getServiceInterface(): Class<out BaseServiceItf> {
        return BaseServiceItf::class.java
    }

    override fun getProxyClass(): Class<out ServiceProxy<BaseServiceItf>>? {
        return null
    }

    override fun init(serviceDescriptor: ServiceDescriptor?, serverEnvironment: ServerEnvironment?) {}

    override fun installed() {}

    override fun uninstalling() {}

    override fun updated(s: String?) {}

}