import de.espirit.mavenplugins.fsmchecker.ComplianceLevel.HIGHEST
import org.gradle.plugins.fsm.configurations.fsDependency

plugins {
    java
    id("de.espirit.firstspirit-module")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

val customLib: Configuration by configurations.creating

dependencies {
    compileOnly("de.espirit.firstspirit:fs-isolated-runtime:5.2.251108")

    fsServerCompile(project(":api"))
    fsModuleCompile(project(":impl"))
    fsWebCompile(project(":web"))
    fsModuleCompile(project(":web"))
    fsModuleCompile(fsDependency(mapOf("dependency" to "joda-time:joda-time:2.14.0")))
    fsModuleCompile(fsDependency("commons-logging:commons-logging:1.2", "1.0", "1.5"))
    fsModuleCompile(fsDependency("org.apache.activemq:activemq-all:6.1.7"))

    fsWebCompile(fsDependency("org.apache.activemq:activemq-all:6.1.7"))

    customLib("org.slf4j:slf4j-api:2.0.17")

    testImplementation("org.junit.jupiter:junit-jupiter:6.0.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.jar {
    archiveBaseName.set("testyMcTestface")
}

firstSpiritModule {
    moduleDirName = "src/main/fsm"
    displayName = "test-project displayName"
    isolationDetectorUrl = "https://fsdev.e-spirit.de/FsmDependencyDetector/"
    complianceLevel = HIGHEST
    firstSpiritVersion = "5.2.230909"
    minimalFirstSpiritVersion = "5.2.230909"
    vendor = "Crownpeak Technology GmbH"
    fsmDependencies = listOf("otherModuleName", "yetAnotherModule")
    libraries {
        create("libWithAllServerLibs") {
            description = "A library component containing all dependencies with server scope"
            configuration = configurations.fsServerCompile.get()
        }
        create("libWithCustomConfiguration") {
            displayName = "Library with custom configuration"
            description = "A library component defined by a custom Gradle configuration"
            hidden = false
            configuration = customLib
        }
    }
}

tasks.test {
    useJUnitPlatform()
}
