plugins {
    id("de.espirit.firstspirit-module")
}

group = "de.espirit"
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
    // Test dependency is irrelevant for licensing
    testImplementation("org.junit.jupiter:junit-jupiter:5.9.2")

    // use one, real, dependency
    // it needs:
    // - license info
    // - some transitive dependencies which also have license info
    implementation(group = "com.fasterxml.jackson.core", name = "jackson-databind", version = "2.10.0")

    // - compile classpath dependencies - should not be included in licenseInfo
    compileOnly(group = "de.espirit.firstspirit", name = "fs-isolated-runtime", version = "5.2.220309")
    compileOnly(group = "joda-time", name = "joda-time", version = "2.9")
}
