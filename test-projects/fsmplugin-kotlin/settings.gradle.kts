pluginManagement {
    repositories {
        val artifactory_hosting_username: String by settings
        val artifactory_hosting_password: String by settings
        maven(url = "https://artifactory.e-spirit.hosting/artifactory/repo/") {
            credentials {
                username = artifactory_hosting_username
                password = artifactory_hosting_password
            }
        }
    }
}

rootProject.name = "test-project"

include("api", "impl", "fsm", "web")
includeBuild("../../")