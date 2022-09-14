plugins {
    id("de.espirit.firstspirit-module")
}

group = "de.espirit"
version = "2.7.5"

dependencies {
    fsModuleCompile(project(":subproject"))
}

// Override some attributes in the META-INF/MANIFEST.MF file located in the FSM
// ...during configuration
tasks.assembleFSM {
    manifest.attributes["Build-Jdk"] = "Custom-Jdk"
}

// ...and during runtime
tasks.assembleFSM {
    doFirst {
        manifest.attributes["Custom-Key"] = "Custom-Value"
    }
}

repositories {
    maven(url = "https://artifactory.e-spirit.de/artifactory/repo") {
        credentials {
            username = "${System.getProperty("artifactory_username")}"
            password = "${System.getProperty("artifactory_password")}"
        }
    }
}