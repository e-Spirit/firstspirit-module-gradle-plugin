plugins {
    id("java-library")
    id("de.espirit.firstspirit-module-annotations")
}

val externalProject = project(":external-subproject")
evaluationDependsOn(":external-subproject")

dependencies {
    compileOnly("de.espirit.firstspirit:fs-isolated-runtime:5.2.220309")
    implementation(externalProject.tasks.getByName("jar").outputs.files)
}