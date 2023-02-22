package de.espirit;

import de.espirit.firstspirit.module.WebApp;
import de.espirit.firstspirit.module.WebEnvironment;
import de.espirit.firstspirit.module.descriptor.WebAppDescriptor;


public abstract class MyAbstractWebApp implements WebApp {

	@Override
	public void createWar() {
	}


	@Override
	public void init(final WebAppDescriptor webAppDescriptor, final WebEnvironment webEnvironment) {
	}


	@Override
	public void installed() {
	}


	@Override
	public void uninstalling() {
	}


	@Override
	public void updated(final String oldVersionString) {
	}
}
