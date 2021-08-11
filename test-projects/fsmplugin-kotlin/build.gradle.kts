plugins {
    id("base")
}

extra["guavaProperty"] = "com.google.guava:guava"
extra["guavaVersionProperty"] = "24.0-jre"

allprojects {

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