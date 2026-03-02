# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

A Gradle plugin system for building FirstSpirit modules (FSM archives). Ships three plugins in one artifact:
- **`de.espirit.firstspirit-module`** — Main plugin: applies the other two, adds `assembleFSM`, `checkIsolation`, and `checkCompliance` tasks
- **`de.espirit.firstspirit-module-annotations`** — Adds FSM component annotations as `compileOnly` dependency
- **`de.espirit.firstspirit-module-configurations`** — Adds custom Gradle dependency configurations (`fsServerCompile`, `fsModuleCompile`, `fsWebCompile`)

Written in **Kotlin** (with two Java files for compliance checking), targets **Java 11+** and **Gradle 8.11+**. Uses Gradle 9.3.0 wrapper. Build file is `build.gradle.kts`.

## Build Commands

```bash
./gradlew build                    # Compile + run unit tests + license check
./gradlew test                     # Unit tests only (excludes *IT tests)
./gradlew integrationTest          # Integration tests only (*IT tests)
./gradlew test --tests "org.gradle.plugins.fsm.FSMPluginTest"           # Single test class
./gradlew test --tests "org.gradle.plugins.fsm.FSMPluginTest.testFoo"   # Single test method
./gradlew assemble                 # Compile without tests
./gradlew clean build              # Clean build
```

Requires `artifactory_username` and `artifactory_password` in `~/.gradle/gradle.properties` (used for dependency resolution and passed as system properties to tests).

Tests use **JUnit 5 (Jupiter) + AssertJ + Mockito + Gradle TestKit**. Max heap 2048MB.

## Architecture

### Plugin Entry Points (registered in `build.gradle.kts` → `gradlePlugin` block)

| Plugin ID | Implementation Class |
|---|---|
| `de.espirit.firstspirit-module` | `org.gradle.plugins.fsm.FSMPlugin` |
| `de.espirit.firstspirit-module-annotations` | `org.gradle.plugins.fsm.annotations.FSMAnnotationsPlugin` |
| `de.espirit.firstspirit-module-configurations` | `org.gradle.plugins.fsm.configurations.FSMConfigurationsPlugin` |

### Source Layout (`src/main/kotlin/org/gradle/plugins/fsm/`)

- **`FSMPlugin.kt`** — Main plugin: applies Java plugin + sub-plugins, creates `firstSpiritModule` extension, registers `assembleFSM` (FSM task), `checkIsolation` (IsolationCheck), `checkCompliance`, and `validateDescriptor` tasks. Replaces JAR artifact with FSM in publications.
- **`FSMPluginExtension.kt`** — Extension properties (`moduleName`, `displayName`, `isolationDetectorUrl`, `webApps`, `fsmDependencies`, `libraries`, etc.) and `webAppComponent()` registration method.
- **`tasks/bundling/FSM.kt`** — Core bundling task. Creates the `.fsm` archive: collects JARs into `lib/`, generates `module-isolated.xml` in `META-INF/`, merges `fsm-resources/` directories, includes license reports.
- **`tasks/verification/IsolationCheck.kt`** — Connects to FSM Dependency Detector web service to verify isolation compliance (MINIMAL/DEFAULT/HIGHEST levels).
- **`tasks/verification/ValidateDescriptor.kt`** — Validates the generated module descriptor XML.
- **`descriptor/`** — Module descriptor generation package:
  - `ModuleDescriptor.kt` — Orchestrates XML generation from scanned components
  - `ComponentScan.kt` / `ClassScanExtensions.kt` — ClassGraph-based bytecode scanning for component annotations
  - `Components.kt` / `ComponentsWithResources.kt` — Component XML tag generation for all FirstSpirit types (`@ModuleComponent`, `@ServiceComponent`, `@ProjectAppComponent`, `@WebAppComponent`, `@PublicComponent`, `@ScheduleTaskComponent`, `@UrlFactoryComponent`, `@GadgetComponent`)
  - `Resources.kt` / `Resource.kt` / `FsmResources.kt` — Resource declaration and `fsm-resources/` merging
  - `WebAppComponents.kt` / `ProjectAppComponents.kt` / `LibraryComponents.kt` — Type-specific component handling
- **`DeclaredWebAppChecker.kt`** — Validates 1:1 mapping between `@WebAppComponent` annotations and `webAppComponent()` extension declarations.
- **`configurations/FSMConfigurationsPlugin.kt`** — Defines custom dependency scopes and the `fsDependency()` helper (supports `minVersion`, `maxVersion`).
- **`annotations/FSMAnnotationsPlugin.kt`** — Adds `com.espirit.moddev.components:annotations` as compileOnly, version from bundled `versions.properties`.

### Module Descriptor Generation Flow

1. `FSM.kt` task action is triggered
2. All project JARs and dependencies are collected into `lib/`
3. `fsm-resources/` from all subprojects are merged into archive root
4. ClassGraph scans JARs for component annotations via bytecode scanning (`ComponentScan.kt`)
5. `ModuleDescriptor.kt` generates component XML tags from discovered annotations
6. Template file (`template-module-isolated.xml`) or user-provided `module-isolated.xml` is filtered with placeholder substitution (`$name`, `$version`, `$components`, `$resources`, `$class`, etc.)
7. Final `module-isolated.xml` placed in `META-INF/`

### Test Structure

- **Unit tests**: `src/test/kotlin/` — JUnit 5 tests, filtered to exclude `*IT` patterns
- **Integration tests**: Run via `./gradlew integrationTest`, match `*IT` pattern, use Gradle TestKit
- **Test fixture projects** in `test-projects/` (both Groovy and Kotlin DSL variants):
  - `fsmplugin-groovy/` / `fsmplugin-kotlin/` — Standard multi-module (api, impl, web, fsm)
  - `annotationsplugin-groovy/` / `annotationsplugin-kotlin/`
  - `configurationsplugin-groovy/` / `configurationsplugin-kotlin/`
  - `multiple-webapps-groovy/` / `multiple-webapps-kotlin/`
- A `testJar` task bundles test classes into a JAR for component scan tests; its path is passed as `testJar` system property

### Branch Versioning

The build script auto-detects JIRA ticket IDs from branch names (e.g., `feature/DEVEX-123-description` → version `DEVEX-123-SNAPSHOT`). On `master`, version comes from `gradle.properties`.

## Publishing

Published to Artifactory (`https://artifactory.e-spirit.de/artifactory/`). Versioning managed by `net.researchgate.release` plugin — release commits are automated (pre-tag + new version commits visible in git log). Release builds require `master` branch.
