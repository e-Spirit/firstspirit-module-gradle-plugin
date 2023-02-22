plugins {
    id("de.espirit.firstspirit-module")
}

allprojects {
    group = "de.espirit"
    version = "1.0"
    repositories {
        maven(url = "https://artifactory.e-spirit.de/artifactory/repo") {
            credentials {
                username = "${System.getProperty("artifactory_username")}"
                password = "${System.getProperty("artifactory_password")}"
            }
        }
    }
}

dependencies {
    fsModuleCompile(project(":fsm-subproject"))
}