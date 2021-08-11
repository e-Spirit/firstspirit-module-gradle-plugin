import org.gradle.plugins.fsm.configurations.fsDependency

plugins {
    id("de.espirit.firstspirit-module-configurations")
    id("de.espirit.firstspirit-module-annotations")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
}

val commonsIOWebDependencyName = "commons-io:commons-io"
rootProject.extra["webappIconName"] = "com.espirit.moddev.example.icon.png"
rootProject.extra["commonsIOWebDependencyName"] = commonsIOWebDependencyName

dependencies {
    compileOnly("de.espirit.firstspirit:fs-isolated-runtime:5.2.200910")
    fsWebCompile(fsDependency(mapOf("dependency" to "org.apache.commons:commons-lang3:3.8.1", "skipInLegacy" to true)))
    implementation("$commonsIOWebDependencyName:2.6")
}