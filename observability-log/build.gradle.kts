plugins {
    id("spring-jar-convention")
}

dependencies {
    implementation(libs.spring.boot.starter.web)
    implementation(libs.sentry.core)
}
