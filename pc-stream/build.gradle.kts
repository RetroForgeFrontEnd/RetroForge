plugins {
    alias(libs.plugins.android.library)
}

// Moonlight for Android's streaming code (com.limelight, GPL-3.0) and its C core moonlight-common-c, vendored so
// RetroForge can stream games from the user's own PC (Sunshine or NVIDIA GameStream) without a second app.
// See docs/pc-streaming.md for what came from where.
android {
    namespace = "com.limelight"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
        externalNativeBuild {
            ndkBuild { arguments += "PRODUCT_FLAVOR=nonRoot" }
        }
        buildConfigField("boolean", "ROOT_BUILD", "false")
        resValue("string", "app_label", "RetroForge")
        resValue("string", "app_label_root", "RetroForge")
        consumerProguardFiles("consumer-rules.pro")
    }
    buildFeatures {
        buildConfig = true
        resValues = true
    }
    externalNativeBuild {
        ndkBuild { path = file("src/main/jni/Android.mk") }
    }
    compileOptions {
        encoding = "UTF-8"
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint {
        // Vendored upstream code: its own warnings (translations, old APIs) are Moonlight's, tracked in the baseline.
        disable += "MissingTranslation"
        baseline = file("lint-baseline.xml")
    }
}

dependencies {
    implementation(libs.bouncycastle.prov)
    implementation(libs.bouncycastle.pkix)
    implementation(libs.jcodec)
    implementation(libs.okhttp)
    implementation(libs.jmdns)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
}
