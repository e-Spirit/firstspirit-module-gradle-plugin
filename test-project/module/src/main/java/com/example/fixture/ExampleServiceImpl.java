package com.example.fixture;

import com.espirit.moddev.components.annotations.ServiceComponent;
import de.espirit.firstspirit.module.ServerEnvironment;
import de.espirit.firstspirit.module.Service;
import de.espirit.firstspirit.module.ServiceProxy;
import de.espirit.firstspirit.module.descriptor.ServiceDescriptor;

@ServiceComponent(name = "ExampleService")
public class ExampleServiceImpl implements ExampleService, Service<ExampleService> {

    private boolean _running;

    @Override
    public void start() {
        _running = true;
    }

    @Override
    public void stop() {
        _running = false;
    }

    @Override
    public boolean isRunning() {
        return _running;
    }

    @Override
    public Class<? extends ExampleService> getServiceInterface() {
        return ExampleService.class;
    }

    @Override
    public Class<? extends ServiceProxy<ExampleService>> getProxyClass() {
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
    public void updated(String oldVersion) {
    }
}
