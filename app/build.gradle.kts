plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "ch.olivsoft.android.blindman"
    compileSdk = 36
    defaultConfig {
        applicationId = "ch.olivsoft.android.blindman"
        minSdk = 23
        targetSdk = 36
        versionCode = 34
        versionName = "1.34"
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
}

dependencies {
    implementation(libs.play.services.ads)
    implementation(libs.material)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.work.runtime.ktx)
}
