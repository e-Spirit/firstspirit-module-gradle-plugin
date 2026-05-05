plugins {
    `java-library`
    kotlin("jvm") version "2.2.21"
    id("de.espirit.firstspirit-module-annotations")
    id("org.springframework.boot") version "3.4.0"
}

dependencies {
    // Since this is in a webapp project, this should be included in the license report...
    implementation("org.slf4j:slf4j-api:2.0.16")

    // ...but not this compileOnly dependency
    compileOnly("de.espirit.firstspirit:fs-isolated-runtime:5.2.241212")

    // ...and also not this SpringBoot dependency
    developmentOnly("de.espirit.firstspirit:fs-isolated-webrt:5.2.241212")
}

springBoot {
    mainClass.set("MyWebApp")
}