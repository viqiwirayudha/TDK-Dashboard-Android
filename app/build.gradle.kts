plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.tigadaun.tdkdashboard"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tigadaun.tdkdashboard"
        minSdk = 24
        targetSdk = 35
        versionCode = 185
        versionName = "18.5-icon-native-download"
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
