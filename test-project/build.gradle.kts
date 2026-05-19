plugins {
    id("base")
}

allprojects {
    version = "0.0.1-SNAPSHOT"

    repositories {
        maven(url = "https://artifactory.e-spirit.de/artifactory/repo/") {
            credentials {
                username = property("artifactory_username") as String
                password = property("artifactory_password") as String
            }
        }
    }
}

subprojects {
    // Single Java toolchain for all subprojects; version from gradle.properties.
    plugins.withType<JavaPlugin> {
        extensions.configure<JavaPluginExtension> {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of((property("jdk-version") as String).toInt()))
            }
        }
    }
}
