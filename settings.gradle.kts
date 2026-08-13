plugins {
    // Lets Gradle download a matching JDK when the Java 21 toolchain requested in build.gradle.kts
    // is not installed locally. Locally installed JDKs are still preferred; this only kicks in when
    // auto-detection finds nothing, which keeps new machines and CI agents working out of the box.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "physics-simulation"
