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
    compileOnly("de.espirit.firstspirit:fs-isolated-runtime:5.2.230212")
    implementation(fsDependency("$guavaProperty:$guavaVersionProperty"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.9.2")
}