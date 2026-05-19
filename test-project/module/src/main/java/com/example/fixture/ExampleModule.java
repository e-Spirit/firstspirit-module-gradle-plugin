package com.example.fixture;

import com.espirit.moddev.components.annotations.ModuleComponent;
import de.espirit.firstspirit.module.Module;
import de.espirit.firstspirit.module.ServerEnvironment;
import de.espirit.firstspirit.module.descriptor.ModuleDescriptor;

@ModuleComponent
public class ExampleModule implements Module {

    @Override
    public void init(ModuleDescriptor moduleDescriptor, ServerEnvironment serverEnvironment) {
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
