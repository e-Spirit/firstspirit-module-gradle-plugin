plugins {
    id("base")
}

subprojects {

    version = "0.0.1-SNAPSHOT"

    repositories {
        maven(url = "https://artifactory.e-spirit.hosting/artifactory/repo/") {
            credentials {
                username = property("artifactory_hosting_username") as String
                password = property("artifactory_hosting_password") as String
            }
        }
    }

}