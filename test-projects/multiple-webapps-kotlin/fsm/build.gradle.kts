plugins {
	id("de.espirit.firstspirit-module")
}

dependencies {
	compileOnly("de.espirit.firstspirit:fs-isolated-runtime:5.2.251108")
	fsWebCompile("joda-time:joda-time:2.14.0")
	fsModuleCompile("org.joda:joda-convert:2.2.4")
}

firstSpiritModule {
	moduleName = "Test Multiple WebApps"
	webAppComponent("WebAppA", project(":web_a"))
	webAppComponent("WebAppB", project(":web_b"))
}