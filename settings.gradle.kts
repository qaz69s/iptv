pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
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

rootProject.name = "StreamBoxPlayer"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":app")
include(":core:common")
include(":core:model")
include(":core:domain")
include(":core:data")
include(":core:datastore")
include(":core:designsystem")
include(":core:ui")
include(":core:testing")
include(":core:parser")
include(":core:network")
include(":core:database")
include(":core:player")
include(":feature:onboarding")
include(":feature:channels")
include(":feature:epg")
include(":feature:settings")
include(":feature:playlist")
