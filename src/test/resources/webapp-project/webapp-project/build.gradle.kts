plugins {
    `java-library`
    id("de.espirit.firstspirit-module-annotations")
    id("org.springframework.boot") version "3.4.0"
}

dependencies {
    // Since this is in a webapp project, this should be included in the license report...
    implementation(group = "org.slf4j", name = "slf4j-api", version = "2.0.16")

    // ...but not this compileOnly dependency
    compileOnly(group = "de.espirit.firstspirit", name = "fs-isolated-runtime", version = "5.2.241212")

    // ...and also not this SpringBoot dependency
    developmentOnly(group = "de.espirit.firstspirit", name = "fs-isolated-webrt", version = "5.2.241212")
}

springBoot {
    mainClass.set("MyWebApp")
}