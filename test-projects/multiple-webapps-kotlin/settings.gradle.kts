pluginManagement {
	repositories {
		val artifactory_username: String by settings
		val artifactory_password: String by settings
		maven {
			setUrl("https://artifactory.e-spirit.de/artifactory/repo/")
			credentials {
				username = artifactory_username
				password = artifactory_password
			}
		}
		gradlePluginPortal()
	}
}

rootProject.name = "multiple-webapps"

include("fsm", "libModule", "web_a", "web_b")
includeBuild("../../")
