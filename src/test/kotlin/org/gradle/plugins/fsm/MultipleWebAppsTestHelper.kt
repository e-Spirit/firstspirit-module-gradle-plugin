package org.gradle.plugins.fsm

import com.espirit.moddev.components.annotations.WebAppComponent
import com.espirit.moddev.components.annotations.WebResource
import de.espirit.firstspirit.module.WebApp
import de.espirit.firstspirit.module.WebEnvironment
import de.espirit.firstspirit.module.descriptor.WebAppDescriptor

abstract class TestWebApp: WebApp {
    override fun init(w: WebAppDescriptor, e: WebEnvironment) {
    }

    override fun createWar() {
    }

    override fun installed() {
    }

    override fun uninstalling() {
    }

    override fun updated(var1: String) {
    }
}

@WebAppComponent(name = "TestWebAppA", webXml = "a/web.xml")
class TestWebAppA: TestWebApp()

@WebAppComponent(name = "TestWebAppB", webXml = "b/web.xml")
class TestWebAppB: TestWebApp()

@WebAppComponent(name = "WebApp with project properties", webXml = "/abc/nonexistent.txt",
    webResources = [
        WebResource(name = "\${project.group}-\${project.name}-res", version = "\${project.myCustomVersionPropertyString}", path = "/abc/nonexistent.txt"),
        WebResource(name = "\${project.group}-\${project.name}-res2", version = "\${project.version}", path = "/abc/nonexistent2.txt")
    ])
class TestWebAppWithProjectProperties: TestWebApp()