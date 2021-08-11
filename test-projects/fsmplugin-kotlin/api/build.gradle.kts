plugins {
    id("java")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
}
dependencies {
    implementation("com.espirit.moddev.components:annotations:1.9.1")
    compileOnly("de.espirit.firstspirit:fs-isolated-runtime:5.2.200910")
}