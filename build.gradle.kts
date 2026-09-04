// Plugin versions are declared once, here, and applied by the modules that need them.
//
// The Kotlin plugin must be resolved by a single classloader across the build; declaring it with
// an explicit version in more than one subproject loads it twice and Gradle warns that this may
// break the build.
//
// The Android Gradle Plugin is deliberately NOT listed here. It is served from dl.google.com,
// which some networks block outright, and naming it in the root block would force every build to
// resolve it -- including the core-only build that exists precisely so the business logic can be
// compiled and tested where the Android SDK cannot be reached. It stays declared in :app alone.
plugins {
    id("org.jetbrains.kotlin.jvm") version "2.0.21" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21" apply false
}
