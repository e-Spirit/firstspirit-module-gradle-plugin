plugins {
	java
}

dependencies {
	compileOnly("com.espirit.moddev.components:annotations:2.0.0")
	compileOnly("de.espirit.firstspirit:fs-isolated-runtime:5.2.220309")

	implementation("com.fasterxml.jackson.core:jackson-databind:2.10.0")
	implementation("org.slf4j:slf4j-api:1.7.25")

	implementation(project(":libModule"))
}

tasks {
	jar {
		archiveBaseName.set("my-web_b")
	}
}
