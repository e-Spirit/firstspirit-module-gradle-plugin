package org.gradle.plugins.fsm

import com.espirit.moddev.components.annotations.WebAppComponent
import com.espirit.moddev.components.annotations.WebResource
import de.espirit.firstspirit.module.WebApp
import de.espirit.firstspirit.module.WebEnvironment
import de.espirit.firstspirit.module.descriptor.WebAppDescriptor

abstract class TestWebApp implements WebApp {
	@Override
	void init(WebAppDescriptor w, WebEnvironment e) {
	}

	@Override
	void createWar() {
	}

	@Override
	void installed() {
	}

	@Override
	void uninstalling() {
	}

	@Override
	void updated(String var1) {
	}
}

@WebAppComponent(name = "TestWebAppA", webXml = "a/web.xml")
class TestWebAppA extends TestWebApp {
}

@WebAppComponent(name = "TestWebAppB", webXml = "b/web.xml")
class TestWebAppB extends TestWebApp {
}

@WebAppComponent(name = "WebApp with project properties", webXml = "/abc/nonexistent.txt",
		webResources = [
				@WebResource(name = "\${project.group}-\${project.name}-res", version = "\${project.myCustomVersionPropertyString}", path = "/abc/nonexistent.txt"),
				@WebResource(name = "\${project.group}-\${project.name}-res2", version = "\${project.version}", path = "/abc/nonexistent2.txt")
		])
class TestWebAppWithProjectProperties extends TestWebApp {

}