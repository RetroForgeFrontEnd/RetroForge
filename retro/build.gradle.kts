plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.retroforge.retro"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":retro-library"))
    testImplementation(libs.junit)
}
