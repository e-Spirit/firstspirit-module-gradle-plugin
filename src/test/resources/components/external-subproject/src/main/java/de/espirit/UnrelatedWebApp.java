package de.espirit;

import com.espirit.moddev.components.annotations.WebAppComponent;
import de.espirit.firstspirit.module.AbstractWebApp;

/**
 * External web app that is not included in the FSM archive. Should not be recognized as a component
 * in the {@code module-isolated.xml}
 */
@WebAppComponent(name = "UnrelatedWebApp", webXml = "web.xml")
public class UnrelatedWebApp extends AbstractWebApp {
}