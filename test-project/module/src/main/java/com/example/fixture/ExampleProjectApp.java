package com.example.fixture;

import com.espirit.moddev.components.annotations.ProjectAppComponent;
import com.espirit.moddev.components.annotations.Resource;
import de.espirit.firstspirit.module.ProjectApp;
import de.espirit.firstspirit.module.ProjectEnvironment;
import de.espirit.firstspirit.module.descriptor.ProjectAppDescriptor;

@ProjectAppComponent(
        name = "ExampleProjectApp",
        displayName = "Example ProjectApp",
        description = "Example ProjectApp with a Configurable",
        configurable = ExampleProjectAppConfigurable.class,
        resources = {
                @Resource(path = "$path", name = "tools.jackson.core:jackson-databind", version = "$version"),
                @Resource(path = "$path", name = "com.fasterxml.jackson.core:jackson-annotations", version = "$version")
        }
)
public class ExampleProjectApp implements ProjectApp {

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
    public void updated(String oldVersion) {
    }
}
