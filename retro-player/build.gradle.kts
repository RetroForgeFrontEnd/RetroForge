plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.retroforge.player"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        ndk {
            // Phones and handhelds (arm64, older 32-bit arm) and the x86_64 emulator.
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
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
    testImplementation(libs.junit)
}
