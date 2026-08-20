plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "dev.wherop.batterywidget"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.wherop.batterywidget"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

// The widget itself is written against the platform framework only — no third-party
// runtime dependencies, so the APK stays tiny and the drawing code has no version drift.
dependencies {
    testImplementation("junit:junit:4.13.2")
}
