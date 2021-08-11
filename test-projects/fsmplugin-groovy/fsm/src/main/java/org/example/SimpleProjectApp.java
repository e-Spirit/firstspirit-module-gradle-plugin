package org.example;

import com.espirit.moddev.components.annotations.ProjectAppComponent;
import de.espirit.firstspirit.module.ProjectApp;
import de.espirit.firstspirit.module.ServerEnvironment;
import de.espirit.firstspirit.module.ProjectEnvironment;
import de.espirit.firstspirit.module.descriptor.ProjectAppDescriptor;
import de.espirit.firstspirit.module.Configuration;

import javax.swing.*;
import java.awt.*;
import java.util.Set;

@ProjectAppComponent(name = "SimpleProjectApp",
        displayName = "SimpleProjectApp", description = "Just a simple ProjectApp",
        configurable = SimpleProjectApp.SimpleProjectAppConfigurable.class)
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

    public static class SimpleProjectAppConfigurable implements Configuration<ProjectEnvironment> {

        @Override
        public boolean hasGui(){
            return false;
        }

        @Override
        public JComponent getGui(Frame var1){
            return null;
        }

        @Override
        public void load(){

        }

        @Override
        public void store(){

        }

        @Override
        public Set<String> getParameterNames(){
            return null;
        }

        @Override
        public String getParameter(String var1){
            return null;
        }

        @Override
        public void init(String var1, String var2, ProjectEnvironment var3) {

        }
        @Override
        public ProjectEnvironment getEnvironment() {
            return null;
        }
    }
}
