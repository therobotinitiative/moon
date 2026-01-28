import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.file.DuplicatesStrategy

plugins {
    kotlin("jvm") version "1.9.10"
    `java-gradle-plugin`
    `maven-publish`
}

group = "com.orbital3d.gradle"
version = "0.1.0-local"

repositories {
    mavenCentral()
    gradlePluginPortal()
}

// Do not require a specific toolchain; instead emit Java 11-compatible bytecode
// so the build runs with the system JDK while producing compatible artifacts.

tasks.withType(JavaCompile::class.java).configureEach {
    options.release.set(11)
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(gradleKotlinDsl())
    compileOnly(gradleApi())
}

// Avoid failing the jar task if duplicate resource entries are discovered (e.g. during local testing).
tasks.withType(org.gradle.api.tasks.Copy::class.java).configureEach {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

gradlePlugin {
    plugins {
        create("moonBase") {
            id = "com.orbital3d.gradle.moon.base"
            implementationClass = "com.orbital3d.gradle.convention.MoonPlugin"
        }
        create("moonDocker") {
            id = "com.orbital3d.gradle.moon.docker"
            implementationClass = "com.orbital3d.gradle.convention.MoonDockerPlugin"
        }
    }
}

publishing {
    publications {
        create<org.gradle.api.publish.maven.MavenPublication>("maven") {
            from(components["java"])
        }
    }
    repositories {
        mavenLocal()
    }
}
tasks.withType(KotlinCompile::class.java).configureEach {
    kotlinOptions.jvmTarget = "11"
}
