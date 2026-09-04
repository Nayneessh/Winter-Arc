// Intentionally empty. Plugin versions are declared per module, not here.
//
// Gradle warns that the Kotlin plugin is loaded twice because :core and :app each declare it with
// an explicit version. That warning is understood and accepted, because the alternative does not
// work: hoisting the Kotlin plugins into a root `plugins { ... apply false }` block loads
// kotlin-android from the root classloader, where AGP is absent, and applying it then fails with
//
//     Could not generate a decorated class for type KotlinAndroidTarget
//       > com/android/build/gradle/api/BaseVariant
//
// Fixing that means adding AGP to the root block, which forces every build to resolve it from
// dl.google.com. That host is blocked by network policy in the environment this is developed in,
// and doing so would break the core-only build path -- the one that lets the whole business layer
// be compiled and tested where no Android SDK can be reached (see settings.gradle.kts).
//
// A warning on the CI build is a smaller price than losing local verification entirely.
