import org.gradle.plugins.fsm.configurations.fsDependency

plugins {
    java
    id("de.espirit.firstspirit-module")
}

// Custom configuration backing the library component declared further below.
val exampleLib: Configuration by configurations.creating

val fsRuntimeVersion = property("fsRuntimeVersion") as String

dependencies {
    // FirstSpirit API at compile time; provided by the server at runtime.
    compileOnly("de.espirit.firstspirit:fs-isolated-runtime:$fsRuntimeVersion")

    // Service interface needs to be loaded by the FirstSpirit server classloader (server scope).
    fsServerCompile(project(":server"))

    // All other components live on module scope, including :web, for ExampleExecutable
    fsModuleCompile(project(":module"))
    fsModuleCompile(project(":web"))

    // Additional 3rd-party dependencies. slf4j on server scope is intentional: it exercises the
    // FSMGP's server-scope handling, not a recommended pattern for real modules.
    fsServerCompile("org.slf4j:slf4j-api:2.0.18")
    fsModuleCompile(
        fsDependency(
            mapOf(
                "dependency" to "tools.jackson.core:jackson-databind:3.1.3",
                "minVersion" to "3.0.0",
                "maxVersion" to "3.1.3"
            )
        )
    )

    exampleLib("joda-time:joda-time:2.14.2")
}

// Toggle that swaps the plugin's built-in descriptor template for the fixture's own template
// under src/main/fsm-templates/. Invoked from CLI as `-PuseModuleDescriptorTemplate=true`.
val useModuleDescriptorTemplate =
    (project.findProperty("useModuleDescriptorTemplate") as String?)?.toBoolean() ?: false

firstSpiritModule {
    displayName = "FSMGP test fixture"
    vendor = "Crownpeak Technology GmbH"

    moduleDirName = if (useModuleDescriptorTemplate) "src/main/fsm-templates" else null

    webAppComponent("ExampleWebApp", project(":web"))

    libraries {
        create("exampleLib") {
            displayName = "Example library component"
            description = "Library component backed by a custom Gradle configuration"
            hidden = false
            configuration = exampleLib
        }
    }
}

// Suffix the FSM archive when the custom template is used so the two builds can coexist
// side-by-side (e.g. fsm-0.0.1-SNAPSHOT.fsm and fsm-template-0.0.1-SNAPSHOT.fsm).
if (useModuleDescriptorTemplate) {
    tasks.assembleFSM {
        archiveAppendix.set("template")
    }
}

tasks.check {
    dependsOn(tasks.checkCompliance)
}
