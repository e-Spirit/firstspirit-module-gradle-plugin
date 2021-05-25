import java.net.URI
import java.nio.file.Files
import java.util.Date
import java.util.Properties
import java.util.regex.Pattern

plugins {
    id("groovy")
    id("maven-publish")
    id("idea")
    id("java-gradle-plugin")
    id("com.dorongold.task-tree") version "1.5"
    id("net.researchgate.release") version "2.8.1"
    id("org.ajoberstar.grgit") version "4.1.0"
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
    withSourcesJar()
    withJavadocJar()
}

val fsmAnnotationsVersion = "1.9.7"

try {
    val branchName = grgit.branch.current().name
    val matcher = Pattern.compile("(?:.*/)?[^A-Z]*([A-Z]+-[0-9]+).*").matcher(branchName)
    if (matcher.matches()) {
        project.version = "${matcher.group(1)}-SNAPSHOT"
    }
} catch (e: java.io.IOException) {
    println("Failure determining branch name: $e")
}

description = "Gradle plugin to build FirstSpirit modules (FSMs)."
group = "de.espirit.gradle"

repositories {
    maven {
        setUrl("https://artifactory.e-spirit.de/artifactory/repo")
        credentials {
            username = property("artifactory_username") as String
            password = property("artifactory_password") as String
        }
    }
}

gradlePlugin {
    plugins {
        create("firstSpiritModule") {
            id = "de.espirit.firstspirit-module"
            implementationClass = "org.gradle.plugins.fsm.FSMPlugin"
        }
        create("firstSpiritModuleAnnotations") {
            id = "de.espirit.firstspirit-module-annotations"
            implementationClass = "org.gradle.plugins.fsm.annotations.FSMAnnotationsPlugin"
        }
        create("firstSpiritModuleConfigurations") {
            id = "de.espirit.firstspirit-module-configurations"
            implementationClass = "org.gradle.plugins.fsm.configurations.FSMConfigurationsPlugin"
        }
    }
}

val fsRuntimeVersion = "5.2.210210" // FirstSpirit 2021-02

dependencies {
    implementation(gradleApi())
    implementation(localGroovy())
    implementation("io.github.classgraph:classgraph:4.8.102")
    implementation("com.github.jk1:gradle-license-report:1.16")
    implementation("com.espirit.moddev.components:annotations:${fsmAnnotationsVersion}")
    implementation("de.espirit.mavenplugins:fsmchecker:0.12")
    implementation("de.espirit.firstspirit:fs-isolated-runtime:${fsRuntimeVersion}")
    testImplementation("de.espirit.firstspirit:fs-isolated-runtime:${fsRuntimeVersion}")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.7.1")
    testImplementation("org.assertj:assertj-core:3.19.0")
    testImplementation("commons-io:commons-io:2.8.0")
    testImplementation("org.mockito:mockito-junit-jupiter:3.7.7")
    testImplementation("org.ow2.asm:asm:9.1")
    testImplementation(gradleTestKit())
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.7.1")

    testImplementation("org.spockframework:spock-core:2.0-M5-groovy-3.0") {
        exclude(group = "org.codehaus.groovy")
    }

}

sourceSets {
    test {
        resources.srcDirs += file("src/test/resources")
        resources.srcDirs += file("src/main/resources")
    }
}

val writePropertiesToResourceFile = tasks.create("writePropertiesToResourceFile") {
    outputs.upToDateWhen { false }
    doLast {
        val props = Properties()
        props.setProperty("fsm-annotations-version", fsmAnnotationsVersion)
        props.setProperty("version", version as String)
        val propsFile = sourceSets.main.get().output.resourcesDir!!.toPath().resolve("versions.properties").toFile()
        val propsFileTest = sourceSets.test.get().output.resourcesDir!!.toPath().resolve("versions.properties").toFile()
        listOf(propsFile, propsFileTest).forEach { targetFile ->
            targetFile.parentFile.mkdirs()
            if(!targetFile.exists()) {
                Files.createFile(targetFile.toPath())
            }
            targetFile.writer().use { writer ->
                props.store(writer, null)
            }
        }
    }
}

tasks.jar {
    dependsOn(writePropertiesToResourceFile)

    manifest {
        attributes["Implementation-Title"]  = "Gradle FSM plugin"
        attributes["Implementation-Version"]  = archiveVersion
        attributes["Built-By"] = System.getProperty("user.name")
        attributes["Built-Date"]  = Date()
        attributes["Built-JDK"]  = System.getProperty("java.version")
        attributes["Built-Gradle"]  = gradle.gradleVersion
    }
}

tasks.test {
    dependsOn(writePropertiesToResourceFile)
    systemProperty("artifactory_username", findProperty("artifactory_username") as String)
    systemProperty("artifactory_password", findProperty("artifactory_password") as String)
    systemProperty("gradle.version", gradle.gradleVersion)
    systemProperty("version", version)
    maxHeapSize = "2048m"
    useJUnitPlatform()
}

val integrationTest = tasks.create ("integrationTest") {
    description = "Runs the integration tests."
    group = "verification"

    dependsOn("jar")

    if (System.getProperty("isCI") != null) {
        mustRunAfter(tasks.test)
    }
}

if (System.getProperty("isCI") != null) {
    tasks.check {
        dependsOn(integrationTest)
    }
}

publishing {
    publications {
        create("pluginMaven", MavenPublication::class.java) {
            pom.withXml {
                val root = asNode()
                root.appendNode("name", "FSM Gradle plugin")
                root.appendNode("description", "Gradle plugin to build FirstSpirit modules (FSMs).")
                root.appendNode("inceptionYear", "2013")

                val license = root.appendNode("licenses").appendNode("license")
                license.appendNode("name", "The Apache Software License, Version 2.0")
                license.appendNode("url", "https://www.apache.org/licenses/LICENSE-2.0.txt")
                license.appendNode("distribution", "repo")

                val developers = root.appendNode("developers")
                val originalDeveloper = developers.appendNode("developer")
                originalDeveloper.appendNode("id", "moritzzimmer")
                originalDeveloper.appendNode("name", "Moritz Zimmer")
                originalDeveloper.appendNode("email", "moritz.zmmr@gmail.com")

                val developer = developers.appendNode("developer")
                developer.appendNode("id", "e-spirit")
                developer.appendNode("name", "E-Spirit AG")
                developer.appendNode("email", "produktmanagement@e-spirit.com")
            }
        }
    }
    repositories {
        maven {
            credentials {
                username = property("artifactory_username") as String?
                password = property("artifactory_password") as String?
            }
            url = if (version.toString().endsWith("SNAPSHOT")) {
                URI.create("https://artifactory.e-spirit.de/artifactory/core-platform-mvn-snapshot")
            } else {
                URI.create("https://artifactory.e-spirit.de/artifactory/core-platform-mvn-release")
            }
        }
    }
}

rootProject.tasks.afterReleaseBuild {
    dependsOn(tasks.publish)
}

idea {
    module {
        isDownloadJavadoc = true
        isDownloadSources = true
    }
}