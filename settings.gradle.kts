@file:Suppress("UnstableApiUsage")

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { setUrl("https://jitpack.io") }
    }
}

rootProject.name = "OndaMusic"

include(":app")
include(":core:model")
include(":core:design")
include(":core:database")
include(":core:sources")
include(":core:lyrics")
include(":core:transfer")
include(":core:playback")
include(":feature:home")
include(":feature:search")
include(":feature:library")
include(":feature:player")
include(":feature:settings")
