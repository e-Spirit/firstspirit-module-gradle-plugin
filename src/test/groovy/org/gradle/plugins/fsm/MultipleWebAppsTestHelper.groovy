package org.gradle.plugins.fsm

import com.espirit.moddev.components.annotations.WebAppComponent
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
