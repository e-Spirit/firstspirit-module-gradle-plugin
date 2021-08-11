import org.gradle.plugins.fsm.configurations.fsDependency

plugins {
    id("de.espirit.firstspirit-module-configurations")
}

dependencies {
    implementation(fsDependency("junit:junit:4.12", true))
}