package de.espirit;

import com.espirit.moddev.components.annotations.WebAppComponent;
import de.espirit.firstspirit.module.AbstractWebApp;
import de.espirit.firstspirit.module.WebApp;

/**
 * Web app implementing {@link AbstractWebApp}. Should be recognized, even though it does
 * not implement {@link WebApp} directly.
 */
@WebAppComponent(name = "AbstractWebAppImpl", webXml = "web.xml")
public class AbstractWebAppImpl extends AbstractWebApp {
}