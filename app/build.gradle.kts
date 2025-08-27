plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "ch.olivsoft.android.blindman"
    compileSdk = 36
    defaultConfig {
        applicationId = "ch.olivsoft.android.blindman"
        minSdk = 23
        targetSdk = 36
        versionCode = 31
        versionName = "1.31"
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    signingConfigs {
        create("release") {
        }
    }
    buildFeatures {
        viewBinding = true
    }
    kotlin {
        jvmToolchain(11)
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.play.services.ads)
    implementation(libs.material)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.core.ktx)
}
