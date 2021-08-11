plugins {
    id("base")
}

subprojects {

    version = "0.0.1-SNAPSHOT"

    repositories {
        maven {
            setUrl("https://artifactory.e-spirit.de/artifactory/repo/")
            credentials {
                username = property("artifactory_username") as String
                password = property("artifactory_password") as String
            }
        }
    }

}