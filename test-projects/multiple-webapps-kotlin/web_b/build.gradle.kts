plugins {
	java
}

dependencies {
	compileOnly("com.espirit.moddev.components:annotations:1.9.1")
	compileOnly("de.espirit.firstspirit:fs-isolated-runtime:5.2.200910")

	implementation("com.fasterxml.jackson.core:jackson-databind:2.10.0")
	implementation("org.slf4j:slf4j-api:1.7.25")
}
