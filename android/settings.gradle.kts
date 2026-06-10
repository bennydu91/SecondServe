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

rootProject.name = "SecondServe"

include(":app")
include(":wear")
include(":domain")
include(":data")
include(":core:ui")
include(":core:ai")
include(":feature:match")
include(":feature:history")
include(":feature:coaching")
include(":feature:profile")
