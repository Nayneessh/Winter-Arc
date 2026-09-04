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

// The domain module is pure Kotlin/JVM and resolves entirely from Maven Central,
// so it builds and tests in environments with no Android SDK. Setting
// WINTER_ARC_DOMAIN_ONLY=1 drops the Android module from the build so that the
// business-logic test suite can run in such an environment (see docs/BUILDING.md).
include(":core:domain")

if (System.getenv("WINTER_ARC_DOMAIN_ONLY") != "1") {
    include(":app")
}
