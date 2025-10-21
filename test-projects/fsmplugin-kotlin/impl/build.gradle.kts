import org.gradle.plugins.fsm.configurations.fsDependency

plugins {
    java
    id("de.espirit.firstspirit-module-configurations")
    id("de.espirit.firstspirit-module-annotations")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
}

val guavaProperty = rootProject.extra["guavaProperty"] as String
val guavaVersionProperty = rootProject.extra["guavaVersionProperty"] as String

dependencies {
    compileOnly("de.espirit.firstspirit:fs-isolated-runtime:5.2.251108")
    implementation(fsDependency("$guavaProperty:$guavaVersionProperty"))
    testImplementation("org.junit.jupiter:junit-jupiter:6.0.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}