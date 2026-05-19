pluginManagement {
    repositories {
        val artifactory_username: String by settings
        val artifactory_password: String by settings
        maven(url = "https://artifactory.e-spirit.de/artifactory/repo/") {
            credentials {
                username = artifactory_username
                password = artifactory_password
            }
        }
    }
}

plugins {
    id("com.crownpeak.plugins.gradle.artifactory-toolchain-resolver") version "latest.release"
}

toolchainManagement {
    jvm {
        javaRepositories {
            val artifactory_username: String by settings
            val artifactory_password: String by settings

            repository("artifactory") {
                resolverClass.set(com.crownpeak.plugins.gradle.toolchain.ArtifactoryToolchainResolver::class.java)
                credentials {
                    username = artifactory_username
                    password = artifactory_password
                }
            }
        }
    }
}

rootProject.name = "test-project"

include("server", "module", "web", "fsm")

// Consume the FSMGP under development from this repo via composite build.
includeBuild("../")
