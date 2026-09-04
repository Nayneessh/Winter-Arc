pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        google()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        google()
    }
}

rootProject.name = "winter-arc"

// :core is pure Kotlin/JVM. It holds the whole model, every calculation and the seeded
// programme, so all of it compiles and tests on a machine with only a JDK -- no Android SDK,
// no emulator, no device. WINTER_ARC_CORE_ONLY=1 drops the Android module so that path stays
// open in environments that cannot reach the Android SDK at all.
include(":core")

if (System.getenv("WINTER_ARC_CORE_ONLY") != "1") {
    include(":app")
}
