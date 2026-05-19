plugins {
    java
}

val fsRuntimeVersion = property("fsRuntimeVersion") as String

dependencies {
    compileOnly("de.espirit.firstspirit:fs-isolated-runtime:$fsRuntimeVersion")
}
