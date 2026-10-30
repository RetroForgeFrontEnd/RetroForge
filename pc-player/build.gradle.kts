plugins {
    alias(libs.plugins.android.library)
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
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.collection)
    implementation(libs.androidx.annotation)
    implementation(libs.commons.compress)
    implementation(libs.xz)
    implementation(libs.javasteam)
    implementation(libs.spongycastle)
    implementation(libs.protobuf.java)
    implementation(libs.zstd.jni) { artifact { type = "aar" } }
    testImplementation(libs.junit)
}
