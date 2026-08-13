plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
    implementation(libs.spring.boot.gradle)
    implementation(libs.spring.dependency.management)
    implementation(libs.kotlin.gradle)
    implementation(libs.kotlin.allopen)
    implementation(libs.kotlin.noarg)
    implementation(libs.ktlint)
}
