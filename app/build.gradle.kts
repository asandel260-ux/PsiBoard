plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.psiboard.ime"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.psiboard.ime"
        minSdk = 21
        targetSdk = 34
        versionCode = 5
        versionName = "0.4.1"
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            // CI has no private signing key, so sign with the auto-generated debug key: still installable, but optimized (R8) and not debuggable
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    lint { checkReleaseBuilds = false; abortOnError = false }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
// Intentionally zero dependencies: no AndroidX, no Compose -> tiny APK, fast cold start.
