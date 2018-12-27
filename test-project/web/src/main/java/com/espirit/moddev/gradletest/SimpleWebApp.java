package com.espirit.moddev.gradletest;

import com.espirit.moddev.components.annotations.WebAppComponent;
import com.espirit.moddev.components.annotations.WebResource;

import de.espirit.firstspirit.module.Configuration;
import de.espirit.firstspirit.module.WebApp;
import de.espirit.firstspirit.module.WebEnvironment;
import de.espirit.firstspirit.module.descriptor.WebAppDescriptor;
import de.espirit.firstspirit.module.ServerEnvironment;

import javax.swing.*;
import java.awt.*;
import java.util.Set;

@WebAppComponent(name = "SimpleWebApp",
        displayName = "SimpleWebApp",
        description = "SimpleWebApp",
        configurable = SimpleWebApp.TestConfigurable.class,
        webXml = "/test/web.xml",
        webResources = {@WebResource(path = "$path", name = "io.github.lukehutch:fast-classpath-scanner", version = "$version")})
public class SimpleWebApp implements WebApp {
    @Override
    public void createWar() {

    }

    @Override
    public void init(WebAppDescriptor webAppDescriptor, WebEnvironment webEnvironment) {

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
