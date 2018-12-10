package org.gradle.plugins.fsm.util

import org.gradle.api.Project

class TestProjectUtils {
    static void setArtifactoryCredentialsFromLocalProperties(Project project) {
        project.ext {
            artifactory_username = System.getProperty("artifactory_username")
            artifactory_password = System.getProperty("artifactory_password")
        }
    }

    static void defineArtifactoryForProject(Project project) {
        project.repositories {
            maven {
                url 'https://artifactory.e-spirit.de/artifactory/repo'
                credentials {
                    username = project.artifactory_username
                    password = project.artifactory_password
                }
            }
        }
    }
}
