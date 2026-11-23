pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "StreamDeckPlayer"
include(
    ":app",
    ":core:common",
    ":core:parser",
    ":core:database",
    ":core:datastore",
    ":core:network",
    ":core:data",
    ":core:designsystem",
    ":core:player",
    ":feature:playlist",
    ":feature:channels",
    ":feature:player-ui",
    ":feature:epg",
    ":feature:catchup",
    ":feature:vod",
    ":feature:settings",
    ":feature:cast",
    ":feature:monetization",
)
