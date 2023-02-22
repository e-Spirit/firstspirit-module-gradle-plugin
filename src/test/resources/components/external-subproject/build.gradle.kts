plugins {
    id("java-library")
    id("de.espirit.firstspirit-module-annotations")
}

dependencies {
    compileOnly(group = "de.espirit.firstspirit", name = "fs-isolated-runtime", version = "5.2.220309")
}