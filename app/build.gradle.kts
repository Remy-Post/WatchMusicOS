plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.music"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.music"
        // Galaxy Watch8 ships Wear OS 6 (API 36); API 33 keeps Watch4–7 on One UI Watch 5+ working.
        minSdk = 33
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            // Whole-app R8. The template's partial `packageScope` produced an IllegalAccessError
            // between optimized Kotlin classes and the unoptimized rest of the app at startup.
            optimization {
                enable = true
            }
            // The icon packs ship thousands of glyphs; keep only the ones the app uses.
            isShrinkResources = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    useLibrary("wear-sdk")
    buildFeatures {
        compose = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        optIn.add("com.google.android.horologist.annotations.ExperimentalHorologistApi")
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.activity.compose)
    implementation(libs.core.ktx)
    implementation(libs.core.splashscreen)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.ui)
    implementation(libs.ui.graphics)
    implementation(libs.ui.tooling.preview)
    implementation(libs.wear.compose.foundation)
    implementation(libs.wear.compose.material3)
    implementation(libs.wear.compose.navigation)
    implementation(libs.wear.compose.ui.tooling)
    implementation(libs.wear.tooling.preview)
    implementation(libs.wear.input)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.palette)
    implementation(libs.coroutines.android)
    implementation(libs.phosphor.icons)
    implementation(libs.material.symbols.rounded.filled)
    implementation(libs.horologist.audio)
    implementation(libs.horologist.audio.ui.material3)

    testImplementation(libs.junit)
    testImplementation(libs.org.json)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.ui.test.junit4)
    debugImplementation(libs.ui.test.manifest)
    debugImplementation(libs.ui.tooling)
}
