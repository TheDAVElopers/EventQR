plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.kotlin.android)
}

// Baseline profile generator. The androidx.baselineprofile Gradle plugin is deliberately not used: version 1.5.0
// leaks an unresolved Provider into the app's Kotlin source set under AGP 9 with the legacy DSL, so the app's
// generation variant cannot compile. This is the equivalent manual wiring. See the KDoc on BaselineProfileGenerator
// for how to regenerate the profile.
android {
    namespace = "com.thedavelopers.eventqr.baselineprofile"
    compileSdk = 36

    defaultConfig {
        // Baseline profile generation needs API 28+; the app itself still supports 26.
        minSdk = 28
        targetSdk = 35
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }

    targetProjectPath = ":app"

    buildTypes {
        // Mirrors the app's "benchmark" build type (debug-signed, unminified release equivalent).
        create("benchmark") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    // The test APK instruments itself while driving the app from outside (required by Macrobenchmark).
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

dependencies {
    implementation(libs.androidx.junit)
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.uiautomator)
}
