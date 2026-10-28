plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.minimal.launcher"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.minimal.launcher"
        minSdk = 28
        targetSdk = 28          // Android 9 на магнитоле, без новых ограничений
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

// Внешние зависимости не нужны: только Android SDK.
