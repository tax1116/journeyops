plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.5.0"
}
// TODO: Change the root project name
rootProject.name = "spring-boot-multi-module-template"

// TODO: Change the name of the project
// TODO: Add the names of the projects to include
include("demo")
