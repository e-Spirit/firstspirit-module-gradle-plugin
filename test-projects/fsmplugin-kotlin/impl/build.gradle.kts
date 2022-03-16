import org.gradle.plugins.fsm.configurations.fsDependency

plugins {
    java
    id("de.espirit.firstspirit-module-configurations")
    id("de.espirit.firstspirit-module-annotations")
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
}

val guavaProperty = rootProject.extra["guavaProperty"] as String
val guavaVersionProperty = rootProject.extra["guavaVersionProperty"] as String

dependencies {
    implementation(fsDependency("junit:junit:4.12"))
    compileOnly("de.espirit.firstspirit:fs-isolated-runtime:5.2.220309")
    implementation("$guavaProperty:$guavaVersionProperty")
}