plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.5.0"
}

rootProject.name = "journeyops"

include(
    "observability-log",
    "user-api",
    "loan-application-api",
    "loan-evaluation-api",
    "loan-contract-api",
)
