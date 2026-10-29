import java.util.Properties

// ScreenScraper developer credentials, issued to RetroForge by ScreenScraper. Kept in local.properties (not in git):
// screenscraper.devid=… / screenscraper.devpassword=…. Without them the app builds and ScreenScraper says it's unavailable.
val localProps = Properties().apply { rootProject.file("local.properties").takeIf { it.isFile }?.inputStream()?.use(::load) }
// Release signing key: your own keystore, described in keystore.properties (not in git). See docs/BUILD_AND_INSTALL.md.
val keystoreProps = Properties().apply { rootProject.file("keystore.properties").takeIf { it.isFile }?.inputStream()?.use(::load) }

fun quoted(key: String) = "\"" + (localProps.getProperty(key) ?: "").replace("\\", "\\\\").replace("\"", "\\\"") + "\""

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.retroforge.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.retroforge.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 5
        versionName = "0.5.0-beta"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "SCREENSCRAPER_DEV_ID", quoted("screenscraper.devid"))
        buildConfigField("String", "SCREENSCRAPER_DEV_PASSWORD", quoted("screenscraper.devpassword"))
        buildConfigField("String", "BUG_REPORT_URL", quoted("bugreport.url"))
        // Optional support links (local.properties): support.buymeacoffee, support.kofi, support.patreon, support.github
        buildConfigField("String", "SUPPORT_BUYMEACOFFEE", quoted("support.buymeacoffee"))
        buildConfigField("String", "SUPPORT_KOFI", quoted("support.kofi"))
        buildConfigField("String", "SUPPORT_PATREON", quoted("support.patreon"))
        buildConfigField("String", "SUPPORT_GITHUB", quoted("support.github"))
    }

    signingConfigs {
        if (keystoreProps.getProperty("storeFile") != null) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // Without keystore.properties the release APK is built unsigned (fine for testing, not for users).
            signingConfigs.findByName("release")?.let { signingConfig = it }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core-ui"))
    implementation(project(":core-settings"))
    implementation(project(":pc"))
    implementation(project(":retro-patch"))
    implementation(project(":retro-hacks"))
    implementation(project(":core-library"))
    implementation(project(":retro"))
    implementation(project(":retro-artwork"))
    implementation(project(":retro-player"))
    implementation(libs.commons.compress)
    implementation(libs.xz)
    implementation(libs.coil.compose)
    implementation(libs.coil.svg)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
