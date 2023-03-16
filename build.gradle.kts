import com.github.jk1.license.filter.LicenseBundleNormalizer
import net.researchgate.release.ReleaseExtension
import org.apache.tools.ant.taskdefs.condition.Os
import java.net.URI
import java.nio.file.Files
import java.util.*

plugins {
    kotlin("jvm") version "1.8.10"
    id("maven-publish")
    id("idea")
    id("java-gradle-plugin")
    id("com.dorongold.task-tree") version "1.5"
    id("net.researchgate.release") version "3.0.2"
    id("org.ajoberstar.grgit") version "5.0.0"
    id("com.github.jk1.dependency-license-report") version "2.1"
    id("org.cyclonedx.bom") version "1.7.2"
}

tasks.withType<JavaCompile> {
    options.release.set(11)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = "11"
    }
}

val fsmAnnotationsVersion = "2.2.0"

val branchName: String = grgit.branch.current().name
Regex("(?:.*/)?[^A-Z]*([A-Z]+-[0-9]+).*").matchEntire(branchName)?.let {
    project.version = "${it.groupValues[1]}-SNAPSHOT"
}

description = "Gradle plugin to build FirstSpirit modules (FSMs)."
group = "de.espirit.gradle"

repositories {
    maven(url = "https://artifactory.e-spirit.de/artifactory/repo") {
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

val fsRuntimeVersion = "5.2.220309" // FirstSpirit 2022-03

dependencies {
    implementation(gradleApi())
    implementation("io.github.classgraph:classgraph:4.8.154")
    implementation("com.github.jk1:gradle-license-report:2.1")
    implementation("org.redundent:kotlin-xml-builder:1.9.0")
    implementation("org.json:json:20220924")
    implementation("org.apache.maven:maven-artifact:3.8.6")
    implementation("org.apache.httpcomponents.client5:httpclient5:5.2.1")
    implementation("com.espirit.moddev.components:annotations:${fsmAnnotationsVersion}")
    implementation("de.espirit.firstspirit:fs-isolated-runtime:${fsRuntimeVersion}")
    testImplementation("de.espirit.firstspirit:fs-isolated-runtime:${fsRuntimeVersion}")
    testImplementation(platform("org.junit:junit-bom:5.9.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.24.2")
    testImplementation("org.mockito:mockito-junit-jupiter:5.1.1")
    testImplementation("org.ow2.asm:asm:9.4")
    testImplementation(gradleTestKit())
}

licenseReport {
    allowedLicensesFile = projectDir.resolve("config/allowed-licenses.json")
    filters = arrayOf(LicenseBundleNormalizer())
}

tasks.checkLicense {
    dependsOn(tasks.cyclonedxBom)
}


tasks.cyclonedxBom {
    setIncludeConfigs(listOf("runtimeClasspath"))
    setOutputFormat("json")
}

val bomFile = layout.buildDirectory.file("reports/bom.json")
val bomArtifact = artifacts.add("archives", bomFile.get().asFile) {
    type = "bom"
    extension = "bom"
    builtBy(tasks.cyclonedxBom)
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

/**
 * Bundles together test classes for component scan tests
 */
val testJar = tasks.create<Jar>("testJar") {
    dependsOn(tasks.testClasses)
    archiveClassifier.set("tests")
    from("build/classes/groovy/test")
    from("build/classes/java/test")
    from("build/classes/kotlin/test")

    // These files should only be visible for certain test cases and will be added when needed
    exclude("org/gradle/plugins/fsm/TestModuleImpl.class")
    exclude("org/gradle/plugins/fsm/TestWebAppWithProjectProperties.class")
    exclude("org/gradle/plugins/fsm/TestProjectAppComponentWithProperties.class")
}

tasks.test {
    dependsOn(writePropertiesToResourceFile)
    dependsOn(testJar)
    filter {
        excludeTestsMatching("*IT")
    }
    systemProperty("artifactory_username", findProperty("artifactory_username") as String)
    systemProperty("artifactory_password", findProperty("artifactory_password") as String)
    systemProperty("gradle.version", gradle.gradleVersion)
    systemProperty("version", version)
    systemProperty("testJar", testJar.archiveFile.get().asFile.absolutePath)
    systemProperty("classesDir", project.buildDir.resolve("classes").absolutePath)

    if (Os.isFamily(Os.FAMILY_WINDOWS)) {
        // Gradle may keep locks on Windows systems, causing the @TempDir cleanup to fail
        systemProperty("junit.jupiter.tempdir.cleanup.mode.default", "NEVER")
    }

    maxHeapSize = "2048m"
    useJUnitPlatform()
}

val integrationTest by tasks.creating(Test::class.java) {
    filter {
        includeTestsMatching("*IT")
    }
    systemProperty("artifactory_username", findProperty("artifactory_username") as String)
    systemProperty("artifactory_password", findProperty("artifactory_password") as String)
    useJUnitPlatform()
}

tasks.check {
    dependsOn(tasks.checkLicense)
}

tasks.publish {
    dependsOn(tasks.checkLicense)
}

publishing {
    publications {
        create<MavenPublication>("pluginMaven") {
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
                developer.appendNode("id", "crownpeak")
                developer.appendNode("name", "Crownpeak Technology GmbH")
                developer.appendNode("url", "https://www.crownpeak.com")
            }
            artifact(bomArtifact)
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

configure<ReleaseExtension> {
    with(git) {
        requireBranch.set("master")
    }
}

idea {
    module {
        isDownloadJavadoc = true
        isDownloadSources = true
    }
}