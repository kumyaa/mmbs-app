plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // KSP and kotlinx-serialization are enabled in Phase A when Room and JSON models land.
}

android {
    namespace = "org.mmbs.tracker"
    compileSdk = 34

    defaultConfig {
        applicationId = "org.mmbs.tracker"
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0-A0"

        // Strip unused locale resources — English only.
        resourceConfigurations += listOf("en")

        // No vector drawable bloat — we render simple drawables only.
        vectorDrawables { useSupportLibrary = false }
    }

    signingConfigs {
        // Debug uses the default debug keystore automatically.
        // Release signing is configured later in Phase A once a release keystore exists.
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Sign with debug key for now so the release APK is installable in CI runs.
            // Swap to a real release signing config once we generate a keystore.
            signingConfig = signingConfigs.getByName("debug")
        }
        getByName("debug") {
            isMinifyEnabled = false
            isShrinkResources = false
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
        viewBinding = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
                "META-INF/*.kotlin_module",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/*.version",
                "kotlin/**",
                "**/*.kotlin_metadata"
            )
        }
    }
}

dependencies {
    // Kotlin stdlib is added implicitly by the Kotlin plugin.

    // Smallest possible AndroidX surface for a Fragment-hosting single-activity app.
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
}
