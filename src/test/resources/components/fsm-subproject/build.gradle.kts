plugins {
    id("java-library")
    id("de.espirit.firstspirit-module-annotations")
}

val externalProject = project(":external-subproject")
evaluationDependsOn(":external-subproject")

dependencies {
    compileOnly(group = "de.espirit.firstspirit", name = "fs-isolated-runtime", version = "5.2.220309")
    implementation(externalProject.tasks.getByName("jar").outputs.files)
}