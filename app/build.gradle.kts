import java.util.Properties

// ScreenScraper developer credentials, issued to RetroForge by ScreenScraper. Kept in local.properties (not in git):
// screenscraper.devid=… / screenscraper.devpassword=…. Without them the app builds and ScreenScraper says it's unavailable.
val localProps = Properties().apply { rootProject.file("local.properties").takeIf { it.isFile }?.inputStream()?.use(::load) }
// Release signing key: your own keystore, described in keystore.properties (not in git). See docs/BUILD_AND_INSTALL.md.
val keystoreProps = Properties().apply { rootProject.file("keystore.properties").takeIf { it.isFile }?.inputStream()?.use(::load) }
// Google Play upload key, kept apart from the GitHub key: keystore-play.properties (not in git), same four entries.
val playKeystoreProps = Properties().apply { rootProject.file("keystore-play.properties").takeIf { it.isFile }?.inputStream()?.use(::load) }

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
        versionCode = 12
        versionName = "0.8.2-beta"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "SCREENSCRAPER_DEV_ID", quoted("screenscraper.devid"))
        buildConfigField("String", "SCREENSCRAPER_DEV_PASSWORD", quoted("screenscraper.devpassword"))
        // Bug reports go to the project's GitHub issues unless local.properties names another tracker.
        buildConfigField("String", "BUG_REPORT_URL", "\"" + (localProps.getProperty("bugreport.url") ?: "https://github.com/RetroForgeFrontEnd/RetroForge/issues") + "\"")
        // Optional support links (local.properties): support.buymeacoffee, support.kofi, support.patreon, support.github
        // Darren's Buy Me a Coffee page unless local.properties says otherwise (the Play version must leave it out).
        buildConfigField("String", "SUPPORT_BUYMEACOFFEE", "\"" + (localProps.getProperty("support.buymeacoffee") ?: "https://buymeacoffee.com/darrenglant") + "\"")
        buildConfigField("String", "SUPPORT_KOFI", quoted("support.kofi"))
        buildConfigField("String", "SUPPORT_PATREON", quoted("support.patreon"))
        buildConfigField("String", "SUPPORT_GITHUB", quoted("support.github"))
    }

    // Two builds from one codebase. "full" is the GitHub APK with everything. "play" is for Google Play: no Switch,
    // no self-updating or installing other apps, no outside donation links (see docs/PLAY_STORE.md). It has its own
    // application ID, so it installs beside the GitHub one (the two are signed with different keys).
    flavorDimensions += "store"
    productFlavors {
        create("full") {
            dimension = "store"
            buildConfigField("boolean", "PLAY_STORE", "false")
        }
        create("play") {
            dimension = "store"
            applicationIdSuffix = ".play"
            buildConfigField("boolean", "PLAY_STORE", "true")
        }
    }

    signingConfigs {
        for ((name, props) in listOf("release" to keystoreProps, "playUpload" to playKeystoreProps)) {
            if (props.getProperty("storeFile") == null) continue
            create(name) {
                storeFile = rootProject.file(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
        }
    }
    // Each version signs with its own key: full with the GitHub key, play with the Play upload key. Without the file
    // its release build is unsigned (fine for testing, not for users).
    productFlavors.getByName("full") { signingConfigs.findByName("release")?.let { signingConfig = it } }
    productFlavors.getByName("play") { signingConfigs.findByName("playUpload")?.let { signingConfig = it } }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    packaging {
        resources {
            // BouncyCastle (PC streaming's pairing) ships the same licence files in each of its jars.
            excludes += "/META-INF/*.md"
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
    implementation(project(":pc-player"))
    implementation(libs.zxing.core)
    implementation(project(":pc-stream"))
    implementation(libs.commons.compress)
    implementation(libs.xz)
    implementation(libs.androidx.webkit)
    implementation(libs.coil.compose)
    implementation(libs.coil.svg)
    implementation(libs.coil.network) // so AsyncImage can load web pictures (RetroAchievements badges)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.media3.session)
    implementation(libs.media3.ffmpeg)
    implementation(libs.smbj)
    implementation(libs.dcerpc) // lists a computer's shared folders (SRVSVC)

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
