pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}

rootProject.name = "keeply"

// --- Core: pure domain and infrastructure, no UI ---
include(":core:domain")
include(":core:data")
include(":core:documents")
include(":core:imaging")
include(":core:ocr")
include(":core:extraction")
include(":core:backup")
include(":core:reminders")
include(":core:ai")
include(":core:services")

// --- Test/demo support: synthetic receipt generation ---
include(":fixtures")

// --- Compose Multiplatform desktop application ---
include(":desktop")
