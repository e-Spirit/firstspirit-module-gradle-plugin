plugins {
    id("java-library")
}

version = "3.0.0"

// Override some attributes in the META-INF/MANIFEST.MF file located in the project jar
// ...during configuration
tasks.jar {
    manifest.attributes["Build-Jdk"] = "Custom-Jdk"
}

// ...and during runtime
tasks.jar {
    doFirst {
        manifest.attributes["Custom-Key"] = "Custom-Value"
    }
}