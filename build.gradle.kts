// Plugins are declared per-module rather than aliased here.
//
// A root `plugins { ... apply false }` block forces Gradle to RESOLVE every plugin listed,
// including the Android Gradle Plugin, even when it is never applied. That would make the
// whole build depend on reaching Google's Maven repository, which defeats the purpose of
// keeping :core:domain buildable from Maven Central alone. Declaring each plugin in the
// module that uses it means AGP is resolved only when :app is part of the build.

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
