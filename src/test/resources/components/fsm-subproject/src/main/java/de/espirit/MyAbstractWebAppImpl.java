package de.espirit;

import com.espirit.moddev.components.annotations.WebAppComponent;
import de.espirit.MyAbstractWebApp;
import de.espirit.firstspirit.module.WebApp;

/**
 * Web app implementing {@link MyAbstractWebApp}, which comes from an external Jar.
 * Should be recognized, since MyAbstractWebApp implements {@link WebApp}
 */
@WebAppComponent(name = "MyAbstractWebAppImpl", webXml = "web.xml")
public class MyAbstractWebAppImpl extends MyAbstractWebApp {
}