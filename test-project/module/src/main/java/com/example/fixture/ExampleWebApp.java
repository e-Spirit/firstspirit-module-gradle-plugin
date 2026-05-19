package com.example.fixture;

import com.espirit.moddev.components.annotations.WebAppComponent;
import de.espirit.firstspirit.module.WebApp;
import de.espirit.firstspirit.module.WebEnvironment;
import de.espirit.firstspirit.module.descriptor.WebAppDescriptor;

@WebAppComponent(
        name = "ExampleWebApp",
        displayName = "Example WebApp",
        description = "Example WebApp",
        xmlSchemaVersion = "6.0",
        webXml = "example-web/web.xml")
public class ExampleWebApp implements WebApp {

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
    public void updated(String oldVersion) {
    }
}
