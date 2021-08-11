import de.espirit.firstspirit.server.module.ModuleInfo.Mode.ISOLATED
import de.espirit.mavenplugins.fsmchecker.ComplianceLevel.MINIMAL
import org.gradle.plugins.fsm.configurations.fsDependency

import java.nio.file.Paths
import java.util.zip.ZipFile

plugins {
    java
    id("de.espirit.firstspirit-module")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
    compileOnly("de.espirit.firstspirit:fs-isolated-runtime:5.2.200910")

    fsServerCompile(project(":api"))
    fsModuleCompile(project(":impl"))
    fsWebCompile(project(":web"))
    fsModuleCompile(project(":web"))
    fsModuleCompile(fsDependency(mapOf("dependency" to "joda-time:joda-time:2.10", "skipInLegacy" to true)))
    fsModuleCompile(fsDependency("commons-logging:commons-logging:1.2", true, "1.0", "1.5"))
    fsModuleCompile(fsDependency("org.apache.activemq:activemq-all:5.14.2", true))

    fsWebCompile(fsDependency("org.apache.activemq:activemq-all:5.14.2", true))

    testImplementation("junit:junit:4.12")
}

tasks.jar {
    archiveBaseName.set("testyMcTestface")
}

firstSpiritModule {
    moduleDirName = "src/main/fsm"
    displayName = "test-project displayName"
    resourceMode = ISOLATED
    isolationDetectorUrl = "https://fsdev.e-spirit.de/FsmDependencyDetector/"
    complianceLevel = MINIMAL
    firstSpiritVersion = "5.2.190507"
    vendor = "e-Spirit AG"
    fsmDependencies = listOf("otherModuleName", "yetAnotherModule")
}

val fsmArchivePath = Paths.get(project.buildDir.path, "fsm", "fsm-${project.version}.fsm")

val testFsmIsProduced by tasks.creating {
    doLast {
        logger.info("Searching for fsm file in $fsmArchivePath")
        assert(fsmArchivePath.toFile().exists())
    }
}

val testZipFileContainsModuleXml by tasks.creating {
    doLast {
        val fsmFile = ZipFile(fsmArchivePath.toString())
        val moduleXml = fsmFile.entries().toList().first { "META-INF/module.xml" == it.getName() }
        assert(moduleXml != null)
    }
}

tasks.test {
    dependsOn(tasks.assembleFSM, testFsmIsProduced, testZipFileContainsModuleXml)
}
