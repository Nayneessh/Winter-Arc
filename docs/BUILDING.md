# Building Winter Arc

## Requirements

| | |
|---|---|
| JDK | 17 or later |
| Android SDK | compileSdk 35, build-tools 35 |
| Gradle | Supplied by the wrapper (8.11.1) — do not install separately |
| minSdk | 26 (Android 8.0) |

## Build

```bash
git clone https://github.com/Nayneessh/Winter-Arc.git
cd Winter-Arc

./gradlew :app:assembleDebug     # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:assembleRelease   # app/build/outputs/apk/release/app-release.apk
```

Install:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Tests

```bash
# Business logic. No Android SDK required.
WINTER_ARC_DOMAIN_ONLY=1 ./gradlew :core:domain:test

# Everything
./gradlew :core:domain:test :app:testDebugUnitTest :app:lintDebug
```

### The domain-only build path

`WINTER_ARC_DOMAIN_ONLY=1` makes `settings.gradle.kts` skip `include(":app")`.

`:core:domain` is pure Kotlin/JVM and resolves entirely from Maven Central, so the whole
business-logic suite — the workout engine, volume, estimated 1RM, PR detection, consistency,
body analytics, unit conversion and seed integrity — runs on any machine with a JDK. No
Android SDK, no emulator, no device.

This is why plugin versions are declared **per module** rather than in a root
`plugins { ... apply false }` block: a root block forces Gradle to resolve the Android Gradle
Plugin on every build, including one that never applies it, which would defeat the whole
arrangement.

It exists for a concrete reason. The environment this project was built in blocks
`dl.google.com` at the network policy level. That host serves both the Android SDK and the
Android Gradle Plugin, and neither is mirrored on Maven Central, so **no Android build was
possible there at all**. Rather than ship logic that had never been executed, the business
layer was isolated where it could be compiled and tested on every change, and APK assembly was
moved to CI.

## Configuration

Supabase is optional. With no credentials the app is fully functional and cloud backup is off.

Resolution order for `SUPABASE_URL` and `SUPABASE_ANON_KEY`:

1. Environment variable (CI secrets)
2. `secrets.properties` — git-ignored, for local overrides
3. `supabase.properties` — committed, holds the publishable key

To override locally:

```properties
# secrets.properties  (git-ignored)
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_ANON_KEY=sb_publishable_...
```

**On committing the publishable key.** It is designed to ship inside client applications and
is present in every browser and mobile build of every Supabase app. It grants no authority by
itself; row-level security decides every read and write. The **service-role key** bypasses RLS
— it is not in this repository, not in the APK, and must never be committed.

## Release signing

Unsigned by default; `assembleRelease` falls back to the debug key so a local release build
still produces an installable APK.

To sign properly, set:

```bash
export WINTER_ARC_KEYSTORE=/path/to/release.jks
export WINTER_ARC_KEYSTORE_PASSWORD=...
export WINTER_ARC_KEY_ALIAS=...
export WINTER_ARC_KEY_PASSWORD=...
./gradlew :app:assembleRelease
```

Keystores and passwords are git-ignored (`*.jks`, `*.keystore`) and must stay that way.

The release build enables R8 minification and resource shrinking. `proguard-rules.pro` keeps
Room's generated code, kotlinx.serialization's generated serializers and the domain model
classes, all of which are referenced reflectively.

## CI

`.github/workflows/android.yml` runs on every push and on manual dispatch.

1. JDK 17, Gradle cache, Android SDK licences
2. `:core:domain:test` — logic regressions fail before any time is spent assembling an APK
3. `:app:testDebugUnitTest`
4. `:app:lintDebug` (non-blocking)
5. `assembleDebug` and `assembleRelease`
6. **APK structure verification** — each archive is checked for `AndroidManifest.xml` and
   `classes.dex`, so a green build is not mistaken for an installable artifact
7. Uploads: `winter-arc-debug-apk`, `winter-arc-release-apk` (90 days), `test-results` (14 days)

### Getting the APK

Open the run from the [Actions tab](https://github.com/Nayneessh/Winter-Arc/actions), scroll to
**Artifacts**, download `winter-arc-debug-apk`, unzip, and install the `.apk` inside.

The debug build is the one to install first: it has a distinct application id
(`com.winterarc.app.debug`) so it can sit alongside a release build, and it is not minified,
so a stack trace means something.

## Database

Migrations live in `supabase/migrations/` and apply in filename order.

```bash
supabase link --project-ref <ref>
supabase db push
```

Room's exported schema JSON is written to `app/schemas/` and should be committed whenever the
database version changes — it is what future migrations get diffed against.
