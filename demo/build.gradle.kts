plugins {
    id("spring-boot-convention")
}

dependencies {
    implementation(libs.spring.boot.starter)
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.actuator)
}
