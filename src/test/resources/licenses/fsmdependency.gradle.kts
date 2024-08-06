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
    fsModuleCompile(group = "com.fasterxml.jackson.core", name = "jackson-databind", version = "2.10.0")

    // - runtime classpath dependencies - not included in FSM, should not appear in license info
    implementation(group = "org.slf4j", name = "slf4j-api", version = "2.0.13")

    // - compile classpath dependencies - should not be included in license info
    compileOnly(group = "de.espirit.firstspirit", name = "fs-isolated-runtime", version = "5.2.220309")
    compileOnly(group = "joda-time", name = "joda-time", version = "2.9")

    // - test dependency - should also appear in FSM or license info
    testImplementation("org.junit.jupiter:junit-jupiter:5.9.2")
}
