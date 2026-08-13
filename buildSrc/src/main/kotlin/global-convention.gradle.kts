import kr.co.taek.dev.build.JDK_VERSION
import kr.co.taek.dev.build.libs

plugins {
    kotlin("jvm")
    id("org.jlleitschuh.gradle.ktlint")
}

group = "kr.co.taek.dev"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(JDK_VERSION))
    }
}

repositories {
    mavenCentral()
    maven {
        url = uri("https://packages.confluent.io/maven/")
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

dependencies {
    implementation(libs.kotlin.logging)
    testImplementation(libs.spring.boot.starter.test)
}

ktlint {
    filter {
        exclude {
            it.file.path.startsWith(project.layout.buildDirectory.get().dir("generated").toString())
        }
    }
}
