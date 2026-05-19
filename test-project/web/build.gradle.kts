plugins {
    java
    id("de.espirit.firstspirit-module-annotations")
}

val fsRuntimeVersion = property("fsRuntimeVersion") as String

dependencies {
    // FirstSpirit API at compile time; provided by the server at runtime.
    compileOnly("de.espirit.firstspirit:fs-isolated-runtime:$fsRuntimeVersion")

    // :web carries the WebApp's static resources under src/main/fsm-resources/example-web/,
    // plus any deps that should ship as web resources of the WebApp. jsoup also exercises
    // web-scope dependency inclusion for the Java sources compiled here.
    implementation("org.jsoup:jsoup:1.22.2")
}
