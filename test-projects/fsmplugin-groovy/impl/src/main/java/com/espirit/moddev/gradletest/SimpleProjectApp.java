package com.espirit.moddev.gradletest;

import com.espirit.moddev.components.annotations.Resource;
import de.espirit.firstspirit.module.ProjectApp;
import de.espirit.firstspirit.module.ProjectEnvironment;
import de.espirit.firstspirit.module.descriptor.ProjectAppDescriptor;
import com.espirit.moddev.components.annotations.ProjectAppComponent;
import de.espirit.firstspirit.module.Configuration;
import de.espirit.firstspirit.module.ServerEnvironment;

import javax.swing.*;
import java.awt.*;
import java.util.Set;

@ProjectAppComponent(name = "SimpleProjectApp",
        displayName = "SimpleProjectApp",
        description = "SimpleProjectApp",
        configurable = SimpleProjectApp.TestConfigurable.class,
        resources = {@Resource(path = "$path", name = "${project.guavaProperty}", version = "$version")})
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

    static class TestConfigurable implements Configuration<ServerEnvironment> {

        @Override
        public boolean hasGui() {
            return false;
        }

        @Override
        public JComponent getGui(Frame applicationFrame) {
            return null;
        }

        @Override
        public void load() {

        }

        @Override
        public void store() {

        }

        @Override
        public Set<String> getParameterNames() {
            return null;
        }

        @Override
        public String getParameter(String s) {
            return null;
        }

        @Override
        public void init(String moduleName, String componentName, ServerEnvironment env) {

        }

        @Override
        public ServerEnvironment getEnvironment() {
            return null;
        }
    }
}
