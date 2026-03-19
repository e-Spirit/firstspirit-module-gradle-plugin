import org.gradle.plugins.fsm.configurations.fsDependency

plugins {
    id("de.espirit.firstspirit-module-configurations")
    id("de.espirit.firstspirit-module-annotations")
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
}

dependencies {
    compileOnly("de.espirit.firstspirit:fs-isolated-runtime:5.2.251108")
    fsWebCompile(fsDependency(mapOf("dependency" to "org.apache.commons:commons-lang3:3.19.0")))
    implementation("commons-io:commons-io:2.20.0")
}