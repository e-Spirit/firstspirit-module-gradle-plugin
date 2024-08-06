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

val customLib: Configuration by configurations.creating

dependencies {
    // custom library dependency should be included
    customLib(group = "joda-time", name = "joda-time", version = "2.12.2")

    // FSM dependency
    fsModuleCompile(group = "com.fasterxml.jackson.core", name = "jackson-databind", version = "2.10.0")
}

firstSpiritModule {
    libraries {
        create("libWithCustomConfiguration") {
            displayName = "Library with custom configuration"
            description = "A library component defined by a custom Gradle configuration"
            configuration = customLib
        }
    }
}
