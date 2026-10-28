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
include(":app", ":core-ui", ":core-settings", ":pc", ":retro-patch", ":retro-hacks", ":retro-library", ":core-library")
