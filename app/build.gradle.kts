import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Versions are declared here rather than in a root `plugins { ... } apply false` block, so that a
// build which never applies the Android plugin does not have to resolve it. See settings.gradle.kts.
plugins {
    id("com.android.application") version "8.7.3"
    id("org.jetbrains.kotlin.android") version "2.0.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
}

android {
    namespace = "com.winterarc.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.winterarc.app"
        minSdk = 26          // java.time is available natively from 26, so no desugaring is needed
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"
    }

    // One signing key, committed, used by every build type.
    //
    // Android refuses to install an update signed by a different key than the version already on
    // the device. The default debug key is generated per machine, so a CI runner mints a fresh one
    // on every clean build -- meaning each new APK could only be installed by uninstalling the
    // previous one first, taking the whole training history with it. A fixed key makes an update
    // simply install over the top.
    //
    // This app is not distributed through any store, so the key is a build input rather than a
    // secret. It is not a Play upload key and grants nothing beyond signing a build of this app.
    signingConfigs {
        create("winterarc") {
            storeFile = rootProject.file("winter-arc.jks")
            storePassword = "winterarc"
            keyAlias = "winterarc"
            keyPassword = "winterarc"
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            signingConfig = signingConfigs.getByName("winterarc")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("winterarc")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":core"))

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
