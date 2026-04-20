plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Short git SHA + dirty marker, resolved once per build invocation. Shown in the
// About card so it's obvious whether the installed APK matches HEAD.
fun gitRevision(): String {
    // Bypass git's "dubious ownership" refusal when the repo is bind-mounted
    // from macOS into the build container (host uid != container root uid).
    fun git(vararg args: String): String = try {
        ProcessBuilder(listOf("git", "-c", "safe.directory=*") + args.toList())
            .redirectErrorStream(true)
            .start()
            .inputStream.bufferedReader().readText().trim()
    } catch (_: Exception) { "" }

    val sha = git("rev-parse", "--short", "HEAD").ifBlank { return "unknown" }
    val dirty = git("status", "--porcelain").isNotBlank()
    return if (dirty) "$sha-dirty" else sha
}

android {
    namespace = "io.github.viyh.freedrift"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.viyh.freedrift"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "GIT_REVISION", "\"${gitRevision()}\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    signingConfigs {
        // Reuse the Android SDK debug keystore for release builds too. Fine for
        // personal sideload sharing — same install UX as debug, no new warnings.
        // Do NOT use this key for any public distribution.
        create("debugShared") {
            storeFile = file(System.getProperty("user.home") + "/.android/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            // Resource shrinking needs its own big JVM pass on top of R8.
            // Skip it — the code minifier does the heavy lifting anyway.
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("debugShared")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-session:1.4.1")
    implementation("androidx.media3:media3-common:1.4.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    implementation("sh.calvin.reorderable:reorderable:2.4.3")
}
