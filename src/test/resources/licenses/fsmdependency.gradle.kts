plugins {
    id("de.espirit.firstspirit-module")
}

group = "com.crownpeak"
version = "1.0-SNAPSHOT"

repositories {
    maven(url = "https://artifactory.e-spirit.de/artifactory/repo") {
        credentials {
            username = "${System.getProperty("artifactory_username")}"
            password = "${System.getProperty("artifactory_password")}"
        }
    }
}

dependencies {
    // FSM dependency
    // it needs:
    // - license info
    // - some transitive dependencies which also have license info
    fsModuleCompile("com.fasterxml.jackson.core:jackson-databind:2.10.0")

    // - runtime classpath dependencies - not included in FSM, should not appear in license info
    implementation("org.slf4j:slf4j-api:2.0.13")

    // - compile classpath dependencies - should not be included in license info
    compileOnly("de.espirit.firstspirit:fs-isolated-runtime:5.2.220309")
    compileOnly("joda-time:joda-time:2.9")

    // - test dependency - should also appear in FSM or license info
    testImplementation("org.junit.jupiter:junit-jupiter:5.9.2")
}
