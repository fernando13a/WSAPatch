import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Optional release signing: drop a `keystore.properties` at the module root with
// storeFile / storePassword / keyAlias / keyPassword to sign the release build with your own key.
// Without it (e.g. on CI), the release build falls back to the debug key so it still installs.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) FileInputStream(keystorePropsFile).use { load(it) }
}

/**
 * Short commit this APK was built from, or "unknown" outside a git checkout.
 *
 * Every build so far shipped as `app-debug.apk` with versionName "1.0.0", so a phone holding a
 * stale download looked exactly like an up-to-date one — a fix rode two rounds of "it still
 * fails" before it turned out the APK on the device predated it. Stamping the commit into the
 * version makes that checkable from the phone, and the failure loud instead of silent.
 *
 * Deliberately non-fatal: a source zip with no `.git`, or a runner without git, must still build.
 */
val gitSha: String = runCatching {
    val process = ProcessBuilder("git", "rev-parse", "--short=7", "HEAD")
        .directory(rootProject.projectDir)
        .redirectErrorStream(true)
        .start()
    val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
    output.takeIf { process.waitFor() == 0 && it.isNotEmpty() }
}.getOrNull() ?: "unknown"

android {
    namespace = "com.ironmind.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ironmind.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 100  // Semantic: major.minor.patch as 1.0.0 = 100
        // The commit rides in versionName so the build on a device can be identified from
        // Settings → Apps, from `adb shell dumpsys package`, and from the AI model screen.
        versionName = "1.0.0+$gitSha"
        buildConfigField("String", "GIT_SHA", "\"$gitSha\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        create("release") {
            if (keystorePropsFile.exists()) {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Use the real release key when configured; otherwise fall back to debug so CI can
            // still produce an installable APK.
            signingConfig = if (keystorePropsFile.exists()) {
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

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    sourceSets {
        // Exposes the exported Room schema history (see the `ksp { }` block below) to
        // instrumented tests as assets, so MigrationTestHelper can load a past version's schema
        // and replay real migrations against it instead of only ever testing against the latest
        // schema.
        getByName("androidTest") {
            assets.srcDirs("$projectDir/schemas")
            java.srcDirs("src/testShared/java")
        }
        // Fakes/rules under src/testShared (FakeWorkoutRepository, FakeLlmInferenceService, ...)
        // are plain Kotlin with no Android framework dependency, so both the JVM unit tests
        // (test/) and the on-device instrumented tests (androidTest/, e.g. Compose UI tests that
        // construct a real ViewModel with fakes instead of needing Hilt test infrastructure)
        // share the exact same doubles instead of each maintaining their own copy.
        getByName("test") {
            java.srcDirs("src/testShared/java")
        }
    }
}

// Room schema export — keeps a versioned history of the DB for migrations & tests.
// (KSP is a top-level extension, configured outside the `android { }` block.)
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    // Core / lifecycle
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.splashscreen)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.bundles.compose)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.ui.tooling)

    // Room (persistence)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Hilt (dependency injection)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Serialization (JSON data backup / export)
    implementation(libs.kotlinx.serialization.json)

    // Image loading with animated GIF / WebP support (exercise reference media)
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)

    // On-device LLM (Google AI Edge / MediaPipe LLM Inference API) — Stage 2.
    implementation(libs.mediapipe.tasks.genai)

    // On-device OCR (ML Kit) — reads the numbers printed on plates/dumbbells from a photo.
    // The bundled variant ships the model inside the APK, so it works with no network.
    implementation(libs.mlkit.text.recognition)

    // Unit testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    // Instrumented testing (Room DAO tests run on-device/emulator)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)

    // Compose UI testing
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
