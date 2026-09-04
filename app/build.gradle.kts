import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Versions are declared here rather than in a root `plugins { ... apply false }` block.
// A root block forces Gradle to RESOLVE the Android Gradle Plugin even for builds that never
// apply it, which would break the domain-only build path (see settings.gradle.kts).
plugins {
    id("com.android.application") version "8.7.3"
    id("org.jetbrains.kotlin.android") version "2.0.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21"
    id("com.google.devtools.ksp") version "2.0.21-1.0.28"
}

/**
 * Reads a secret from (in order) an environment variable, then a git-ignored
 * secrets.properties, then falls back to empty. Empty is a supported state: the app runs
 * fully offline-only, so a build never fails for want of a cloud credential.
 */
fun secret(key: String): String {
    // 1. Environment / CI secret wins.
    System.getenv(key)?.takeIf { it.isNotBlank() }?.let { return it }
    // 2. Git-ignored local overrides.
    // 3. Committed public client config (publishable key only — see supabase.properties).
    listOf("secrets.properties", "supabase.properties").forEach { name ->
        val f = rootProject.file(name)
        if (f.exists()) {
            val props = java.util.Properties().apply { f.inputStream().use { load(it) } }
            props.getProperty(key)?.takeIf { it.isNotBlank() }?.let { return it }
        }
    }
    return ""
}

fun supabaseUrl(): String = secret("SUPABASE_URL")
fun supabaseAnonKey(): String = secret("SUPABASE_ANON_KEY")

android {
    namespace = "com.winterarc.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.winterarc.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        // Supabase is OPTIONAL. The app is fully functional with these blank: cloud backup
        // simply stays switched off. They are injected at build time from CI secrets or a
        // local secrets.properties, and never committed.
        buildConfigField("String", "SUPABASE_URL", "\"${supabaseUrl()}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${supabaseAnonKey()}\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            // Populated only when CI provides a keystore. Falls back to debug signing so a
            // local `assembleRelease` still produces an installable APK.
            val storePath = System.getenv("WINTER_ARC_KEYSTORE")
            if (!storePath.isNullOrBlank() && file(storePath).exists()) {
                storeFile = file(storePath)
                storePassword = System.getenv("WINTER_ARC_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("WINTER_ARC_KEY_ALIAS")
                keyPassword = System.getenv("WINTER_ARC_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            val ks = System.getenv("WINTER_ARC_KEYSTORE")
            signingConfig = if (!ks.isNullOrBlank() && file(ks).exists()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/versions/9/previous-compilation-data.bin")
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

dependencies {
    implementation(project(":core:domain"))

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.navigation:navigation-compose:2.8.5")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("com.google.truth:truth:1.4.4")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")

    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.room:room-testing:2.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
