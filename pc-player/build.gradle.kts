plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.retroforge.pcplayer"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        ndk {
            // Wine for Android is arm64 only; x86_64 builds the X server for the emulator.
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
        externalNativeBuild {
            cmake { arguments += "-DANDROID_STL=c++_static" }
        }
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    buildFeatures {
        // The in-game menu matches the retro player's (Compose).
        compose = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":core-ui"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.collection)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.commons.compress)
    implementation(libs.xz)
    implementation(libs.javasteam)
    implementation(libs.javasteam.depotdownloader)
    implementation(libs.spongycastle)
    implementation(libs.okhttp)
    implementation(libs.protobuf.java)
    implementation(libs.zstd.jni) { artifact { type = "aar" } }
    testImplementation(libs.junit)
}
