plugins {
    id("de.espirit.firstspirit-module")
}

allprojects {
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
}

firstSpiritModule {
    webAppComponent("MyWebApp", project(":webapp-project"))
}
