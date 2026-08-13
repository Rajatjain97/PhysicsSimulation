import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    java
    id("org.springframework.boot") version "3.5.16"
}

group = "com.physicsfactory"
version = "0.1.0"
description = "Physics Factory - automated generation of physics-based short-form videos"

java {
    toolchain {
        // Gradle provisions/uses a JDK 21 toolchain regardless of the JDK that runs Gradle itself.
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // The Spring Boot BOM is imported directly from the Boot plugin's coordinates, so dependency
    // versions and the plugin version can never drift apart. This intentionally replaces the
    // io.spring.dependency-management plugin: one less plugin to keep compatible with Gradle.
    implementation(platform(SpringBootPlugin.BOM_COORDINATES))
    annotationProcessor(platform(SpringBootPlugin.BOM_COORDINATES))
    testImplementation(platform(SpringBootPlugin.BOM_COORDINATES))

    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

springBoot {
    // Generates META-INF/build-info.properties so the running application can report its own
    // name and version instead of hardcoding them.
    buildInfo()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-Xlint:all")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
