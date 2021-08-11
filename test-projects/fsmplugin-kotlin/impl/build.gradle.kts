import org.gradle.plugins.fsm.configurations.fsDependency

plugins {
    java
    id("de.espirit.firstspirit-module-configurations")
    id("de.espirit.firstspirit-module-annotations")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
}

val guavaProperty = rootProject.extra["guavaProperty"] as String
val guavaVersionProperty = rootProject.extra["guavaVersionProperty"] as String

dependencies {
    implementation(fsDependency("junit:junit:4.12", true))
    compileOnly("de.espirit.firstspirit:fs-isolated-runtime:5.2.200910")
    implementation("$guavaProperty:$guavaVersionProperty")
}