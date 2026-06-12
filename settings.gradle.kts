pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "my-bank-app"

include(
    "config-server",
    "discovery-server",
    "bank-gateway",
    "accounts-service",
    "cash-service",
    "transfer-service",
    "notifications-service",
    "front-ui",
)

