package com.espirit.moddev.gradletest;

import de.espirit.firstspirit.module.ProjectApp;
import de.espirit.firstspirit.module.ProjectEnvironment;
import de.espirit.firstspirit.module.descriptor.ProjectAppDescriptor;
import com.espirit.moddev.components.annotations.ProjectAppComponent;
import de.espirit.firstspirit.module.Configuration;

@ProjectAppComponent(name = "SimpleProjectApp",
        displayName = "SimpleProjectApp",
        description = "SimpleProjectApp",
        configurable = TestConfigurable,
        resources = [@Resource(path = "lib/guava-24.0.jar", name = "com.google.guava:guava", version = "24.0")])
public class SimpleProjectApp implements ProjectApp {
    @Override
    public void init(ProjectAppDescriptor projectAppDescriptor, ProjectEnvironment projectEnvironment) {

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
