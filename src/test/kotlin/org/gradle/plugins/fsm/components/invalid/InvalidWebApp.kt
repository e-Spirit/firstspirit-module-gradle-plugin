package org.gradle.plugins.fsm.components.invalid

import com.espirit.moddev.components.annotations.WebAppComponent
import de.espirit.firstspirit.module.WebApp

/**
 * Class does not implement [WebApp]
 */
@WebAppComponent(name = "InvalidWebApp", webXml = "web.xml")
class InvalidWebApp