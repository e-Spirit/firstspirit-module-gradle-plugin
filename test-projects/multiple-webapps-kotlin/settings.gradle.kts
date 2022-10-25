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

rootProject.name = "multiple-webapps"

include("fsm", "libModule", "web_a", "web_b")
includeBuild("../../")
