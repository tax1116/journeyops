plugins {
    id("spring-boot-convention")
}

dependencies {
    implementation(project(":observability-log"))
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.restclient)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.micrometer.tracing.bridge.otel)
    implementation(libs.sentry.spring.boot.starter)
    testImplementation(libs.mockwebserver)
}
