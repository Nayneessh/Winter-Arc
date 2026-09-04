import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm") version "2.0.21"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21"
}

// A jvmToolchain(17) would demand a JDK 17 be installed even when a newer JDK is present and
// perfectly capable of emitting 17 bytecode. Targeting the release directly lets this module
// build on whatever modern JDK the machine happens to have, which is the whole point of keeping
// it free of the Android SDK.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    testImplementation("junit:junit:4.13.2")
}

tasks.test {
    useJUnit()
    testLogging { events("passed", "failed", "skipped") }
}
