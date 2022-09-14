package org.gradle.plugins.fsm.util

import org.gradle.api.Project
import java.net.URI

object TestProjectUtils {

    fun defineArtifactoryForProject(project: Project) {
        project.extensions.add("artifactory_username", System.getProperty("artifactory_username"))
        project.extensions.add("artifactory_password", System.getProperty("artifactory_password"))

        with(project.repositories) {
            maven {
                it.url = URI.create("https://artifactory.e-spirit.de/artifactory/repo")
                it.credentials.username = project.extensions.getByName("artifactory_username") as String
                it.credentials.password = project.extensions.getByName("artifactory_password") as String
            }
        }
    }

}