pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "RetroForge"
include(":app", ":core-ui", ":theme-engine", ":core-settings", ":pc", ":retro-patch", ":retro-hacks", ":retro-library", ":core-library", ":retro", ":retro-artwork", ":retro-player")
