package com.espirit.moddev.gradletest;

import com.espirit.moddev.components.annotations.WebAppComponent;
import de.espirit.firstspirit.module.Configuration;
import de.espirit.firstspirit.module.ServerEnvironment;
import de.espirit.firstspirit.module.WebApp;
import de.espirit.firstspirit.module.WebEnvironment;
import de.espirit.firstspirit.module.descriptor.WebAppDescriptor;

import javax.swing.*;
import java.awt.*;
import java.util.Set;

@WebAppComponent(name = "SimpleWebApp",
        displayName = "SimpleWebApp",
        description = "This is a simple WebApp",
        configurable = MyWebApp.WebAppConfigurable.class,
        webXml = "/webResourceFolder/web.xml")
public class MyWebApp implements WebApp {

    @Override
    public void createWar() {

    }

    @Override
    public void init(WebAppDescriptor var1, WebEnvironment var2) {}

    @Override
    public void installed() {

    }

    @Override
    public void uninstalling() {

    }

    @Override
    public void updated(String s) {

    }

    public static class WebAppConfigurable implements Configuration<ServerEnvironment> {

        @Override
        public boolean hasGui() {
            return false;
        }

        @Override
        public JComponent getGui(Frame var1) {
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
        public String getParameter(String var1) {
            return null;
        }

        @Override
        public void init(String var1, String var2, ServerEnvironment var3) {

        }

        @Override
        public ServerEnvironment getEnvironment() {
            return null;
        }
    }
}
