plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "ch.olivsoft.android.blindman"
    compileSdk = 37
    defaultConfig {
        applicationId = "ch.olivsoft.android.blindman"
        minSdk = 23
        versionCode = 38
        versionName = "1.38"
    }
    buildTypes {
        debug {
            buildConfigField("boolean", "USE_COMPOSE", "true")
        }
        create("debug_xml") {
            initWith(getByName("debug"))
            buildConfigField("boolean", "USE_COMPOSE", "false")
            applicationIdSuffix = ".xml"
            isDebuggable = true
        }
        release {
            buildConfigField("boolean", "USE_COMPOSE", "true")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            ndk {
                debugSymbolLevel = "FULL"
            }
        }
    }
    signingConfigs {
        create("release") {
        }
    }
    buildFeatures {
        viewBinding = true
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(platform(libs.androidx.compose.bom))

    implementation(libs.play.services.ads)
    implementation(libs.material)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material3.window.size.class1)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.runtime.livedata)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    debugImplementation(libs.androidx.ui.tooling)
    "debug_xmlImplementation"(libs.androidx.ui.tooling)
}
