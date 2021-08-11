package de.espirit.multiwebapp;

import com.espirit.moddev.components.annotations.WebAppComponent;
import de.espirit.firstspirit.module.WebApp;
import de.espirit.firstspirit.module.WebEnvironment;
import de.espirit.firstspirit.module.descriptor.WebAppDescriptor;

@WebAppComponent(name = "WebAppA", webXml = "web_a/web.xml")
public class WebAppA implements WebApp {
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
}
