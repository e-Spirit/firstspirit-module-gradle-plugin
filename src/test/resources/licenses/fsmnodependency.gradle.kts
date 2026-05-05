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
    // - compile classpath dependencies - should not be included in licenseInfo
    compileOnly("de.espirit.firstspirit:fs-isolated-runtime:5.2.220309")
    compileOnly("joda-time:joda-time:2.9")

    // - test dependency - should also appear in FSM or license info
    testImplementation("org.junit.jupiter:junit-jupiter:5.9.2")
}
