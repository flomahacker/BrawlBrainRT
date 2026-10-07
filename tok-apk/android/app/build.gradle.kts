plugins {
    id("com.android.application")
}

android {
    namespace = "com.flomahacker.tok"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.flomahacker.tok"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

dependencies {
    implementation("androidx.webkit:webkit:1.17.1")
}
