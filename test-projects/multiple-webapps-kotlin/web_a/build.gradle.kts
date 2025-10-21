plugins {
	java
}

dependencies {
	compileOnly("de.espirit.firstspirit:fs-isolated-runtime:5.2.251108")

	implementation("joda-time:joda-time:2.14.0")
	implementation("org.joda:joda-convert:2.2.4")
	implementation("org.slf4j:slf4j-api:2.0.17")

	implementation(project(":libModule"))
}

tasks {
	jar {
		archiveBaseName.set("my-web_a")
	}
}