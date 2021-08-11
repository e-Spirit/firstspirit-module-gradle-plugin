plugins {
	java
}

dependencies {
	compileOnly("com.espirit.moddev.components:annotations:1.9.1")
	compileOnly("de.espirit.firstspirit:fs-isolated-runtime:5.2.200910")

	implementation("joda-time:joda-time:2.10")
	implementation("org.joda:joda-convert:2.1.1")
	implementation("org.slf4j:slf4j-api:1.7.24")
}
