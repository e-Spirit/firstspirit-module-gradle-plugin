plugins {
    java
    id("de.espirit.firstspirit-module-annotations")
}

val fsRuntimeVersion = property("fsRuntimeVersion") as String

dependencies {
    compileOnly("de.espirit.firstspirit:fs-isolated-runtime:$fsRuntimeVersion")
    compileOnly(project(":server"))
}
