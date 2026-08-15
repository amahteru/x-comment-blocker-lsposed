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
        maven { url = java.net.URI("https://api.xposed.info/") }
        maven { url = java.net.URI("https://jitpack.io") }
    }
}

rootProject.name = "x-comment-blocker-lsposed"
include(":app")
