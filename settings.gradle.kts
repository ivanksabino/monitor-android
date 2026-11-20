pluginManagement {
    includeBuild("build-logic")
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

rootProject.name = "MonitorCidadao"

include(":app")
include(
    ":core:routing",
    ":core:designsystem",
    ":core:network",
    ":core:network-fixtures",
    ":core:imageloader",
    ":core:storage",
    ":core:database",
    ":core:analytics",
    ":core:featureflags",
    ":core:testing",
)
include(":data:api", ":data:repository")
include(":domain")
include(
    ":feature:feed",
    ":feature:explorar",
    ":feature:perfil",
    ":feature:entenda",
    ":feature:participe",
    ":feature:ajustes",
)
