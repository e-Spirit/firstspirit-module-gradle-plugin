plugins {
    id("java")
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
}
dependencies {
    implementation("com.espirit.moddev.components:annotations:2.0.0")
    compileOnly("de.espirit.firstspirit:fs-isolated-runtime:5.2.230212")
}