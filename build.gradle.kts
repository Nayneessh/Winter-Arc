// Intentionally empty.
//
// Plugin versions are declared per module rather than in a root `plugins { ... } apply false`
// block. A root block makes Gradle resolve the Android Gradle Plugin on every build -- even one
// that never applies it -- which would break the core-only build path described in
// settings.gradle.kts.
