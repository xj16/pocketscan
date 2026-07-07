plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    jacoco
}

// Export Room schemas into the repo so migrations can be diffed in review.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

android {
    namespace = "dev.xj16.pocketscan"
    compileSdk = 34

    defaultConfig {
        applicationId = "dev.xj16.pocketscan"
        minSdk = 24
        targetSdk = 34
        versionCode = 2
        versionName = "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
            isMinifyEnabled = false
            // Emit JaCoCo execution data for the JVM unit tests so
            // `jacocoTestReport` has coverage to report on.
            enableUnitTestCoverage = true
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
    }
    androidResources {
        // TFLite models must not be compressed or they can't be mmap'd.
        noCompress += "tflite"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    // --- Core / lifecycle ---
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)

    // --- Compose (BOM aligns all Compose artifact versions) ---
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.ui.tooling)

    // --- Room (local SQLite ledger) ---
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // --- CameraX (capture pipeline) ---
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // --- On-device OCR (ML Kit — bundled model, no network) ---
    implementation(libs.mlkit.text.recognition)

    // --- On-device receipt categorization (TensorFlow Lite) ---
    // The interpreter loads an optional model from assets; when it's absent the
    // classifier falls back to a keyword model so the build never needs a
    // committed binary blob and inference still works offline.
    implementation(libs.tensorflow.lite)

    // --- Computer vision (OpenCV — edge detection + perspective warp) ---
    implementation(libs.opencv)

    // --- Runtime permissions helper ---
    implementation(libs.accompanist.permissions)

    // --- Unit tests (JVM / Robolectric) ---
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.androidx.core.testing)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)

    // --- Instrumented tests ---
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
}

// --- Coverage ---------------------------------------------------------------
// A JaCoCo report over the JVM/Robolectric unit tests. Focuses on the logic
// layers that carry the app's correctness (parser, CSV, ViewModels, data,
// vision geometry) and excludes generated code and pure Compose UI, which is
// covered by manual/instrumented testing instead.
jacoco {
    toolVersion = "0.8.12"
}

tasks.register<JacocoReport>("jacocoTestReport") {
    dependsOn("testDebugUnitTest")
    group = "verification"
    description = "Generates a JaCoCo coverage report from the debug unit tests."

    reports {
        xml.required.set(true)
        html.required.set(true)
    }

    val excludes = listOf(
        "**/R.class", "**/R$*.class", "**/BuildConfig.*", "**/Manifest*.*",
        "**/*Test*.*", "android/**/*.*",
        // Generated Room + Compose scaffolding and pure-UI screens.
        "**/*_Impl*.*", "**/*Kt$*.*",
        "**/dev/xj16/pocketscan/ui/screen/**",
        "**/dev/xj16/pocketscan/ui/theme/**",
        "**/*ComposableSingletons*.*",
    )

    val debugTree = fileTree("${layout.buildDirectory.get()}/tmp/kotlin-classes/debug") {
        setExcludes(excludes)
    }
    classDirectories.setFrom(debugTree)
    sourceDirectories.setFrom(files("src/main/java"))
    executionData.setFrom(
        fileTree(layout.buildDirectory.get()) {
            setIncludes(listOf("**/testDebugUnitTest.exec", "**/*.ec"))
        },
    )
}
