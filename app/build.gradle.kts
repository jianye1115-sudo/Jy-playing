plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    // The Google Services plugin reads app/google-services.json. A clearly
    // labelled SAMPLE file is committed so a fresh clone still configures;
    // swap in your real Firebase config before shipping.
    alias(libs.plugins.google.services)
}

android {
    namespace = "app.stepbuddy"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.stepbuddy"
        minSdk = 26          // Android 8.0 — required for notification channels & adaptive icons.
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Domain used by the App Link invite flow. Referenced from the
        // manifest so it stays in one place. See docs/SETUP.md.
        manifestPlaceholders["pairingHost"] = "stepbuddy.app"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.activity.compose)

    // Jetpack Compose (Material 3) via BOM so versions stay aligned.
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.ui.tooling)

    // Room — local cache so the elderly device works fully offline.
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // DataStore — user Settings (font scale, speech rate, language, overlay).
    implementation(libs.androidx.datastore.preferences)

    // Firebase — Anonymous Auth + Firestore (sync) + Storage (screenshots).
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.storage)

    implementation(libs.coil.compose)          // Load screenshots (local file or remote URL).
    implementation(libs.zxing.core)            // Generate pairing QR codes.
    implementation(libs.zxing.embedded)        // Scan pairing QR codes.
    implementation(libs.accompanist.permissions)

    implementation(libs.kotlinx.serialization.json)   // .stepbuddy export/import + Firestore models.
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services) // await() on Firebase Tasks.

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
}
