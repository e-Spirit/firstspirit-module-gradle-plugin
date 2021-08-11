package com.espirit.moddev;

import com.espirit.moddev.components.annotations.WebAppComponent;
import com.espirit.moddev.components.annotations.WebResource;
import de.espirit.firstspirit.module.AbstractWebApp;
import de.espirit.firstspirit.module.*;
import de.espirit.firstspirit.server.module.ModuleInfo;

import javax.swing.*;
import java.awt.*;
import java.lang.annotation.Annotation;
import java.util.Set;

@WebAppComponent(name = "SimpleWebApp",
        displayName = "SimpleWebApp",
        description = "This is a simple WebApp",
        configurable = SimpleWebApp.WebAppConfigurable.class,
        webXml = "webResourceFolder/web.xml",
        webResources = {
            @WebResource(path = "someResources/icon.png", name = "${project.webappIconName}", version = "${project.version}", targetPath = "img"),
            @WebResource(path = "$path", name = "${project.commonsIOWebDependencyName}", version = "${version}", targetPath = "lib")
        }
)
public class SimpleWebApp extends AbstractWebApp {
    public static class WebAppConfigurable implements Configuration<ServerEnvironment> {

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
        public void init(String var1, String var2, ServerEnvironment var3) {

        }
        @Override
        public ServerEnvironment getEnvironment() {
            return null;
        }
    }

}
