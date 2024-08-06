plugins {
    id("de.espirit.firstspirit-module")
}

group = "com.crownpeak"
version = "1.0-SNAPSHOT"

repositories {
    maven(url = "https://artifactory.e-spirit.de/artifactory/repo") {
        credentials {
            username = "${System.getProperty("artifactory_username")}"
            password = "${System.getProperty("artifactory_password")}"
        }
    }
}

dependencies {
    // TableLayout has a licenses.csv entry containing quotes, which must be escaped correctly
    fsModuleCompile(group = "tablelayout", name = "TableLayout", version = "20050920")
}
