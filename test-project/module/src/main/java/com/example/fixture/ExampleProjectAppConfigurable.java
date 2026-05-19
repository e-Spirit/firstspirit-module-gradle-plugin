package com.example.fixture;

import de.espirit.firstspirit.module.Configuration;
import de.espirit.firstspirit.module.ProjectEnvironment;

import javax.swing.JComponent;
import java.awt.Frame;
import java.util.Set;

public class ExampleProjectAppConfigurable implements Configuration<ProjectEnvironment> {

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
    public String getParameter(String name) {
        return null;
    }

    @Override
    public void init(String moduleName, String componentName, ProjectEnvironment env) {
    }

    @Override
    public ProjectEnvironment getEnvironment() {
        return null;
    }
}
