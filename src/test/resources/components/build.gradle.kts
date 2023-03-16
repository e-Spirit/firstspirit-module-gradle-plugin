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

val externalProject = project(":external-subproject")
evaluationDependsOn(":external-subproject")

dependencies {
    fsModuleCompile(project(":fsm-subproject"))
    // Simulate external project by depending on the jar file rather than the project directly
    implementation(externalProject.tasks.getByName("jar").outputs.files)
}