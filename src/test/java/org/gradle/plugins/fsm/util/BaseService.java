package org.gradle.plugins.fsm.util;

import de.espirit.firstspirit.module.ServerEnvironment;
import de.espirit.firstspirit.module.Service;
import de.espirit.firstspirit.module.ServiceProxy;
import de.espirit.firstspirit.module.descriptor.ServiceDescriptor;

public class BaseService implements Service<ServerEnvironment> {
    @Override
    public void start() {

    }

    @Override
    public void stop() {

    }

    @Override
    public boolean isRunning() {
        return false;
    }

    @Override
    public Class<? extends ServerEnvironment> getServiceInterface() {
        return null;
    }

    @Override
    public Class<? extends ServiceProxy<ServerEnvironment>> getProxyClass() {
        return null;
    }

    @Override
    public void init(ServiceDescriptor serviceDescriptor, ServerEnvironment serverEnvironment) {

    }

    @Override
    public void installed() {

    }

    @Override
    public void uninstalling() {

    }

    @Override
    public void updated(String s) {

    }
}
