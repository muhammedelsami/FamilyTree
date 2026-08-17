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
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "FamilyTree"

include(":app")

// --- core ---
include(":core:model")
include(":core:common")
include(":core:domain")
include(":core:database")
include(":core:datastore")
include(":core:data")
include(":core:gedcom")
include(":core:diagram")
include(":core:media")
include(":core:backup")
include(":core:notifications")
include(":core:billing")
include(":core:designsystem")
include(":core:ui")

// --- feature ---
include(":feature:trees")
include(":feature:person")
include(":feature:family")
include(":feature:diagram")
include(":feature:media")
include(":feature:backup")
include(":feature:share")
include(":feature:settings")
