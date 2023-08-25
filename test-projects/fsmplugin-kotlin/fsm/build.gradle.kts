import de.espirit.mavenplugins.fsmchecker.ComplianceLevel.HIGHEST
import org.gradle.plugins.fsm.configurations.fsDependency
import java.util.zip.ZipFile

plugins {
    java
    id("de.espirit.firstspirit-module")
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

val customLib: Configuration by configurations.creating

dependencies {
    compileOnly("de.espirit.firstspirit:fs-isolated-runtime:5.2.230909")

    fsServerCompile(project(":api"))
    fsModuleCompile(project(":impl"))
    fsWebCompile(project(":web"))
    fsModuleCompile(project(":web"))
    fsModuleCompile(fsDependency(mapOf("dependency" to "joda-time:joda-time:2.10")))
    fsModuleCompile(fsDependency("commons-logging:commons-logging:1.2", "1.0", "1.5"))
    fsModuleCompile(fsDependency("org.apache.activemq:activemq-all:5.14.2"))

    fsWebCompile(fsDependency("org.apache.activemq:activemq-all:5.14.2"))

    customLib("org.slf4j:slf4j-api:2.0.6")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
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

val fsmArchivePath = layout.buildDirectory.file("fsm/fsm-${project.version}.fsm").get().asFile

val testFsmIsProduced by tasks.creating {
    dependsOn(tasks.assembleFSM)

    doLast {
        logger.info("Searching for fsm file in $fsmArchivePath")
        assert(fsmArchivePath.exists())
    }
}

val testZipFileContainsModuleXml by tasks.creating {
    dependsOn(tasks.assembleFSM)

    doLast {
        val fsmFile = ZipFile(fsmArchivePath)
        val moduleXml = fsmFile.entries().toList().first { "META-INF/module-isolated.xml" == it.getName() }
        assert(moduleXml != null)
    }
}

tasks.test {
    dependsOn(tasks.assembleFSM, testFsmIsProduced, testZipFileContainsModuleXml)
    useJUnitPlatform()
}
